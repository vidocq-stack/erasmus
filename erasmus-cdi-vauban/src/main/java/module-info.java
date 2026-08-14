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
/**
 * Placeholder module — CDI integration (Validator/ValidatorFactory beans,
 * {@code @ValidateOnExecution} interceptor, container-managed
 * {@code ConstraintValidator}s) is ROADMAP M7. Real dependencies on
 * {@code jakarta.cdi}/{@code io.vidocq.vauban.api} land with that milestone,
 * following the {@code src/main/module-info/} workaround pattern used by
 * {@code cervantes-cdi-vauban} once the Vauban annotation processor is wired in.
 */
module io.vidocq.erasmus.cdi.vauban {
    requires transitive io.vidocq.erasmus.core;
}
