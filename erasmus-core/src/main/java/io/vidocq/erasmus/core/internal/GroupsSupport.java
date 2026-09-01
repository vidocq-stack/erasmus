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

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Group resolution for {@code validate(bean, groups...)} (ROADMAP M3): group inheritance
 * (a group interface extending others pulls in the supers) and {@code @GroupSequence}
 * short-circuiting.
 *
 * <p><b>Deliberate scope gap</b>: mixing a {@code @GroupSequence} group with other,
 * unrelated groups in the same call collapses everything into one unordered sheet instead
 * of correctly interleaving the sequence's short-circuit with the other groups — only a
 * single requested group that is itself a sequence gets the ordered-sheets treatment. Rare
 * in practice (most calls pass either {@code Default} or one custom sequence), documented
 * rather than silently wrong.
 */
final class GroupsSupport {

    private GroupsSupport() {
    }

    /**
     * The ordered list of "sheets" to evaluate one at a time, stopping at the first sheet
     * that produces any violation. A plain group request (including no groups at all, which
     * defaults to {@link Default}) collapses to a single sheet containing every requested
     * group's expansion. A single requested group that is itself {@code @GroupSequence}-annotated
     * expands into one sheet per step of that sequence, in order.
     */
    static List<List<Class<?>>> resolveSheets(Class<?>[] requestedGroups) {
        Class<?>[] groups = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;

        if (groups.length == 1 && groups[0].isAnnotationPresent(GroupSequence.class)) {
            List<List<Class<?>>> sheets = new ArrayList<>();
            for (Class<?> step : groups[0].getAnnotation(GroupSequence.class).value()) {
                sheets.add(List.copyOf(expand(step)));
            }
            return List.copyOf(sheets);
        }

        Set<Class<?>> merged = new LinkedHashSet<>();
        for (Class<?> group : groups) {
            merged.addAll(expand(group));
        }
        return List.of(List.copyOf(merged));
    }

    /** True if any of a constraint's declared groups appears in the effective (expanded) sheet. */
    static boolean intersects(Set<Class<?>> constraintGroups, List<Class<?>> effectiveGroups) {
        for (Class<?> group : constraintGroups) {
            if (effectiveGroups.contains(group)) {
                return true;
            }
        }
        return false;
    }

    /** A group plus every group interface it extends, recursively. */
    private static Set<Class<?>> expand(Class<?> group) {
        Set<Class<?>> expanded = new LinkedHashSet<>();
        collect(group, expanded);
        return expanded;
    }

    private static void collect(Class<?> group, Set<Class<?>> into) {
        if (!into.add(group)) {
            return;
        }
        for (Class<?> superGroup : group.getInterfaces()) {
            collect(superGroup, into);
        }
    }
}
