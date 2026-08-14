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
import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;
import jakarta.validation.valueextraction.ValueExtractor;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Erasmus's {@link Configuration}. XML mapping ({@code addMapping}) and
 * {@code ignoreXmlConfiguration} are accepted and stored but not yet read
 * back anywhere — {@code META-INF/validation.xml} support is ROADMAP M10.
 */
public final class ErasmusConfiguration implements Configuration<ErasmusConfiguration> {

    private final ValidationProvider<ErasmusConfiguration> validationProvider;

    private boolean ignoreXml;
    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;
    private final Set<ValueExtractor<?>> valueExtractors = new LinkedHashSet<>();
    private final Set<InputStream> mappingStreams = new LinkedHashSet<>();
    private final Map<String, String> properties = new LinkedHashMap<>();

    public ErasmusConfiguration(ValidationProvider<ErasmusConfiguration> validationProvider) {
        this.validationProvider = validationProvider;
    }

    @Override
    public ErasmusConfiguration ignoreXmlConfiguration() {
        this.ignoreXml = true;
        return this;
    }

    @Override
    public ErasmusConfiguration messageInterpolator(MessageInterpolator interpolator) {
        this.messageInterpolator = interpolator;
        return this;
    }

    @Override
    public ErasmusConfiguration traversableResolver(TraversableResolver resolver) {
        this.traversableResolver = resolver;
        return this;
    }

    @Override
    public ErasmusConfiguration constraintValidatorFactory(ConstraintValidatorFactory factory) {
        this.constraintValidatorFactory = factory;
        return this;
    }

    @Override
    public ErasmusConfiguration parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public ErasmusConfiguration clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public ErasmusConfiguration addValueExtractor(ValueExtractor<?> extractor) {
        valueExtractors.add(extractor);
        return this;
    }

    @Override
    public ErasmusConfiguration addMapping(InputStream stream) {
        mappingStreams.add(stream);
        return this;
    }

    @Override
    public ErasmusConfiguration addProperty(String name, String value) {
        properties.put(name, value);
        return this;
    }

    @Override
    public MessageInterpolator getDefaultMessageInterpolator() {
        return new ErasmusMessageInterpolator();
    }

    @Override
    public TraversableResolver getDefaultTraversableResolver() {
        return new ErasmusTraversableResolver();
    }

    @Override
    public ConstraintValidatorFactory getDefaultConstraintValidatorFactory() {
        return new ErasmusConstraintValidatorFactory();
    }

    @Override
    public ParameterNameProvider getDefaultParameterNameProvider() {
        return new ErasmusParameterNameProvider();
    }

    @Override
    public ClockProvider getDefaultClockProvider() {
        return new ErasmusClockProvider();
    }

    @Override
    public BootstrapConfiguration getBootstrapConfiguration() {
        return ErasmusBootstrapConfiguration.INSTANCE;
    }

    @Override
    public ValidatorFactory buildValidatorFactory() {
        ConfigurationState state = new ErasmusConfigurationState(
                ignoreXml,
                messageInterpolator != null ? messageInterpolator : getDefaultMessageInterpolator(),
                Set.copyOf(mappingStreams),
                Set.copyOf(valueExtractors),
                constraintValidatorFactory != null ? constraintValidatorFactory : getDefaultConstraintValidatorFactory(),
                traversableResolver != null ? traversableResolver : getDefaultTraversableResolver(),
                parameterNameProvider != null ? parameterNameProvider : getDefaultParameterNameProvider(),
                clockProvider != null ? clockProvider : getDefaultClockProvider(),
                Map.copyOf(properties));
        return validationProvider.buildValidatorFactory(state);
    }
}
