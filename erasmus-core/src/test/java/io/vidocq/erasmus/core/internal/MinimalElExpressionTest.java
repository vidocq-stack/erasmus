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
package io.vidocq.erasmus.core.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ROADMAP M2: the homegrown minimal EL-subset evaluator. */
class MinimalElExpressionTest {

    @Test
    void variableLookup() {
        assertEquals(5L, MinimalElExpression.evaluate("max", Map.of("max", 5L)));
    }

    @Test
    void literalsAndArithmetic() {
        assertEquals(7L, MinimalElExpression.evaluate("3 + 4", Map.of()));
        assertEquals(1L, MinimalElExpression.evaluate("10 % 3", Map.of()));
        assertEquals(6L, MinimalElExpression.evaluate("3 * (1 + 1)", Map.of()), "integer arithmetic stays integer");
    }

    @Test
    void relationalAndEquality() {
        assertEquals(Boolean.TRUE, MinimalElExpression.evaluate("max > 1", Map.of("max", 5L)));
        assertEquals(Boolean.FALSE, MinimalElExpression.evaluate("max <= 1", Map.of("max", 5L)));
        assertEquals(Boolean.TRUE, MinimalElExpression.evaluate("'a' == 'a'", Map.of()));
    }

    @Test
    void logicalOperators() {
        assertEquals(Boolean.TRUE, MinimalElExpression.evaluate("true && !false", Map.of()));
        assertEquals(Boolean.TRUE, MinimalElExpression.evaluate("false || (1 < 2)", Map.of()));
    }

    @Test
    void ternary_pluralizationStyleExpression() {
        // exactly the kind of real-world expression Bean Validation messages use this for
        assertEquals("s", MinimalElExpression.evaluate("max > 1 ? 's' : ''", Map.of("max", 5L)));
        assertEquals("", MinimalElExpression.evaluate("max > 1 ? 's' : ''", Map.of("max", 1L)));
    }

    @Test
    void validatedValueIsJustAnotherVariable() {
        assertEquals(42L, MinimalElExpression.evaluate("validatedValue", Map.of("validatedValue", 42L)));
    }

    @Test
    void unknownVariable_resolvesToNull() {
        assertEquals(null, MinimalElExpression.evaluate("missing", Map.of()));
    }

    @Test
    void malformedExpression_throws() {
        assertThrows(IllegalArgumentException.class, () -> MinimalElExpression.evaluate("1 +", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MinimalElExpression.evaluate("1 2", Map.of()));
    }
}
