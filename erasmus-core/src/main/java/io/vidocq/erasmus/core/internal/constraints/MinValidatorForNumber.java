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
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Covers {@code BigDecimal}, {@code BigInteger}, and the integral wrapper types
 * (byte/short/int/long and their boxes) via {@link Number#longValue()}.
 * {@code Float}/{@code Double} are rejected outright — the spec excludes them from
 * {@code @Min}/{@code @Max} because binary floating-point comparison against an
 * exact {@code long} bound is lossy.
 */
public final class MinValidatorForNumber implements ConstraintValidator<Min, Number> {

    private long minValue;

    @Override
    public void initialize(Min constraintAnnotation) {
        this.minValue = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.compareTo(BigDecimal.valueOf(minValue)) >= 0;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.compareTo(BigInteger.valueOf(minValue)) >= 0;
        }
        if (value instanceof Float || value instanceof Double) {
            throw new UnexpectedTypeException("@Min does not support floating-point types — got " + value.getClass());
        }
        return value.longValue() >= minValue;
    }
}
