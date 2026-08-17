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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ROADMAP M2: locale variants of the default message bundle. */
class LocaleMessageInterpolatorTest {

    @NotNull
    private String field;

    @Size(min = 1, max = 5)
    private String bounded;

    private <A extends java.lang.annotation.Annotation> A annotationOf(String field, Class<A> type) throws NoSuchFieldException {
        return LocaleMessageInterpolatorTest.class.getDeclaredField(field).getAnnotation(type);
    }

    @Test
    void frenchLocale_resolvesTranslatedMessage() throws NoSuchFieldException {
        ErasmusMessageInterpolator interpolator = new ErasmusMessageInterpolator();
        var descriptor = new io.vidocq.erasmus.core.internal.metadata.ConstraintDescriptorImpl<>(annotationOf("field", NotNull.class));

        String message = interpolator.interpolate(
                descriptor.getMessageTemplate(),
                new MessageInterpolatorContextImpl(descriptor, null),
                Locale.FRENCH);

        assertEquals("ne doit pas être nul", message);
    }

    @Test
    void frenchLocale_stillSubstitutesAttributes() throws NoSuchFieldException {
        ErasmusMessageInterpolator interpolator = new ErasmusMessageInterpolator();
        var descriptor = new io.vidocq.erasmus.core.internal.metadata.ConstraintDescriptorImpl<>(annotationOf("bounded", Size.class));

        String message = interpolator.interpolate(
                descriptor.getMessageTemplate(),
                new MessageInterpolatorContextImpl(descriptor, null),
                Locale.FRENCH);

        assertEquals("la taille doit être comprise entre 1 et 5", message);
    }

    @Test
    void unsupportedLocale_fallsBackToDefaultBundle() throws NoSuchFieldException {
        ErasmusMessageInterpolator interpolator = new ErasmusMessageInterpolator();
        var descriptor = new io.vidocq.erasmus.core.internal.metadata.ConstraintDescriptorImpl<>(annotationOf("field", NotNull.class));

        String message = interpolator.interpolate(
                descriptor.getMessageTemplate(),
                new MessageInterpolatorContextImpl(descriptor, null),
                Locale.JAPANESE);

        assertEquals("must not be null", message, "no _ja bundle exists, so English is the correct fallback");
    }
}
