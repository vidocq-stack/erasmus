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
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Issue #16 — bootstrap on a <em>class path</em>, where module-info's {@code provides} clause
 * does not exist: {@code ServiceLoader} only sees {@code META-INF/services}. This is how the
 * official TCK, Weld and OpenLiberty load Erasmus.
 *
 * <p>Run by its own surefire execution ({@code class-path-bootstrap}, {@code useModulePath=false})
 * and excluded from the default, module-path one, where {@link ErasmusBootstrapTest} already
 * covers the {@code provides} route.
 */
class ClassPathBootstrapTest {

    @Test
    void buildDefaultValidatorFactory_findsErasmusOnTheClassPath() {
        assertFalse(getClass().getModule().isNamed(),
                "this test must run on the class path, or it proves nothing about META-INF/services");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertInstanceOf(ErasmusValidatorFactory.class, factory.unwrap(ErasmusValidatorFactory.class),
                    "the default provider on the class path must be Erasmus");
        }
    }
}
