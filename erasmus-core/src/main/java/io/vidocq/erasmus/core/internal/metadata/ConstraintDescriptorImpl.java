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
package io.vidocq.erasmus.core.internal.metadata;

import io.vidocq.erasmus.core.internal.constraints.BuiltinConstraints;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflective {@link ConstraintDescriptor}: built once per (property, annotation)
 * pair by {@link ConstraintMetadataBuilder} and cached in {@code BeanMetadata}.
 *
 * <p>Composed constraints ({@code getComposingConstraints()}) and explicit
 * {@code groups}/{@code payload} attributes are out of scope until ROADMAP M2/M3
 * — every constraint is currently reported under the {@link Default} group only.
 */
public final class ConstraintDescriptorImpl<A extends Annotation> implements ConstraintDescriptor<A> {

    private final A annotation;
    private final String messageTemplate;
    private final Map<String, Object> attributes;
    private final List<Class<? extends ConstraintValidator<A, ?>>> validatorClasses;

    @SuppressWarnings("unchecked")
    public ConstraintDescriptorImpl(A annotation) {
        this.annotation = annotation;
        this.attributes = readAttributes(annotation);
        this.messageTemplate = (String) attributes.get("message");

        Class<? extends Annotation> constraintType = annotation.annotationType();
        Constraint meta = constraintType.getAnnotation(Constraint.class);
        if (meta == null) {
            throw new ValidationException(constraintType.getName() + " is not a @Constraint annotation");
        }
        // The spec's own built-in constraints (@NotNull, @Size, ...) declare
        // @Constraint(validatedBy = {}) — an empty array. Only user-defined custom
        // constraints populate it directly; built-ins are resolved via the
        // implementation's own default registry instead (see BuiltinConstraints).
        List<Class<? extends ConstraintValidator<?, ?>>> declared = List.of(meta.validatedBy());
        List<Class<? extends ConstraintValidator<?, ?>>> resolved =
                declared.isEmpty() ? BuiltinConstraints.validatorsFor(constraintType) : declared;
        if (resolved == null) {
            throw new ValidationException("No ConstraintValidator registered for " + constraintType.getName());
        }
        this.validatorClasses = (List<Class<? extends ConstraintValidator<A, ?>>>) (List<?>) resolved;
    }

    private static Map<String, Object> readAttributes(Annotation annotation) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Method member : annotation.annotationType().getDeclaredMethods()) {
            try {
                values.put(member.getName(), member.invoke(annotation));
            } catch (ReflectiveOperationException e) {
                throw new ValidationException("Cannot read attribute " + member.getName()
                        + " of " + annotation.annotationType().getName(), e);
            }
        }
        return Map.copyOf(values);
    }

    @Override
    public A getAnnotation() {
        return annotation;
    }

    @Override
    public String getMessageTemplate() {
        return messageTemplate;
    }

    @Override
    public Set<Class<?>> getGroups() {
        return Set.of(Default.class);
    }

    @Override
    public Set<Class<? extends Payload>> getPayload() {
        return Set.of();
    }

    @Override
    public ConstraintTarget getValidationAppliesTo() {
        return ConstraintTarget.IMPLICIT;
    }

    @Override
    public List<Class<? extends ConstraintValidator<A, ?>>> getConstraintValidatorClasses() {
        return validatorClasses;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getComposingConstraints() {
        return Set.of();
    }

    @Override
    public boolean isReportAsSingleViolation() {
        return false;
    }

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        return ValidateUnwrappedValue.DEFAULT;
    }

    @Override
    public <U> U unwrap(Class<U> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap " + getClass().getName() + " as " + type.getName());
    }
}
