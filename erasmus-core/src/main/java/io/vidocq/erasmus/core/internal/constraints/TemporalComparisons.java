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
 * Shared comparison for the {@code @Past}/{@code @PastOrPresent}/{@code @Future}/
 * {@code @FutureOrPresent} family, against whatever each validator resolves as "now" via
 * {@code ClockProvider} (so the comparison itself stays generic over any {@code Comparable}
 * temporal type — only obtaining "now" needs a type-specific validator, since there is no
 * single common {@code now(Clock)} factory across {@code java.time} types and
 * {@code java.util.Date}).
 */
final class TemporalComparisons {

    private TemporalComparisons() {
    }

    static <T extends Comparable<T>> boolean isPast(T value, T now) {
        return value.compareTo(now) < 0;
    }

    static <T extends Comparable<T>> boolean isPastOrPresent(T value, T now) {
        return value.compareTo(now) <= 0;
    }

    static <T extends Comparable<T>> boolean isFuture(T value, T now) {
        return value.compareTo(now) > 0;
    }

    static <T extends Comparable<T>> boolean isFutureOrPresent(T value, T now) {
        return value.compareTo(now) >= 0;
    }
}
