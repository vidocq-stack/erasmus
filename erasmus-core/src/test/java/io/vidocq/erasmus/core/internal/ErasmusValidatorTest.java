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
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end {@code validate(bean)} path (ROADMAP M1): no groups, no cascading,
 * no container-element unwrapping — a flat pass over directly annotated
 * fields/getters, built-in constraints only.
 */
class ErasmusValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Person validPerson() {
        return new Person("Erasmus", 42, List.of("Desiderius"), "erasmus@rotterdam.example");
    }

    @Test
    void validBean_hasNoViolations() {
        assertTrue(validator.validate(validPerson()).isEmpty());
    }

    @Test
    void nullName_violatesNotNull() {
        Person person = validPerson();
        person.name = null;

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        ConstraintViolation<Person> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
        assertEquals("must not be null", violation.getMessage());
        assertEquals(person, violation.getRootBean());
    }

    @Test
    void tooShortName_violatesSize() {
        Person person = validPerson();
        person.name = "A";

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        ConstraintViolation<Person> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
        assertEquals("size must be between 2 and 20", violation.getMessage());
        assertEquals("A", violation.getInvalidValue());
    }

    @Test
    void negativeAge_violatesMin() {
        Person person = validPerson();
        person.age = -1;

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        assertEquals("must be greater than or equal to 0", violations.iterator().next().getMessage());
    }

    @Test
    void tooOldAge_violatesMax() {
        Person person = validPerson();
        person.age = 200;

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        assertEquals("must be less than or equal to 150", violations.iterator().next().getMessage());
    }

    @Test
    void emptyNicknames_violatesNotEmpty() {
        Person person = validPerson();
        person.nicknames = List.of();

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        assertEquals("must not be empty", violations.iterator().next().getMessage());
    }

    @Test
    void blankEmail_violatesNotBlank_viaGetter() {
        Person person = validPerson();
        person.email = "   ";

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(1, violations.size());
        ConstraintViolation<Person> violation = violations.iterator().next();
        assertEquals("email", violation.getPropertyPath().toString());
        assertEquals("must not be blank", violation.getMessage());
    }

    @Test
    void multipleInvalidProperties_produceOneViolationEach() {
        Person person = new Person(null, -5, List.of(), " ");

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertEquals(4, violations.size());
    }

    @Test
    void validateProperty_onlyValidatesThatProperty() {
        Person person = validPerson();
        person.name = null;
        person.age = -5;

        Set<ConstraintViolation<Person>> violations = validator.validateProperty(person, "age");

        assertEquals(1, violations.size());
        assertEquals("age", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validateValue_hasNoRootBeanInstance() {
        Set<ConstraintViolation<Person>> violations = validator.validateValue(Person.class, "name", null);

        assertEquals(1, violations.size());
        ConstraintViolation<Person> violation = violations.iterator().next();
        assertEquals("must not be null", violation.getMessage());
        assertEquals(null, violation.getRootBean(), "no bean instance was ever supplied");
    }

    @Test
    void customMessageTemplate_interpolatesAttributes() {
        CustomMessageFixture fixture = new CustomMessageFixture();
        fixture.code = "abcdef";

        Set<ConstraintViolation<CustomMessageFixture>> violations = validator.validate(fixture);

        assertEquals(1, violations.size());
        assertEquals("between 1 and 5 please", violations.iterator().next().getMessage());
    }

    private static final class Person {
        @NotNull
        @Size(min = 2, max = 20)
        private String name;

        @Min(0)
        @Max(150)
        private int age;

        @NotEmpty
        private List<String> nicknames;

        private String email;

        Person(String name, int age, List<String> nicknames, String email) {
            this.name = name;
            this.age = age;
            this.nicknames = nicknames;
            this.email = email;
        }

        @NotBlank
        public String getEmail() {
            return email;
        }
    }

    private static final class CustomMessageFixture {
        @Size(min = 1, max = 5, message = "between {min} and {max} please")
        private String code;
    }
}
