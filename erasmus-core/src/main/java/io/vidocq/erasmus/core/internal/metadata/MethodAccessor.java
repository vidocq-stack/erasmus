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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads a property through its public getter. The getter method itself is public,
 * but a public method declared on a non-public class (e.g. a package-private bean,
 * or a private nested fixture in a test) is still not callable via reflection
 * without {@code setAccessible} — Java checks the declaring class's accessibility
 * too, not just the member's own modifiers.
 */
final class MethodAccessor implements PropertyAccessor {

    private final Method getter;

    MethodAccessor(Method getter) {
        getter.trySetAccessible();
        this.getter = getter;
    }

    @Override
    public Object get(Object bean) {
        try {
            return getter.invoke(bean);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ValidationException("Cannot invoke getter " + getter.getDeclaringClass().getName()
                    + "." + getter.getName(), e);
        }
    }

    @Override
    public Class<?> getType() {
        return getter.getReturnType();
    }
}
