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
 * <p>A call is resolved into the groups to process, <b>in order</b>: §5.4.2 requires that
 * "each group in a group sequence must be processed sequentially", stopping at the first one
 * that produces a violation. Each element of that list is one group materialised as a set of
 * interfaces — the group itself plus everything it inherits (§5.4.1). A call that names no
 * sequence yields a single element, so there is nothing to stop at; §6.1.3 covers the case of
 * several groups at once, which is "equivalent to the validation of a group G inheriting all
 * groups", hence one merged element rather than several.
 *
 * <p><b>Deliberate scope gap</b>: mixing a {@code @GroupSequence} group with other,
 * unrelated groups in the same call merges everything into that one element instead of
 * correctly interleaving the sequence's short-circuit with the other groups — only a single
 * requested group that is itself a sequence gets the ordered treatment. Rare in practice
 * (most calls pass either {@code Default} or one custom sequence), documented rather than
 * silently wrong.
 */
final class GroupsSupport {

    private GroupsSupport() {
    }

    /**
     * The groups to process, in order, each one expanded with the groups it inherits — the
     * caller evaluates them one at a time and stops at the first that produces a violation.
     * A plain request (including no groups at all, which defaults to {@link Default}) yields
     * a single element holding every requested group's expansion. A single requested group
     * that is itself {@code @GroupSequence}-annotated yields one element per group listed in
     * that sequence, in the order declared.
     */
    static List<List<Class<?>>> resolveOrderedGroups(Class<?>[] requestedGroups) {
        Class<?>[] groups = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;

        if (groups.length == 1 && groups[0].isAnnotationPresent(GroupSequence.class)) {
            List<List<Class<?>>> ordered = new ArrayList<>();
            for (Class<?> sequencedGroup : groups[0].getAnnotation(GroupSequence.class).value()) {
                ordered.add(List.copyOf(expand(sequencedGroup)));
            }
            return List.copyOf(ordered);
        }

        Set<Class<?>> merged = new LinkedHashSet<>();
        for (Class<?> group : groups) {
            merged.addAll(expand(group));
        }
        return List.of(List.copyOf(merged));
    }

    /** True if any of a constraint's declared groups appears in the group being evaluated. */
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
