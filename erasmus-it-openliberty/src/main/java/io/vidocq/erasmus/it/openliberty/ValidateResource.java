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
package io.vidocq.erasmus.it.openliberty;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.Set;

/**
 * {@code GET /validate?city=...}: validates a {@link Person} living in that city through the
 * plain bootstrap API — {@code Validation.buildDefaultValidatorFactory()}, nothing injected — and
 * answers in plain text with the factory class that was found, then one line per violation.
 */
@Path("validate")
public class ValidateResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String validate(@QueryParam("city") String city) {
        StringBuilder out = new StringBuilder();
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<Person>> violations =
                    factory.getValidator().validate(new Person(new Address(city)));
            out.append("factory: ").append(factory.getClass().getName()).append('\n');
            out.append("violations: ").append(violations.size()).append('\n');
            violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .forEach(violation -> out.append(violation.getPropertyPath()).append(": ")
                            .append(violation.getMessage()).append('\n'));
        }
        return out.toString();
    }
}
