/*
 * Copyright (c) 2026 Yann Blazart, Antoine Sabot-Durand and the Vidocq contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * or any later version, which is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * It is also made available under the European Union Public Licence v. 1.2,
 * which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * SPDX-License-Identifier: EPL-2.0 OR EUPL-1.2 OR GPL-2.0-or-later
 */
package io.vidocq.erasmus.core.internal;

import jakarta.validation.MessageInterpolator;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link MessageInterpolator}.
 *
 * <p>Handles the two substitution steps the built-in constraints (ROADMAP M1/M2)
 * actually need: resolving a whole-template resource bundle reference such as
 * {@code {jakarta.validation.constraints.NotNull.message}} against a resource
 * bundle, then substituting {@code {attributeName}} tokens with the constraint's
 * own attribute values (e.g. {@code {min}}/{@code {max}} for {@code @Size}). This
 * intentionally does NOT evaluate the spec's predefined EL subset ({@code ${...}}
 * expressions) — that homegrown EL-subset grammar is scoped to ROADMAP M2.
 *
 * <p><b>Bundle lookup order</b> — the spec names {@code jakarta.validation.ValidationMessages}
 * as the bundle a caller may supply to override default messages. Erasmus's own
 * defaults intentionally do NOT live at that path: this module already {@code requires}
 * the {@code jakarta.validation} module (which exports package {@code jakarta.validation}),
 * so shipping a resource under that same package inside our own module would be a
 * split-package — two modules both "containing" {@code jakarta.validation} — which the
 * module system rejects at resolution time. Erasmus's defaults therefore live under its
 * own package instead, and are consulted only once the (likely absent) user override
 * bundle at the spec's own path comes up empty.
 */
public final class ErasmusMessageInterpolator implements MessageInterpolator {

    private static final String USER_OVERRIDE_BUNDLE = "jakarta.validation.ValidationMessages";
    private static final String DEFAULT_BUNDLE = "io.vidocq.erasmus.core.internal.ValidationMessages";
    private static final Pattern BUNDLE_KEY = Pattern.compile("^\\{([^{}]+)}$");
    private static final Pattern ATTRIBUTE_PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.]+)}");

    @Override
    public String interpolate(String messageTemplate, Context context) {
        return interpolate(messageTemplate, context, Locale.getDefault());
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        String resolved = resolveBundleReference(messageTemplate, locale);
        return substituteAttributes(resolved, context.getConstraintDescriptor().getAttributes());
    }

    private static String resolveBundleReference(String template, Locale locale) {
        Matcher matcher = BUNDLE_KEY.matcher(template);
        if (!matcher.matches()) {
            return template;
        }
        String key = matcher.group(1);
        String fromUserBundle = lookup(USER_OVERRIDE_BUNDLE, key, locale);
        if (fromUserBundle != null) {
            return fromUserBundle;
        }
        String fromDefaultBundle = lookup(DEFAULT_BUNDLE, key, locale);
        return fromDefaultBundle != null ? fromDefaultBundle : template;
    }

    private static String lookup(String bundleBaseName, String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(bundleBaseName, locale);
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (MissingResourceException e) {
            // No such bundle on the module/class path — try the next one, or fall back to the raw template.
        }
        return null;
    }

    private static String substituteAttributes(String template, Map<String, Object> attributes) {
        Matcher matcher = ATTRIBUTE_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = attributes.get(matcher.group(1));
            String replacement = value != null ? String.valueOf(value) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
