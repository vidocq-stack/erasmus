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

import java.util.Iterator;
import java.util.List;

/**
 * A path made of a single property node. Multi-segment paths (cascaded graphs,
 * container-element nodes) land with ROADMAP M3/M4.
 */
final class PathImpl implements Path {

    private final List<Path.Node> nodes;

    private PathImpl(Path.Node node) {
        this.nodes = List.of(node);
    }

    static PathImpl ofProperty(String propertyName) {
        return new PathImpl(new NodeImpl(propertyName));
    }

    @Override
    public Iterator<Path.Node> iterator() {
        return nodes.iterator();
    }

    @Override
    public String toString() {
        return nodes.get(0).getName();
    }
}
