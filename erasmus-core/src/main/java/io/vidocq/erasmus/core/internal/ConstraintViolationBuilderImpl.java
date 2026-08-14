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

import jakarta.validation.ConstraintValidatorContext;

/**
 * Minimal {@code ConstraintViolationBuilder}: supports the common
 * {@code buildConstraintViolationWithTemplate(msg).addConstraintViolation()} usage.
 * Custom node navigation is deferred to ROADMAP M3 (graph validation) — no built-in
 * constraint before then needs to redirect a violation to a different path segment.
 */
final class ConstraintViolationBuilderImpl implements ConstraintValidatorContext.ConstraintViolationBuilder {

    private final String messageTemplate;
    private final ErasmusConstraintValidatorContext owner;

    ConstraintViolationBuilderImpl(String messageTemplate, ErasmusConstraintValidatorContext owner) {
        this.messageTemplate = messageTemplate;
        this.owner = owner;
    }

    @Override
    public NodeBuilderDefinedContext addNode(String name) {
        throw unsupported();
    }

    @Override
    public NodeBuilderCustomizableContext addPropertyNode(String name) {
        throw unsupported();
    }

    @Override
    public LeafNodeBuilderCustomizableContext addBeanNode() {
        throw unsupported();
    }

    @Override
    public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(
            String name, Class<?> containerType, Integer typeArgumentIndex) {
        throw unsupported();
    }

    @Override
    public NodeBuilderDefinedContext addParameterNode(int index) {
        throw unsupported();
    }

    @Override
    public ConstraintValidatorContext addConstraintViolation() {
        owner.recordCustomMessageTemplate(messageTemplate);
        return owner;
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Custom violation node paths land with graph validation — see ROADMAP M3");
    }
}
