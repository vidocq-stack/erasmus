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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Shared sign comparison for {@code @Positive}/{@code @PositiveOrZero}/{@code @Negative}/
 * {@code @NegativeOrZero}. Unlike {@code @Min}/{@code @Max}, these constraints DO support
 * {@code Float}/{@code Double} — a sign check has none of the precision problems a threshold
 * comparison has. Using the primitive {@code double} comparison operators directly (rather
 * than routing through {@code long}) matters: {@code 0.5}'s {@code longValue()} truncates to
 * {@code 0}, which would wrongly read as "not positive". Comparisons against {@code NaN} with
 * {@code <}/{@code <=}/{@code >}/{@code >=} are always {@code false} in Java, which conveniently
 * already matches the spec's intent that {@code NaN} is neither positive nor negative.
 */
final class NumberSignSupport {

    private NumberSignSupport() {
    }

    static boolean isPositive(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd.signum() > 0;
        }
        if (value instanceof BigInteger bi) {
            return bi.signum() > 0;
        }
        if (value instanceof Float || value instanceof Double) {
            return value.doubleValue() > 0;
        }
        return value.longValue() > 0;
    }

    static boolean isPositiveOrZero(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd.signum() >= 0;
        }
        if (value instanceof BigInteger bi) {
            return bi.signum() >= 0;
        }
        if (value instanceof Float || value instanceof Double) {
            return value.doubleValue() >= 0;
        }
        return value.longValue() >= 0;
    }

    static boolean isNegative(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd.signum() < 0;
        }
        if (value instanceof BigInteger bi) {
            return bi.signum() < 0;
        }
        if (value instanceof Float || value instanceof Double) {
            return value.doubleValue() < 0;
        }
        return value.longValue() < 0;
    }

    static boolean isNegativeOrZero(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd.signum() <= 0;
        }
        if (value instanceof BigInteger bi) {
            return bi.signum() <= 0;
        }
        if (value instanceof Float || value instanceof Double) {
            return value.doubleValue() <= 0;
        }
        return value.longValue() <= 0;
    }
}
