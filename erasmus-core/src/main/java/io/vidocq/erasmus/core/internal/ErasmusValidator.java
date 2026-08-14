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
import io.vidocq.erasmus.core.internal.metadata.ConstraintDescriptorImpl;
import io.vidocq.erasmus.core.internal.metadata.ConstraintMetadataBuilder;
import io.vidocq.erasmus.core.internal.metadata.PropertyMetadata;
import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Property-level Bean Validation engine (ROADMAP M1). No groups (every constraint
 * is evaluated regardless of the {@code groups} vararg — see ROADMAP M3), no
 * cascading (see M3), no container-element unwrapping (see M4), no executable
 * validation (see M5): a flat {@code validate(bean)} over directly annotated
 * fields/getters, built-in constraints only.
 */
final class ErasmusValidator implements Validator {

    private final MessageInterpolator messageInterpolator;
    private final TraversableResolver traversableResolver;
    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ParameterNameProvider parameterNameProvider;
    private final ClockProvider clockProvider;
    private final ConcurrentMap<Class<?>, BeanMetadata> metadataCache;

    ErasmusValidator(MessageInterpolator messageInterpolator, TraversableResolver traversableResolver,
                      ConstraintValidatorFactory constraintValidatorFactory, ParameterNameProvider parameterNameProvider,
                      ClockProvider clockProvider, ConcurrentMap<Class<?>, BeanMetadata> metadataCache) {
        this.messageInterpolator = messageInterpolator;
        this.traversableResolver = traversableResolver;
        this.constraintValidatorFactory = constraintValidatorFactory;
        this.parameterNameProvider = parameterNameProvider;
        this.clockProvider = clockProvider;
        this.metadataCache = metadataCache;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("object must not be null");
        }
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) object.getClass();
        BeanMetadata metadata = metadataCache.computeIfAbsent(beanClass, ConstraintMetadataBuilder::build);

        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        for (PropertyMetadata property : metadata.properties()) {
            Object value = property.accessor().get(object);
            validateProperty(object, beanClass, property, value, violations);
        }
        return violations;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("object must not be null");
        }
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) object.getClass();
        PropertyMetadata property = propertyOf(beanClass, propertyName);
        Object value = property.accessor().get(object);

        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        validateProperty(object, beanClass, property, value, violations);
        return violations;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName, Object value,
                                                           Class<?>... groups) {
        PropertyMetadata property = propertyOf(beanType, propertyName);
        Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
        // Per spec: getRootBean() legitimately returns null here — there is no bean instance,
        // only a candidate value for a property that has not been assigned to one yet.
        validateProperty(null, beanType, property, value, violations);
        return violations;
    }

    private PropertyMetadata propertyOf(Class<?> beanClass, String propertyName) {
        BeanMetadata metadata = metadataCache.computeIfAbsent(beanClass, ConstraintMetadataBuilder::build);
        return metadata.property(propertyName)
                .orElseThrow(() -> new IllegalArgumentException(propertyName + " is not a property of " + beanClass.getName()));
    }

    private <T> void validateProperty(T rootBean, Class<T> rootBeanClass, PropertyMetadata property,
                                       Object value, Set<ConstraintViolation<T>> violations) {
        for (ConstraintDescriptorImpl<?> descriptor : property.constraints()) {
            validateConstraint(rootBean, rootBeanClass, property.name(), property.accessor().getType(),
                    value, descriptor, violations);
        }
    }

    private <T, A extends Annotation> void validateConstraint(
            T rootBean, Class<T> rootBeanClass, String propertyName, Class<?> declaredType, Object value,
            ConstraintDescriptorImpl<A> descriptor, Set<ConstraintViolation<T>> violations) {

        // Resolution uses the declared (static) type when the value is null — the runtime
        // class of `null` does not exist, and every built-in validator besides @NotNull
        // must treat null as trivially valid regardless of which candidate gets picked.
        Class<?> targetType = value != null ? value.getClass() : declaredType;
        List<Class<? extends ConstraintValidator<A, ?>>> candidates = descriptor.getConstraintValidatorClasses();
        Class<? extends ConstraintValidator<A, ?>> validatorClass = ConstraintValidatorResolver.resolve(candidates, targetType);

        @SuppressWarnings("unchecked")
        ConstraintValidator<A, Object> validator =
                (ConstraintValidator<A, Object>) constraintValidatorFactory.getInstance(validatorClass);
        validator.initialize(descriptor.getAnnotation());

        ErasmusConstraintValidatorContext context = new ErasmusConstraintValidatorContext(descriptor, clockProvider);
        boolean valid;
        try {
            valid = validator.isValid(value, context);
        } finally {
            constraintValidatorFactory.releaseInstance(validator);
        }
        if (valid) {
            return;
        }

        List<String> templates = new ArrayList<>();
        if (!context.isDefaultConstraintViolationDisabled()) {
            templates.add(descriptor.getMessageTemplate());
        }
        templates.addAll(context.getCustomMessageTemplates());

        for (String template : templates) {
            String message = messageInterpolator.interpolate(template, new MessageInterpolatorContextImpl(descriptor, value));
            violations.add(new ConstraintViolationImpl<>(message, template, rootBean, rootBeanClass, rootBean, value,
                    PathImpl.ofProperty(propertyName), descriptor));
        }
    }

    @Override
    public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
        throw new UnsupportedOperationException("Constraint metadata API lands in ROADMAP M6");
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap " + getClass().getName() + " as " + type.getName());
    }

    @Override
    public ExecutableValidator forExecutables() {
        throw new UnsupportedOperationException("Executable validation lands in ROADMAP M5");
    }
}
