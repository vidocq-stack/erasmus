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

import jakarta.validation.ElementKind;
import jakarta.validation.Path;

/**
 * A single, non-cascaded property node. Cascading (bean/container-element nodes,
 * indices, map keys) lands with graph validation — see ROADMAP M3/M4.
 */
final class NodeImpl implements Path.PropertyNode {

    private final String name;

    NodeImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isInIterable() {
        return false;
    }

    @Override
    public Integer getIndex() {
        return null;
    }

    @Override
    public Object getKey() {
        return null;
    }

    @Override
    public ElementKind getKind() {
        return ElementKind.PROPERTY;
    }

    @Override
    public <T extends Path.Node> T as(Class<T> nodeType) {
        if (nodeType.isInstance(this)) {
            return nodeType.cast(this);
        }
        throw new ClassCastException("Cannot cast to " + nodeType.getName());
    }

    @Override
    public Class<?> getContainerClass() {
        return null;
    }

    @Override
    public Integer getTypeArgumentIndex() {
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
