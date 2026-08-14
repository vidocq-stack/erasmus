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
package io.vidocq.erasmus.core.internal.constraints;

/**
 * Shared {@code min}/{@code max} sanity check for the four {@code @Size}
 * validators (one per target type — CharSequence/Collection/Map/Array).
 */
final class SizeValidatorSupport {

    private SizeValidatorSupport() {
    }

    static void validateParameters(int min, int max) {
        if (min < 0) {
            throw new IllegalArgumentException("The min parameter of @Size cannot be negative");
        }
        if (max < 0) {
            throw new IllegalArgumentException("The max parameter of @Size cannot be negative");
        }
        if (max < min) {
            throw new IllegalArgumentException("The max parameter of @Size cannot be less than its min parameter");
        }
    }
}
