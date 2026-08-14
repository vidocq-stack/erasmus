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

import io.vidocq.erasmus.core.internal.metadata.BeanMetadata;
import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.valueextraction.ValueExtractor;

import java.util.concurrent.ConcurrentMap;

/**
 * Per-{@code Validator} override of the factory's default components
 * ({@code ValidatorFactory.usingContext()}).
 */
final class ErasmusValidatorContext implements ValidatorContext {

    private final ConcurrentMap<Class<?>, BeanMetadata> metadataCache;

    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;

    ErasmusValidatorContext(MessageInterpolator messageInterpolator,
                             TraversableResolver traversableResolver, ConstraintValidatorFactory constraintValidatorFactory,
                             ParameterNameProvider parameterNameProvider, ClockProvider clockProvider,
                             ConcurrentMap<Class<?>, BeanMetadata> metadataCache) {
        this.messageInterpolator = messageInterpolator;
        this.traversableResolver = traversableResolver;
        this.constraintValidatorFactory = constraintValidatorFactory;
        this.parameterNameProvider = parameterNameProvider;
        this.clockProvider = clockProvider;
        this.metadataCache = metadataCache;
    }

    @Override
    public ValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
        this.messageInterpolator = messageInterpolator;
        return this;
    }

    @Override
    public ValidatorContext traversableResolver(TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
        return this;
    }

    @Override
    public ValidatorContext constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
        this.constraintValidatorFactory = constraintValidatorFactory;
        return this;
    }

    @Override
    public ValidatorContext parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public ValidatorContext clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public ValidatorContext addValueExtractor(ValueExtractor<?> extractor) {
        // Accepted but not yet consulted: container-element constraints (the only
        // thing that would need it) land in ROADMAP M4.
        return this;
    }

    @Override
    public Validator getValidator() {
        return new ErasmusValidator(messageInterpolator, traversableResolver, constraintValidatorFactory,
                parameterNameProvider, clockProvider, metadataCache);
    }
}
