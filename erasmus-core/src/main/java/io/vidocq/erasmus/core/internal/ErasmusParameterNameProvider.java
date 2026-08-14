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

import jakarta.validation.ParameterNameProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link ParameterNameProvider}. Real names are used when the executable
 * was compiled with {@code -parameters}; otherwise falls back to {@code arg0},
 * {@code arg1}, ... as the spec allows. Not yet exercised until executable
 * validation lands (ROADMAP M5) — needed today only to satisfy bootstrap wiring.
 */
public final class ErasmusParameterNameProvider implements ParameterNameProvider {

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        return parameterNames(constructor);
    }

    @Override
    public List<String> getParameterNames(Method method) {
        return parameterNames(method);
    }

    private static List<String> parameterNames(Executable executable) {
        Parameter[] parameters = executable.getParameters();
        List<String> names = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            names.add(parameter.isNamePresent() ? parameter.getName() : "arg" + i);
        }
        return names;
    }
}
