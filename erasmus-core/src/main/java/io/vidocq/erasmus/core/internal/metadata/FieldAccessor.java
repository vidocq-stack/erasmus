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

import jakarta.validation.ValidationException;

import java.lang.reflect.Field;

/**
 * Reads a property straight off its (possibly private) field.
 *
 * <p>{@code setAccessible(true)} is unavoidable here: the whole point of field-level
 * Bean Validation is to constrain private fields that have no public getter, and a
 * generic validator sitting in a different module than the bean cannot otherwise read
 * them. This is exactly the reflection cost that {@code erasmus-codegen-apt} (ROADMAP M5)
 * is designed to eliminate for beans processed at compile time — this accessor remains
 * the honest reflective fallback for everything else.
 */
final class FieldAccessor implements PropertyAccessor {

    private final Field field;

    FieldAccessor(Field field) {
        field.trySetAccessible();
        this.field = field;
    }

    @Override
    public Object get(Object bean) {
        try {
            return field.get(bean);
        } catch (IllegalAccessException e) {
            throw new ValidationException("Cannot read field " + field.getDeclaringClass().getName()
                    + "." + field.getName(), e);
        }
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }
}
