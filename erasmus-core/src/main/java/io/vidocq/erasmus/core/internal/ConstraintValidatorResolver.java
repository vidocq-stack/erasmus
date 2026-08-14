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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.UnexpectedTypeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Picks, among a constraint's declared {@code ConstraintValidator} candidates, the
 * one whose validated type is the most specific supertype of the target property
 * type — the spec's constraint validator resolution algorithm, in its simple form
 * (no ambiguity resolution beyond "most specific wins": the built-in constraints
 * through ROADMAP M1/M2 declare disjoint candidate types, so ties do not arise yet).
 */
final class ConstraintValidatorResolver {

    private ConstraintValidatorResolver() {
    }

    @SuppressWarnings("unchecked")
    static <A extends Annotation> Class<? extends ConstraintValidator<A, ?>> resolve(
            List<Class<? extends ConstraintValidator<A, ?>>> candidates, Class<?> targetType) {
        Class<?> boxedTarget = box(targetType);
        Class<? extends ConstraintValidator<A, ?>> best = null;
        Class<?> bestValidatedType = null;
        for (Class<? extends ConstraintValidator<A, ?>> candidate : candidates) {
            Class<?> validatedType = box(validatedType(candidate));
            if (!validatedType.isAssignableFrom(boxedTarget)) {
                continue;
            }
            if (best == null || bestValidatedType.isAssignableFrom(validatedType)) {
                best = candidate;
                bestValidatedType = validatedType;
            }
        }
        if (best == null) {
            throw new UnexpectedTypeException(
                    "No ConstraintValidator found for type " + targetType.getName() + " among " + candidates);
        }
        return best;
    }

    private static Class<?> validatedType(Class<? extends ConstraintValidator<?, ?>> validatorClass) {
        for (Type iface : validatorClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType parameterized
                    && parameterized.getRawType() == ConstraintValidator.class) {
                return rawClassOf(parameterized.getActualTypeArguments()[1]);
            }
        }
        throw new IllegalStateException(validatorClass.getName() + " must directly implement ConstraintValidator<A, T>");
    }

    private static Class<?> rawClassOf(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        throw new IllegalStateException("Unsupported validated type: " + type);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
