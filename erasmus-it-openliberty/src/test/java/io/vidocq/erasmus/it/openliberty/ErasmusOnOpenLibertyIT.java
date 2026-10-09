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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #16: Erasmus, bundled in a WAR, on an OpenLiberty server that enables only
 * {@code restfulWS-3.1} — no {@code beanValidation} feature. The resource bootstraps through
 * {@code Validation.buildDefaultValidatorFactory()}, so it only works if Erasmus is found on the
 * web application's class path, through {@code META-INF/services}.
 */
class ErasmusOnOpenLibertyIT {

    private static final String BASE = "http://localhost:" + System.getProperty("liberty.http.port")
            + "/erasmus-it/validate";

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void blankCity_isReportedByErasmus_atTheCascadedPath() throws Exception {
        HttpResponse<String> response = get(BASE + "?city=");

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("""
                factory: io.vidocq.erasmus.core.internal.ErasmusValidatorFactory
                violations: 1
                address.city: must not be blank
                """, response.body());
    }

    @Test
    void validCity_hasNoViolation() throws Exception {
        HttpResponse<String> response = get(BASE + "?city=Rotterdam");

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("""
                factory: io.vidocq.erasmus.core.internal.ErasmusValidatorFactory
                violations: 0
                """, response.body());
    }

    private HttpResponse<String> get(String uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
