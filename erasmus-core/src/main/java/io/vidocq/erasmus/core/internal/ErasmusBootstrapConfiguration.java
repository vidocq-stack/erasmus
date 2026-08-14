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

import jakarta.validation.BootstrapConfiguration;
import jakarta.validation.executable.ExecutableType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Constant {@link BootstrapConfiguration}: no {@code META-INF/validation.xml} is
 * read (XML mapping is deferred — see ROADMAP M10), so bootstrap always reports
 * the spec's own defaults.
 */
public final class ErasmusBootstrapConfiguration implements BootstrapConfiguration {

    public static final ErasmusBootstrapConfiguration INSTANCE = new ErasmusBootstrapConfiguration();

    private ErasmusBootstrapConfiguration() {
    }

    @Override
    public String getDefaultProviderClassName() {
        return null;
    }

    @Override
    public String getConstraintValidatorFactoryClassName() {
        return null;
    }

    @Override
    public String getMessageInterpolatorClassName() {
        return null;
    }

    @Override
    public String getTraversableResolverClassName() {
        return null;
    }

    @Override
    public String getParameterNameProviderClassName() {
        return null;
    }

    @Override
    public String getClockProviderClassName() {
        return null;
    }

    @Override
    public Set<String> getValueExtractorClassNames() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getConstraintMappingResourcePaths() {
        return Collections.emptySet();
    }

    @Override
    public boolean isExecutableValidationEnabled() {
        return true;
    }

    @Override
    public Set<ExecutableType> getDefaultValidatedExecutableTypes() {
        return EnumSet.of(ExecutableType.CONSTRUCTORS, ExecutableType.NON_GETTER_METHODS);
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.emptyMap();
    }
}
