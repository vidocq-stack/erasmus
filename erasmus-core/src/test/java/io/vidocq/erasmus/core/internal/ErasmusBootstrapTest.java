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

import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROADMAP M1 — bootstrap: {@code Validation.buildDefaultValidatorFactory()} must
 * resolve Erasmus via {@code ServiceLoader} (module-info's {@code provides} clause)
 * and hand back a working {@link Validator}.
 */
class ErasmusBootstrapTest {

    @Test
    void buildDefaultValidatorFactory_resolvesErasmus() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertNotNull(factory);
            assertTrue(factory.unwrap(ErasmusValidatorFactory.class) instanceof ErasmusValidatorFactory);
        }
    }

    @Test
    void getValidator_returnsAWorkingValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertNotNull(validator);
            assertTrue(validator.validate("anything").isEmpty(), "a bean with no constraints has no violations");
        }
    }

    @Test
    void usingContext_letsCallerOverrideComponents() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.usingContext()
                    .messageInterpolator(new ErasmusMessageInterpolator())
                    .getValidator();
            assertNotNull(validator);
        }
    }

    @Test
    void unwrap_toUnrelatedType_throwsValidationException() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThrows(ValidationException.class, () -> factory.unwrap(String.class));
        }
    }
}
