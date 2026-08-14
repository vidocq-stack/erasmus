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
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.ConfigurationState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ValidatorFactory}: resolves the effective components once (falling back
 * to the state's own defaults when the caller did not override them) and shares a
 * single constraint-metadata cache across every {@link Validator} it produces.
 */
public final class ErasmusValidatorFactory implements ValidatorFactory {

    private final MessageInterpolator messageInterpolator;
    private final TraversableResolver traversableResolver;
    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ParameterNameProvider parameterNameProvider;
    private final ClockProvider clockProvider;
    private final ConcurrentMap<Class<?>, BeanMetadata> metadataCache = new ConcurrentHashMap<>();

    public ErasmusValidatorFactory(ConfigurationState configurationState) {
        this.messageInterpolator = configurationState.getMessageInterpolator();
        this.traversableResolver = configurationState.getTraversableResolver();
        this.constraintValidatorFactory = configurationState.getConstraintValidatorFactory();
        this.parameterNameProvider = configurationState.getParameterNameProvider();
        this.clockProvider = configurationState.getClockProvider();
    }

    @Override
    public Validator getValidator() {
        return new ErasmusValidator(messageInterpolator, traversableResolver, constraintValidatorFactory,
                parameterNameProvider, clockProvider, metadataCache);
    }

    @Override
    public ValidatorContext usingContext() {
        return new ErasmusValidatorContext(messageInterpolator, traversableResolver,
                constraintValidatorFactory, parameterNameProvider, clockProvider, metadataCache);
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
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
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap " + getClass().getName() + " as " + type.getName());
    }

    @Override
    public void close() {
        // Nothing to release: constraint validator instances are not pooled at the
        // factory level (each isValid() call releases its own instance immediately).
    }
}
