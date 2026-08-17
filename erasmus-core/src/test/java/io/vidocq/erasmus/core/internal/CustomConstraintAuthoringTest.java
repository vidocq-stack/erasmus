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

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROADMAP M2: custom constraint authoring — {@code @Constraint(validatedBy = ...)},
 * composed constraints, {@code @ReportAsSingleViolation}, and multiple validators for
 * different target types on one custom annotation. None of this is Erasmus's own
 * production API; it's user-authored code proving the mechanism works end-to-end.
 */
class CustomConstraintAuthoringTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // --- A simple custom constraint with its own validator ---

    @Constraint(validatedBy = PalindromeValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Palindrome {
        String message() default "'${validatedValue}' must be a palindrome";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class PalindromeValidator implements ConstraintValidator<Palindrome, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.contentEquals(new StringBuilder(value).reverse());
        }
    }

    private static final class Word {
        @Palindrome
        private String value;

        Word(String value) {
            this.value = value;
        }
    }

    @Test
    void customValidatedByConstraint_worksEndToEnd() {
        assertTrue(validator.validate(new Word("kayak")).isEmpty());

        Set<ConstraintViolation<Word>> violations = validator.validate(new Word("abc"));
        assertEquals(1, violations.size());
        assertEquals("value", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void customMessageTemplate_evaluatesElExpression() {
        Set<ConstraintViolation<Word>> violations = validator.validate(new Word("abc"));

        assertEquals("'abc' must be a palindrome", violations.iterator().next().getMessage());
    }

    // --- A composed constraint (no @ReportAsSingleViolation): every failing part reports its own violation ---

    @NotBlank
    @Size(min = 3, max = 20)
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Username {
        String message() default "invalid username";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    private static final class Account {
        @Username
        private String username;

        Account(String username) {
            this.username = username;
        }
    }

    @Test
    void composedConstraint_withoutReportAsSingleViolation_reportsEachFailingPart() {
        // blank AND too short: both @NotBlank and @Size(min=3) fail independently
        Set<ConstraintViolation<Account>> violations = validator.validate(new Account(""));

        assertEquals(2, violations.size());
        for (ConstraintViolation<Account> violation : violations) {
            assertEquals("username", violation.getPropertyPath().toString());
        }
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("must not be blank")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("size must be between 3 and 20")));
    }

    @Test
    void composedConstraint_allPartsPassing_isValid() {
        assertTrue(validator.validate(new Account("erasmus")).isEmpty());
    }

    // --- A composed constraint WITH @ReportAsSingleViolation: collapses to one violation ---

    @NotBlank
    @Size(min = 8, max = 100)
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrongPassword {
        String message() default "password does not meet strength requirements";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    private static final class Credentials {
        @StrongPassword
        private String password;

        Credentials(String password) {
            this.password = password;
        }
    }

    @Test
    void reportAsSingleViolation_collapsesEveryFailingPartIntoOne() {
        // blank AND too short: both composing constraints fail, but only ONE violation should surface
        Set<ConstraintViolation<Credentials>> violations = validator.validate(new Credentials(""));

        assertEquals(1, violations.size());
        assertEquals("password does not meet strength requirements", violations.iterator().next().getMessage());
    }

    @Test
    void reportAsSingleViolation_passingIsStillValid() {
        assertTrue(validator.validate(new Credentials("longenough12")).isEmpty());
    }

    // --- Multiple ConstraintValidators for different target types on one custom annotation ---

    @Constraint(validatedBy = {NonEmptyContainerValidatorForCollection.class, NonEmptyContainerValidatorForMap.class})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NonEmptyContainer {
        String message() default "must not be empty";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class NonEmptyContainerValidatorForCollection
            implements ConstraintValidator<NonEmptyContainer, java.util.Collection<?>> {
        @Override
        public boolean isValid(java.util.Collection<?> value, ConstraintValidatorContext context) {
            return value == null || !value.isEmpty();
        }
    }

    public static final class NonEmptyContainerValidatorForMap
            implements ConstraintValidator<NonEmptyContainer, Map<?, ?>> {
        @Override
        public boolean isValid(Map<?, ?> value, ConstraintValidatorContext context) {
            return value == null || !value.isEmpty();
        }
    }

    private static final class Basket {
        @NonEmptyContainer
        private List<String> items;

        Basket(List<String> items) {
            this.items = items;
        }
    }

    private static final class Inventory {
        @NonEmptyContainer
        private Map<String, Integer> stock;

        Inventory(Map<String, Integer> stock) {
            this.stock = stock;
        }
    }

    @Test
    void multipleValidatedByCandidates_resolveByRuntimeType() {
        assertEquals(1, validator.validate(new Basket(List.of())).size());
        assertTrue(validator.validate(new Basket(List.of("apple"))).isEmpty());

        assertEquals(1, validator.validate(new Inventory(Map.of())).size());
        assertTrue(validator.validate(new Inventory(Map.of("apple", 3))).isEmpty());
    }
}
