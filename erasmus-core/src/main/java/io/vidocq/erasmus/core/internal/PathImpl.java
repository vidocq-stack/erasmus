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

import jakarta.validation.Path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A path made of one or more property nodes — extended, one segment at a time, as
 * {@code @Valid} cascading descends into nested beans (ROADMAP M3). Container-element
 * nodes (index/key-bearing segments) are M4's job.
 */
final class PathImpl implements Path {

    private final List<Path.Node> nodes;

    private PathImpl(List<Path.Node> nodes) {
        this.nodes = nodes;
    }

    static PathImpl ofProperty(String propertyName) {
        return new PathImpl(List.of(new NodeImpl(propertyName)));
    }

    /**
     * A new path with {@code propertyName} appended as the next segment — used when
     * cascading into a nested bean's own property.
     */
    PathImpl append(String propertyName) {
        List<Path.Node> extended = new ArrayList<>(nodes);
        extended.add(new NodeImpl(propertyName));
        return new PathImpl(List.copyOf(extended));
    }

    @Override
    public Iterator<Path.Node> iterator() {
        return nodes.iterator();
    }

    @Override
    public String toString() {
        return nodes.stream().map(Path.Node::getName).collect(Collectors.joining("."));
    }
}
