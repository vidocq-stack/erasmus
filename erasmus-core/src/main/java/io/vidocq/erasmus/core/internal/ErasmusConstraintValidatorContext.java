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

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ConstraintValidatorContext} handed to each {@code ConstraintValidator.isValid(...)}
 * call. Supports the common case — the default violation, plus zero or more custom
 * templates added via {@code buildConstraintViolationWithTemplate(...).addConstraintViolation()}.
 * Custom property-node navigation ({@code addPropertyNode}, {@code addBeanNode}, ...) is not
 * needed by any built-in constraint before graph validation lands — see ROADMAP M3.
 */
final class ErasmusConstraintValidatorContext implements ConstraintValidatorContext {

    private final ConstraintDescriptor<?> descriptor;
    private final ClockProvider clockProvider;
    private final List<String> customMessageTemplates = new ArrayList<>();
    private boolean defaultConstraintViolationDisabled;

    ErasmusConstraintValidatorContext(ConstraintDescriptor<?> descriptor, ClockProvider clockProvider) {
        this.descriptor = descriptor;
        this.clockProvider = clockProvider;
    }

    boolean isDefaultConstraintViolationDisabled() {
        return defaultConstraintViolationDisabled;
    }

    List<String> getCustomMessageTemplates() {
        return customMessageTemplates;
    }

    void recordCustomMessageTemplate(String messageTemplate) {
        customMessageTemplates.add(messageTemplate);
    }

    @Override
    public void disableDefaultConstraintViolation() {
        defaultConstraintViolationDisabled = true;
    }

    @Override
    public String getDefaultConstraintMessageTemplate() {
        return descriptor.getMessageTemplate();
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider;
    }

    @Override
    public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
        return new ConstraintViolationBuilderImpl(messageTemplate, this);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new ValidationException("Cannot unwrap " + getClass().getName() + " as " + type.getName());
    }
}
