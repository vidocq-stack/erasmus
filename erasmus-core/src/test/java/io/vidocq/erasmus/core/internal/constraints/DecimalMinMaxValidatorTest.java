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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecimalMinMaxValidatorTest {

    @DecimalMin("10.5")
    private BigDecimal minInclusive;

    @DecimalMin(value = "10.5", inclusive = false)
    private BigDecimal minExclusive;

    @DecimalMax("10.5")
    private BigDecimal maxInclusive;

    @DecimalMax(value = "10.5", inclusive = false)
    private BigDecimal maxExclusive;

    private <A extends java.lang.annotation.Annotation> A annotationOf(String field, Class<A> type) throws NoSuchFieldException {
        return DecimalMinMaxValidatorTest.class.getDeclaredField(field).getAnnotation(type);
    }

    @Test
    void decimalMinForNumber_inclusive() throws NoSuchFieldException {
        DecimalMinValidatorForNumber validator = new DecimalMinValidatorForNumber();
        validator.initialize(annotationOf("minInclusive", DecimalMin.class));

        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(BigDecimal.valueOf(10.4), null));
        assertTrue(validator.isValid(BigDecimal.valueOf(10.5), null), "inclusive: exactly the bound is valid");
        assertTrue(validator.isValid(BigDecimal.valueOf(10.6), null));
    }

    @Test
    void decimalMinForNumber_exclusive() throws NoSuchFieldException {
        DecimalMinValidatorForNumber validator = new DecimalMinValidatorForNumber();
        validator.initialize(annotationOf("minExclusive", DecimalMin.class));

        assertFalse(validator.isValid(BigDecimal.valueOf(10.5), null), "exclusive: exactly the bound is invalid");
        assertTrue(validator.isValid(BigDecimal.valueOf(10.6), null));
    }

    @Test
    void decimalMinForNumber_rejectsFloatingPoint() throws NoSuchFieldException {
        DecimalMinValidatorForNumber validator = new DecimalMinValidatorForNumber();
        validator.initialize(annotationOf("minInclusive", DecimalMin.class));

        assertThrows(UnexpectedTypeException.class, () -> validator.isValid(11.0, null));
    }

    @Test
    void decimalMinForCharSequence() throws NoSuchFieldException {
        DecimalMinValidatorForCharSequence validator = new DecimalMinValidatorForCharSequence();
        validator.initialize(annotationOf("minInclusive", DecimalMin.class));

        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("10.5", null));
        assertFalse(validator.isValid("10.4", null));
        assertFalse(validator.isValid("not-a-number", null), "unparseable input is a violation, not a crash");
    }

    @Test
    void decimalMaxForNumber_inclusive() throws NoSuchFieldException {
        DecimalMaxValidatorForNumber validator = new DecimalMaxValidatorForNumber();
        validator.initialize(annotationOf("maxInclusive", DecimalMax.class));

        assertTrue(validator.isValid(BigDecimal.valueOf(10.5), null));
        assertTrue(validator.isValid(BigDecimal.valueOf(10.4), null));
        assertFalse(validator.isValid(BigDecimal.valueOf(10.6), null));
    }

    @Test
    void decimalMaxForNumber_exclusive() throws NoSuchFieldException {
        DecimalMaxValidatorForNumber validator = new DecimalMaxValidatorForNumber();
        validator.initialize(annotationOf("maxExclusive", DecimalMax.class));

        assertFalse(validator.isValid(BigDecimal.valueOf(10.5), null));
        assertTrue(validator.isValid(BigDecimal.valueOf(10.4), null));
    }

    @Test
    void decimalMaxForCharSequence() throws NoSuchFieldException {
        DecimalMaxValidatorForCharSequence validator = new DecimalMaxValidatorForCharSequence();
        validator.initialize(annotationOf("maxInclusive", DecimalMax.class));

        assertTrue(validator.isValid("10.5", null));
        assertFalse(validator.isValid("10.6", null));
        assertFalse(validator.isValid("not-a-number", null));
    }
}
