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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROADMAP M3: {@code @Valid} cascading (with cycle detection), groups, group
 * inheritance, and {@code @GroupSequence} short-circuiting.
 */
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

    private static final class Person {
        @NotBlank
        private String name;

        @Valid
        private Address address;

        Person(String name, Address address) {
            this.name = name;
            this.address = address;
        }
    }

    @Test
    void cascadesIntoNestedBean_withDottedPath() {
        Person person = new Person("Erasmus", new Address(""));

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        ConstraintViolation<Person> violation = violations.iterator().next();
        assertEquals("address.city", violation.getPropertyPath().toString());
        assertEquals(person, violation.getRootBean(), "root bean stays the top-level Person even for a nested violation");
        assertEquals(person.address, violation.getLeafBean(), "leaf bean is the Address instance that actually failed");
    }

    @Test
    void nullCascadedProperty_isSkippedWithoutError() {
        Person person = new Person("Erasmus", null);

        assertTrue(validator.validate(person).isEmpty());
    }

    private static final class PersonWithoutValid {
        private Address address;

        PersonWithoutValid(Address address) {
            this.address = address;
        }
    }

    @Test
    void withoutAtValid_nestedBeanIsNeverCascadedIntoEvenIfInvalid() {
        PersonWithoutValid person = new PersonWithoutValid(new Address(""));

        assertTrue(validator.validate(person).isEmpty(), "no @Valid on the property means no cascading, full stop");
    }

    // --- Cycles ---

    private static final class Node {
        @NotBlank
        private String label;

        @Valid
        private Node next;

        Node(String label) {
            this.label = label;
        }
    }

    @Test
    void circularGraph_terminatesAndStillReportsRealViolations() {
        Node a = new Node("a");
        Node b = new Node("");
        a.next = b;
        b.next = a; // cycle back to the root

        Set<ConstraintViolation<Node>> violations = validator.validate(a);

        assertEquals(1, violations.size());
        assertEquals("next.label", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void selfReferencingNode_terminates() {
        Node self = new Node("");
        self.next = self;

        Set<ConstraintViolation<Node>> violations = validator.validate(self);

        assertEquals(1, violations.size(), "the cycle back into itself must not be re-validated a second time");
        assertEquals("label", violations.iterator().next().getPropertyPath().toString());
    }

    // --- Groups ---

    private interface Strict {
    }

    private static final class Account {
        @NotBlank
        private String username;

        @NotBlank(groups = Strict.class)
        private String password;

        Account(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    @Test
    void defaultGroup_onlyChecksDefaultGroupConstraints() {
        Account account = new Account("", "");

        Set<ConstraintViolation<Account>> violations = validator.validate(account);

        assertEquals(1, violations.size());
        assertEquals("username", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void explicitGroup_replacesDefaultEntirely() {
        Account account = new Account("", "");

        Set<ConstraintViolation<Account>> violations = validator.validate(account, Strict.class);

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }

    private interface AllChecks extends Default, Strict {
    }

    @Test
    void groupInheritance_expandsToEverySuperInterface() {
        Account account = new Account("", "");

        Set<ConstraintViolation<Account>> violations = validator.validate(account, AllChecks.class);

        assertEquals(2, violations.size());
    }

    // --- Group sequences ---

    private interface FirstPass {
    }

    private interface SecondPass {
    }

    @GroupSequence({FirstPass.class, SecondPass.class})
    private interface OrderedChecks {
    }

    private static final class Form {
        @NotBlank(groups = FirstPass.class)
        private String requiredField;

        @Size(min = 5, groups = SecondPass.class)
        private String detailField;

        Form(String requiredField, String detailField) {
            this.requiredField = requiredField;
            this.detailField = detailField;
        }
    }

    @Test
    void groupSequence_shortCircuitsAtTheFirstFailingGroup() {
        Form form = new Form("", "ab"); // both FirstPass and SecondPass would fail independently

        Set<ConstraintViolation<Form>> violations = validator.validate(form, OrderedChecks.class);

        assertEquals(1, violations.size(), "SecondPass must never even run once FirstPass has a violation");
        assertEquals("requiredField", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void groupSequence_proceedsToNextGroupOnceThePreviousOnePasses() {
        Form form = new Form("ok", "ab"); // FirstPass passes, SecondPass fails

        Set<ConstraintViolation<Form>> violations = validator.validate(form, OrderedChecks.class);

        assertEquals(1, violations.size());
        assertEquals("detailField", violations.iterator().next().getPropertyPath().toString());
    }
}
