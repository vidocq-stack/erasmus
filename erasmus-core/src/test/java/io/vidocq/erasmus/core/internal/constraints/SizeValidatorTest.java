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

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bean Validation 3.1 built-in constraint: {@code @Size}, across its four target types. */
class SizeValidatorTest {

    @Size(min = 2, max = 4)
    private String sized2to4;

    @Size(min = 1, max = 0)
    private String invalidBounds;

    private Size annotationOf(String fieldName) throws NoSuchFieldException {
        Field field = SizeValidatorTest.class.getDeclaredField(fieldName);
        return field.getAnnotation(Size.class);
    }

    @Test
    void charSequence() throws NoSuchFieldException {
        SizeValidatorForCharSequence validator = new SizeValidatorForCharSequence();
        validator.initialize(annotationOf("sized2to4"));

        assertTrue(validator.isValid(null, null), "null is trivially valid — only @NotNull rejects null");
        assertFalse(validator.isValid("a", null), "below min");
        assertTrue(validator.isValid("ab", null), "at min");
        assertTrue(validator.isValid("abcd", null), "at max");
        assertFalse(validator.isValid("abcde", null), "above max");
    }

    @Test
    void collection() throws NoSuchFieldException {
        SizeValidatorForCollection validator = new SizeValidatorForCollection();
        validator.initialize(annotationOf("sized2to4"));

        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(List.of("a"), null));
        assertTrue(validator.isValid(List.of("a", "b"), null));
    }

    @Test
    void map() throws NoSuchFieldException {
        SizeValidatorForMap validator = new SizeValidatorForMap();
        validator.initialize(annotationOf("sized2to4"));

        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(Map.of("a", 1), null));
        assertTrue(validator.isValid(Map.of("a", 1, "b", 2), null));
    }

    @Test
    void array() throws NoSuchFieldException {
        SizeValidatorForArray validator = new SizeValidatorForArray();
        validator.initialize(annotationOf("sized2to4"));

        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(new Object[] {"a"}, null));
        assertTrue(validator.isValid(new Object[] {"a", "b"}, null));
    }

    @Test
    void maxLessThanMin_rejectedAtInitialize() throws NoSuchFieldException {
        Size invalid = annotationOf("invalidBounds");
        assertThrows(IllegalArgumentException.class, () -> new SizeValidatorForCharSequence().initialize(invalid));
    }
}
