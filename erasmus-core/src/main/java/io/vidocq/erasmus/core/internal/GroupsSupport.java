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

import jakarta.validation.groups.Default;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Group resolution for {@code validate(bean, groups...)} (ROADMAP M3): which groups a call
 * asked for — including group inheritance, a group interface extending others pulls in the
 * supers — and whether a given constraint belongs to any of them. {@code @GroupSequence}
 * follows.
 */
final class GroupsSupport {

    private GroupsSupport() {
    }

    /**
     * The groups a call asked for, each expanded with the group interfaces it extends — no
     * groups at all means {@link Default}.
     */
    static List<Class<?>> resolve(Class<?>[] requestedGroups) {
        Class<?>[] groups = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;
        Set<Class<?>> merged = new LinkedHashSet<>();
        for (Class<?> group : groups) {
            merged.addAll(expand(group));
        }
        return List.copyOf(merged);
    }

    /** True if any of a constraint's declared groups appears in the effective (expanded) groups. */
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
