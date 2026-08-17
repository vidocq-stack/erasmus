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
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public final class DecimalMinValidatorForCharSequence implements ConstraintValidator<DecimalMin, CharSequence> {

    private BigDecimal minValue;
    private boolean inclusive;

    @Override
    public void initialize(DecimalMin constraintAnnotation) {
        this.minValue = DecimalBoundSupport.parseBound(constraintAnnotation.value());
        this.inclusive = constraintAnnotation.inclusive();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BigDecimal actual;
        try {
            actual = new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            // Not parseable as a number: a validation failure, not a crash — the value is
            // simply not a valid decimal, same treatment as any other constraint violation.
            return false;
        }
        int comparison = actual.compareTo(minValue);
        return inclusive ? comparison >= 0 : comparison > 0;
    }
}
