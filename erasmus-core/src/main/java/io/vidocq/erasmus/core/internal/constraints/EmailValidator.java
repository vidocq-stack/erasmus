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
package io.vidocq.erasmus.core.internal.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * A pragmatic, not full-RFC-5322, email-shape check — matching how real implementations
 * approach it (full RFC 5322 grammar, with quoted local parts and comments, is famously
 * impractical to validate with a regex and rarely what anyone actually wants rejected/accepted).
 * Known gap, documented rather than silently accepted: no IDN/punycode domain support, no
 * quoted local-part syntax. If {@code regexp}/{@code flags} are supplied on top of the default
 * {@code ".*"}, the value must ALSO match that additional pattern.
 */
public final class EmailValidator implements ConstraintValidator<Email, CharSequence> {

    private static final java.util.regex.Pattern BASIC_EMAIL_SHAPE = java.util.regex.Pattern.compile(
            "^[A-Za-z0-9_+&*-]+(?:\\.[A-Za-z0-9_+&*-]+)*@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

    private java.util.regex.Pattern additionalPattern;

    @Override
    public void initialize(Email constraintAnnotation) {
        int flags = 0;
        for (Pattern.Flag flag : constraintAnnotation.flags()) {
            flags |= flag.getValue();
        }
        this.additionalPattern = java.util.regex.Pattern.compile(constraintAnnotation.regexp(), flags);
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.length() == 0) {
            return true;
        }
        String email = value.toString();
        return BASIC_EMAIL_SHAPE.matcher(email).matches() && additionalPattern.matcher(email).matches();
    }
}
