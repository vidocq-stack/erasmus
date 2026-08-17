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

import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternValidatorTest {

    @Pattern(regexp = "[a-z]+")
    private String lowercaseOnly;

    @Pattern(regexp = "[a-z]+", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String caseInsensitive;

    private Pattern annotationOf(String field) throws NoSuchFieldException {
        return PatternValidatorTest.class.getDeclaredField(field).getAnnotation(Pattern.class);
    }

    @Test
    void matchesWholeStringOnly() throws NoSuchFieldException {
        PatternValidator validator = new PatternValidator();
        validator.initialize(annotationOf("lowercaseOnly"));

        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("hello", null));
        assertFalse(validator.isValid("Hello", null));
        assertFalse(validator.isValid("hello!", null), "must match the WHOLE string, not just a substring");
    }

    @Test
    void flagsAreAppliedToCompilation() throws NoSuchFieldException {
        PatternValidator validator = new PatternValidator();
        validator.initialize(annotationOf("caseInsensitive"));

        assertTrue(validator.isValid("Hello", null));
    }
}
