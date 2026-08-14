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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bean Validation 3.1 built-in constraint: {@code @NotEmpty}, across its four target types. */
class NotEmptyValidatorTest {

    @Test
    void charSequence() {
        NotEmptyValidatorForCharSequence validator = new NotEmptyValidatorForCharSequence();
        assertTrue(validator.isValid(null, null), "null is trivially valid — only @NotNull rejects null");
        assertFalse(validator.isValid("", null));
        assertTrue(validator.isValid("x", null));
        assertTrue(validator.isValid(" ", null), "@NotEmpty does not trim — that is @NotBlank's job");
    }

    @Test
    void collection() {
        NotEmptyValidatorForCollection validator = new NotEmptyValidatorForCollection();
        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(List.of(), null));
        assertTrue(validator.isValid(List.of("x"), null));
    }

    @Test
    void map() {
        NotEmptyValidatorForMap validator = new NotEmptyValidatorForMap();
        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(Map.of(), null));
        assertTrue(validator.isValid(Map.of("k", "v"), null));
    }

    @Test
    void array() {
        NotEmptyValidatorForArray validator = new NotEmptyValidatorForArray();
        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid(new Object[0], null));
        assertTrue(validator.isValid(new Object[] {"x"}, null));
    }
}
