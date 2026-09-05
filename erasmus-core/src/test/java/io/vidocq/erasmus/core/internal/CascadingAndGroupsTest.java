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
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ROADMAP M3: {@code @Valid} cascading, cycle detection, groups, and {@code @GroupSequence}. */
class CascadingAndGroupsTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // --- Cascading ---

    private static final class Address {
        @NotBlank
        private String city;

        Address(String city) {
            this.city = city;
        }
    }

    private static final class PersonWithCascadedAddress {
        @Valid
        private Address address;

        PersonWithCascadedAddress(Address address) {
            this.address = address;
        }
    }

    private static final class PersonWithPlainAddress {
        private Address address;

        PersonWithPlainAddress(Address address) {
            this.address = address;
        }
    }

    @Test
    void cascading_descendsIntoValidAnnotatedProperty_withDottedPath() {
        // "" (not null) — @NotBlank treats null as trivially valid, same null-safety
        // convention as every built-in validator except @NotNull.
        Set<ConstraintViolation<PersonWithCascadedAddress>> violations =
                validator.validate(new PersonWithCascadedAddress(new Address("")));

        assertEquals(1, violations.size());
        assertEquals("address.city", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void cascading_nullProperty_producesNoViolationsAndNoNpe() {
        assertTrue(validator.validate(new PersonWithCascadedAddress(null)).isEmpty());
    }

    @Test
    void withoutValidAnnotation_nestedBeanIsNotChecked() {
        assertTrue(validator.validate(new PersonWithPlainAddress(new Address(""))).isEmpty());
    }

    // --- Cycle detection ---

    private static final class Node {
        @NotNull
        private String name;

        @Valid
        private Node next;

        Node(String name) {
            this.name = name;
        }
    }

    @Test
    void circularGraph_terminatesAndValidatesEachNodeOnce() {
        Node a = new Node(null);
        Node b = new Node(null);
        a.next = b;
        b.next = a;

        Set<ConstraintViolation<Node>> violations = validator.validate(a);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("next.name")));
    }

    @Test
    void selfReferencingNode_terminates() {
        Node self = new Node(null);
        self.next = self;

        Set<ConstraintViolation<Node>> violations = validator.validate(self);

        assertEquals(1, violations.size());
        assertEquals("name", violations.iterator().next().getPropertyPath().toString());
    }

    // --- Groups ---

    private interface Strict {
    }

    /**
     * Exactly two constraints, in two different groups, and the fixture below violates both at
     * once: {@code username} is blank (@NotBlank, no groups() declared, so the Default group)
     * and {@code password} is shorter than 8 (@Size, declared in the Strict group only).
     * Each call below therefore has to report one of them and skip the other — which is what
     * makes them tell "filter by group" apart from "evaluate everything".
     */
    private static final class Account {
        @NotBlank
        private String username;

        @Size(min = 8, groups = Strict.class)
        private String password;

        Account(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    /** The violated property paths, so a failure prints which constraints fired, not just how many. */
    private static Set<String> violatedPaths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    void defaultGroup_onlyEvaluatesDefaultGroupConstraints() {
        Set<ConstraintViolation<Account>> violations = validator.validate(new Account("", "short"));

        assertEquals(Set.of("username"), violatedPaths(violations),
                "Default requested: @NotBlank on username must fire, @Size(groups = Strict) on password must not");
    }

    @Test
    void explicitGroup_onlyEvaluatesThatGroupsConstraints() {
        Set<ConstraintViolation<Account>> violations = validator.validate(new Account("", "short"), Strict.class);

        assertEquals(Set.of("password"), violatedPaths(violations),
                "Strict requested: @Size(groups = Strict) on password must fire, @NotBlank on username must not");
    }

    // --- Group inheritance ---

    private interface BaseGroup {
    }

    private interface ExtendedGroup extends BaseGroup {
    }

    private interface UnrelatedGroup {
    }

    /**
     * Two constraints, both violated, in two groups with no relation to each other:
     * {@code sku} under {@code BaseGroup}, which {@code ExtendedGroup} extends, and
     * {@code ean} under {@code UnrelatedGroup}, which it does not. Requesting
     * {@code ExtendedGroup} therefore has to pull in the first and leave the second alone —
     * expanding a group means walking up its super-interfaces, not taking everything in sight.
     */
    private static final class Item {
        @NotNull(groups = BaseGroup.class)
        private String sku;

        @NotNull(groups = UnrelatedGroup.class)
        private String ean;

        Item(String sku, String ean) {
            this.sku = sku;
            this.ean = ean;
        }
    }

    @Test
    void groupInheritance_extendedGroupPullsInBaseGroupConstraints() {
        Set<ConstraintViolation<Item>> violations = validator.validate(new Item(null, null), ExtendedGroup.class);

        assertEquals(Set.of("sku"), violatedPaths(violations),
                "ExtendedGroup extends BaseGroup, so sku must fire; UnrelatedGroup is unrelated, so ean must not");
    }

    // --- @GroupSequence short-circuiting ---

    private interface StepOne {
    }

    private interface StepTwo {
    }

    @GroupSequence({StepOne.class, StepTwo.class})
    private interface OrderedSequence {
    }

    private static final class Form {
        @NotBlank(groups = StepOne.class)
        private String field1;

        @NotBlank(groups = StepTwo.class)
        private String field2;

        Form(String field1, String field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    @Test
    void groupSequence_stopsAtFirstFailingGroup() {
        Set<ConstraintViolation<Form>> violations = validator.validate(new Form("", ""), OrderedSequence.class);

        assertEquals(1, violations.size());
        assertEquals("field1", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void groupSequence_proceedsToSecondGroupWhenFirstPasses() {
        Set<ConstraintViolation<Form>> violations = validator.validate(new Form("ok", ""), OrderedSequence.class);

        assertEquals(1, violations.size());
        assertEquals("field2", violations.iterator().next().getPropertyPath().toString());
    }
}
