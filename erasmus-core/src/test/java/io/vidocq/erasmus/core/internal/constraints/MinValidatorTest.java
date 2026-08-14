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

import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bean Validation 3.1 built-in constraint: {@code @Min}. */
class MinValidatorTest {

    @Min(10)
    private long floor;

    private final MinValidatorForNumber validator = new MinValidatorForNumber();

    @Test
    void setUpAndBasicIntegerTypes() throws NoSuchFieldException {
        validator.initialize(MinValidatorTest.class.getDeclaredField("floor").getAnnotation(Min.class));

        assertTrue(validator.isValid(null, null), "null is trivially valid — only @NotNull rejects null");
        assertFalse(validator.isValid(9, null));
        assertTrue(validator.isValid(10, null));
        assertTrue(validator.isValid(11L, null));
        assertFalse(validator.isValid((short) 9, null));
    }

    @Test
    void bigDecimalAndBigInteger() throws NoSuchFieldException {
        validator.initialize(MinValidatorTest.class.getDeclaredField("floor").getAnnotation(Min.class));

        assertFalse(validator.isValid(BigDecimal.valueOf(9.99), null));
        assertTrue(validator.isValid(BigDecimal.valueOf(10), null));
        assertFalse(validator.isValid(BigInteger.valueOf(9), null));
        assertTrue(validator.isValid(BigInteger.valueOf(10), null));
    }

    @Test
    void floatingPointIsRejectedOutright() throws NoSuchFieldException {
        validator.initialize(MinValidatorTest.class.getDeclaredField("floor").getAnnotation(Min.class));

        assertThrows(UnexpectedTypeException.class, () -> validator.isValid(10.0f, null));
        assertThrows(UnexpectedTypeException.class, () -> validator.isValid(10.0d, null));
    }
}
