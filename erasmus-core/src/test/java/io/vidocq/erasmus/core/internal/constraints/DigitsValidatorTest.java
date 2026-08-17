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
import jakarta.validation.constraints.Digits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitsValidatorTest {

    @Digits(integer = 3, fraction = 2)
    private BigDecimal amount;

    private Digits annotation() throws NoSuchFieldException {
        return DigitsValidatorTest.class.getDeclaredField("amount").getAnnotation(Digits.class);
    }

    @Test
    void forNumber() throws NoSuchFieldException {
        DigitsValidatorForNumber validator = new DigitsValidatorForNumber();
        validator.initialize(annotation());

        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid(BigDecimal.valueOf(123.45), null), "at the limit on both sides");
        assertFalse(validator.isValid(BigDecimal.valueOf(1234.45), null), "too many integer digits");
        assertFalse(validator.isValid(BigDecimal.valueOf(123.456), null), "too many fraction digits");
        assertTrue(validator.isValid(BigDecimal.valueOf(1.5), null), "fewer digits than the max is fine");
        assertTrue(validator.isValid(BigDecimal.ZERO, null));
        assertTrue(validator.isValid(100, null), "integral types with zero fraction digits used");
    }

    @Test
    void forNumber_rejectsFloatingPoint() throws NoSuchFieldException {
        DigitsValidatorForNumber validator = new DigitsValidatorForNumber();
        validator.initialize(annotation());

        assertThrows(UnexpectedTypeException.class, () -> validator.isValid(1.5, null));
    }

    @Test
    void forCharSequence() throws NoSuchFieldException {
        DigitsValidatorForCharSequence validator = new DigitsValidatorForCharSequence();
        validator.initialize(annotation());

        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("123.45", null));
        assertFalse(validator.isValid("1234.45", null));
        assertFalse(validator.isValid("not-a-number", null));
    }
}
