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
 * Resolves the {@code Class<?>... groups} varargs of a {@code validate*} call into an
 * ordered list of "sheets" — each sheet a flat, unordered set of groups to check together.
 *
 * <p>Group <em>inheritance</em> (a group interface extending others) always expands: every
 * requested group pulls in its own super-interfaces too. {@code @GroupSequence} additionally
 * introduces order and short-circuiting <em>between</em> sheets — but only when the whole
 * call targets a single group that is itself a sequence. Mixing a sequence group with other,
 * unrelated groups in the same call collapses everything into one unordered sheet instead of
 * inter-leaving the sequence's short-circuiting with the other groups — a deliberate,
 * documented scope decision for this first pass (ROADMAP M3); the common case (validating
 * against {@code Default} or against a single custom sequence) is exactly right.
 */
final class GroupsSupport {

    private GroupsSupport() {
    }

    static List<List<Class<?>>> resolveSheets(Class<?>[] requestedGroups) {
        Class<?>[] effective = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;

        if (effective.length == 1) {
            GroupSequence sequence = effective[0].getAnnotation(GroupSequence.class);
            if (sequence != null) {
                List<List<Class<?>>> sheets = new ArrayList<>();
                for (Class<?> sub : sequence.value()) {
                    sheets.add(List.copyOf(expand(sub)));
                }
                return List.copyOf(sheets);
            }
        }

        Set<Class<?>> merged = new LinkedHashSet<>();
        for (Class<?> group : effective) {
            merged.addAll(expand(group));
        }
        return List.of(List.copyOf(merged));
    }

    static boolean intersects(Set<Class<?>> constraintGroups, List<Class<?>> effectiveGroups) {
        for (Class<?> group : effectiveGroups) {
            if (constraintGroups.contains(group)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Class<?>> expand(Class<?> group) {
        Set<Class<?>> expanded = new LinkedHashSet<>();
        collect(group, expanded);
        return expanded;
    }

    private static void collect(Class<?> group, Set<Class<?>> expanded) {
        if (!expanded.add(group)) {
            return;
        }
        for (Class<?> superInterface : group.getInterfaces()) {
            collect(superInterface, expanded);
        }
    }
}
