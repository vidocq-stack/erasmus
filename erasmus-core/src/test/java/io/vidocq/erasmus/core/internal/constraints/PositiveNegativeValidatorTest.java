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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean Validation 3.1 built-in constraints: {@code @Positive}/{@code @PositiveOrZero}/
 * {@code @Negative}/{@code @NegativeOrZero}. Unlike {@code @Min}/{@code @Max}, these DO
 * support {@code Float}/{@code Double} — a sign check has no precision problem.
 */
class PositiveNegativeValidatorTest {

    @Test
    void positive() {
        PositiveValidator validator = new PositiveValidator();
        assertTrue(validator.isValid(null, null), "null is trivially valid");
        assertTrue(validator.isValid(1, null));
        assertFalse(validator.isValid(0, null));
        assertFalse(validator.isValid(-1, null));
        assertTrue(validator.isValid(0.5, null), "floating point is supported here, unlike @Min/@Max");
        assertFalse(validator.isValid(0.5 - 0.5, null));
        assertTrue(validator.isValid(BigDecimal.ONE, null));
        assertFalse(validator.isValid(BigDecimal.ZERO, null));
        assertFalse(validator.isValid(Double.NaN, null), "NaN is neither positive nor negative");
    }

    @Test
    void positiveOrZero() {
        PositiveOrZeroValidator validator = new PositiveOrZeroValidator();
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid(0, null));
        assertTrue(validator.isValid(1, null));
        assertFalse(validator.isValid(-1, null));
        assertTrue(validator.isValid(0.0, null));
        assertFalse(validator.isValid(Double.NaN, null));
    }

    @Test
    void negative() {
        NegativeValidator validator = new NegativeValidator();
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid(-1, null));
        assertFalse(validator.isValid(0, null));
        assertFalse(validator.isValid(1, null));
        assertTrue(validator.isValid(BigInteger.valueOf(-1), null));
        assertFalse(validator.isValid(Double.NaN, null));
    }

    @Test
    void negativeOrZero() {
        NegativeOrZeroValidator validator = new NegativeOrZeroValidator();
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid(0, null));
        assertTrue(validator.isValid(-1, null));
        assertFalse(validator.isValid(1, null));
        assertFalse(validator.isValid(Double.NaN, null));
    }
}
