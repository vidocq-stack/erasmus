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

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean Validation 3.1 built-in constraints: {@code @Past}/{@code @PastOrPresent}/
 * {@code @Future}/{@code @FutureOrPresent}, for the three temporal types covered so far
 * ({@code Instant}, {@code LocalDate}, {@code java.util.Date} — the rest of the spec's
 * temporal type list is a documented gap, see ROADMAP).
 */
class PastFutureValidatorTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-06-15T12:00:00Z");
    private static final ConstraintValidatorContext FIXED_NOW = fixedClockContext(NOW_INSTANT);

    private static ConstraintValidatorContext fixedClockContext(Instant instant) {
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        ClockProvider clockProvider = () -> clock;
        return new ConstraintValidatorContext() {
            @Override
            public void disableDefaultConstraintViolation() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getDefaultConstraintMessageTemplate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ClockProvider getClockProvider() {
                return clockProvider;
            }

            @Override
            public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T unwrap(Class<T> type) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void instant() {
        Instant past = NOW_INSTANT.minusSeconds(60);
        Instant future = NOW_INSTANT.plusSeconds(60);

        assertTrue(new PastValidatorForInstant().isValid(past, FIXED_NOW));
        assertFalse(new PastValidatorForInstant().isValid(future, FIXED_NOW));
        assertFalse(new PastValidatorForInstant().isValid(NOW_INSTANT, FIXED_NOW), "exactly now is not strictly past");

        assertTrue(new PastOrPresentValidatorForInstant().isValid(NOW_INSTANT, FIXED_NOW));
        assertFalse(new PastOrPresentValidatorForInstant().isValid(future, FIXED_NOW));

        assertTrue(new FutureValidatorForInstant().isValid(future, FIXED_NOW));
        assertFalse(new FutureValidatorForInstant().isValid(NOW_INSTANT, FIXED_NOW));

        assertTrue(new FutureOrPresentValidatorForInstant().isValid(NOW_INSTANT, FIXED_NOW));
        assertFalse(new FutureOrPresentValidatorForInstant().isValid(past, FIXED_NOW));

        assertTrue(new PastValidatorForInstant().isValid(null, FIXED_NOW), "null is trivially valid");
    }

    @Test
    void localDate() {
        LocalDate today = LocalDate.ofInstant(NOW_INSTANT, ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        assertTrue(new PastValidatorForLocalDate().isValid(yesterday, FIXED_NOW));
        assertFalse(new PastValidatorForLocalDate().isValid(today, FIXED_NOW));

        assertTrue(new PastOrPresentValidatorForLocalDate().isValid(today, FIXED_NOW));
        assertFalse(new PastOrPresentValidatorForLocalDate().isValid(tomorrow, FIXED_NOW));

        assertTrue(new FutureValidatorForLocalDate().isValid(tomorrow, FIXED_NOW));
        assertFalse(new FutureValidatorForLocalDate().isValid(today, FIXED_NOW));

        assertTrue(new FutureOrPresentValidatorForLocalDate().isValid(today, FIXED_NOW));
        assertFalse(new FutureOrPresentValidatorForLocalDate().isValid(yesterday, FIXED_NOW));
    }

    @Test
    void utilDate() {
        Date past = Date.from(NOW_INSTANT.minusSeconds(60));
        Date future = Date.from(NOW_INSTANT.plusSeconds(60));

        assertTrue(new PastValidatorForDate().isValid(past, FIXED_NOW));
        assertFalse(new PastValidatorForDate().isValid(future, FIXED_NOW));

        assertTrue(new FutureValidatorForDate().isValid(future, FIXED_NOW));
        assertFalse(new FutureValidatorForDate().isValid(past, FIXED_NOW));

        assertTrue(new PastOrPresentValidatorForDate().isValid(Date.from(NOW_INSTANT), FIXED_NOW));
        assertTrue(new FutureOrPresentValidatorForDate().isValid(Date.from(NOW_INSTANT), FIXED_NOW));
    }
}
