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

import jakarta.validation.Constraint;
import jakarta.validation.Valid;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflective constraint metadata builder — the fallback path (ROADMAP design
 * principles table: "Maximum static code generation"). Walks a bean class's
 * field hierarchy and public getters, collecting any annotation itself
 * meta-annotated {@code @Constraint} — generically, so every future built-in
 * or custom constraint (ROADMAP M2 onward) works with zero change here — plus
 * whether the property carries {@code @Valid} (ROADMAP M3 cascading).
 *
 * <p>Groups are resolved per-{@link ConstraintDescriptorImpl}, not here.
 * Container-element constraints are not yet resolved at this layer — see ROADMAP M4.
 */
public final class ConstraintMetadataBuilder {

    private ConstraintMetadataBuilder() {
    }

    public static BeanMetadata build(Class<?> beanClass) {
        Map<String, PropertyAccessor> accessorByProperty = new LinkedHashMap<>();
        Map<String, List<ConstraintDescriptorImpl<?>>> constraintsByProperty = new LinkedHashMap<>();
        Set<String> cascadedProperties = new LinkedHashSet<>();

        for (Class<?> type = beanClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                List<ConstraintDescriptorImpl<?>> descriptors = constraintDescriptorsOf(field.getAnnotations());
                boolean cascaded = isCascaded(field.getAnnotations());
                if (descriptors.isEmpty() && !cascaded) {
                    continue;
                }
                String name = field.getName();
                accessorByProperty.putIfAbsent(name, accessorFor(type, field));
                constraintsByProperty.computeIfAbsent(name, key -> new ArrayList<>()).addAll(descriptors);
                if (cascaded) {
                    cascadedProperties.add(name);
                }
            }
        }

        for (Method method : beanClass.getMethods()) {
            String propertyName = propertyNameOfGetter(method);
            if (propertyName == null) {
                continue;
            }
            List<ConstraintDescriptorImpl<?>> descriptors = constraintDescriptorsOf(method.getAnnotations());
            boolean cascaded = isCascaded(method.getAnnotations());
            if (descriptors.isEmpty() && !cascaded) {
                continue;
            }
            accessorByProperty.putIfAbsent(propertyName, new MethodAccessor(method));
            constraintsByProperty.computeIfAbsent(propertyName, key -> new ArrayList<>()).addAll(descriptors);
            if (cascaded) {
                cascadedProperties.add(propertyName);
            }
        }

        List<PropertyMetadata> properties = new ArrayList<>();
        for (String name : accessorByProperty.keySet()) {
            properties.add(new PropertyMetadata(
                    name, accessorByProperty.get(name),
                    List.copyOf(constraintsByProperty.getOrDefault(name, List.of())),
                    cascadedProperties.contains(name)));
        }
        return new BeanMetadata(beanClass, List.copyOf(properties));
    }

    private static List<ConstraintDescriptorImpl<?>> constraintDescriptorsOf(Annotation[] annotations) {
        List<ConstraintDescriptorImpl<?>> descriptors = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                descriptors.add(new ConstraintDescriptorImpl<>(annotation));
            }
        }
        return descriptors;
    }

    private static boolean isCascaded(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == Valid.class) {
                return true;
            }
        }
        return false;
    }

    private static PropertyAccessor accessorFor(Class<?> declaringType, Field field) {
        Method getter = findGetter(declaringType, field);
        return getter != null ? new MethodAccessor(getter) : new FieldAccessor(field);
    }

    private static Method findGetter(Class<?> type, Field field) {
        String capitalized = capitalize(field.getName());
        boolean isBoolean = field.getType() == boolean.class || field.getType() == Boolean.class;
        String getterName = isBoolean ? "is" + capitalized : "get" + capitalized;
        try {
            Method getter = type.getMethod(getterName);
            return getter.getParameterCount() == 0 ? getter : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String propertyNameOfGetter(Method method) {
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        String name = method.getName();
        Class<?> returnType = method.getReturnType();
        if (name.startsWith("get") && name.length() > 3 && returnType != void.class) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && (returnType == boolean.class || returnType == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
