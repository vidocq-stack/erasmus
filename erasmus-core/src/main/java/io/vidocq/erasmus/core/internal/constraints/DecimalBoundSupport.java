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
 * Shared parsing/comparison logic for the four {@code @DecimalMin}/{@code @DecimalMax}
 * validators (Number and CharSequence target types).
 */
final class DecimalBoundSupport {

    private DecimalBoundSupport() {
    }

    static BigDecimal parseBound(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "@DecimalMin/@DecimalMax value must be a valid BigDecimal string representation: " + value, e);
        }
    }

    static BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        return BigDecimal.valueOf(value.longValue());
    }
}
