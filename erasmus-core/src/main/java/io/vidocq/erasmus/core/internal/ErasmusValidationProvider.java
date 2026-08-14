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

import jakarta.validation.Configuration;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;

/**
 * Erasmus's {@link ValidationProvider}, discovered via {@code ServiceLoader}
 * (see {@code module-info.java}'s {@code provides} clause). Instantiated
 * reflectively by the JDK's {@code ServiceLoader} — requires a public
 * no-arg constructor, satisfied implicitly here.
 */
public final class ErasmusValidationProvider implements ValidationProvider<ErasmusConfiguration> {

    @Override
    public ErasmusConfiguration createSpecializedConfiguration(BootstrapState state) {
        return new ErasmusConfiguration(this);
    }

    @Override
    public Configuration<?> createGenericConfiguration(BootstrapState state) {
        return new ErasmusConfiguration(this);
    }

    @Override
    public ValidatorFactory buildValidatorFactory(ConfigurationState configurationState) {
        return new ErasmusValidatorFactory(configurationState);
    }
}
