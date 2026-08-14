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

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.valueextraction.ValueExtractor;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of an {@link ErasmusConfiguration}, handed to
 * {@link ErasmusValidationProvider#buildValidatorFactory(ConfigurationState)}.
 * Every component is already resolved to its default where the caller did not
 * override it — see {@link ErasmusConfiguration#buildValidatorFactory()}.
 */
record ErasmusConfigurationState(
        boolean ignoreXml,
        MessageInterpolator messageInterpolator,
        Set<InputStream> mappingStreams,
        Set<ValueExtractor<?>> valueExtractors,
        ConstraintValidatorFactory constraintValidatorFactory,
        TraversableResolver traversableResolver,
        ParameterNameProvider parameterNameProvider,
        ClockProvider clockProvider,
        Map<String, String> properties) implements ConfigurationState {

    @Override
    public boolean isIgnoreXmlConfiguration() {
        return ignoreXml;
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    @Override
    public Set<InputStream> getMappingStreams() {
        return mappingStreams;
    }

    @Override
    public Set<ValueExtractor<?>> getValueExtractors() {
        return valueExtractors;
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    @Override
    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider;
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }
}
