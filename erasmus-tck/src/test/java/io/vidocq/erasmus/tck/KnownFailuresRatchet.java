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
package io.vidocq.erasmus.tck;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the official TCK into a ratchet that moves in one direction only.
 *
 * <p>{@code tck-known-failures.txt} lists, one {@code fully.qualified.TestClass#method} per line,
 * every TCK test Erasmus does not pass yet. After each test method runs:
 * <ul>
 *   <li>listed and failing — expected: reported as <em>skipped</em>, with the original failure
 *       as its reason, so it does not fail the build;</li>
 *   <li>listed and passing — reported as a <em>failure</em>: it now passes, so its line must be
 *       removed in the same commit, and the commit's diff shows which TCK tests it turned green;</li>
 *   <li>not listed and failing — a regression: left as the failure it is.</li>
 * </ul>
 *
 * <p>At the end of the suite it writes {@code target/tck-summary.txt}: per TCK package, how many
 * tests pass and how many are still listed. Status changes are made in {@code afterInvocation},
 * which TestNG calls before the result reaches surefire and the reporters.
 */
public final class KnownFailuresRatchet implements IInvokedMethodListener, ISuiteListener {

    static final String KNOWN_FAILURES_PROPERTY = "erasmus.tck.knownFailures";
    static final String SUMMARY_PROPERTY = "erasmus.tck.summary";
    private static final String TCK_TESTS_PACKAGE = "org.hibernate.beanvalidation.tck.tests.";

    private final Set<String> knownFailures = load(Path.of(System.getProperty(KNOWN_FAILURES_PROPERTY,
            "tck-known-failures.txt")));
    /** Outcome as Erasmus actually produced it, before any rewriting: true = passed. */
    private final Map<String, Boolean> outcomes = new ConcurrentHashMap<>();

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod()) {
            return;
        }
        String key = result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
        boolean known = knownFailures.contains(key);
        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> {
                outcomes.put(key, true);
                if (known) {
                    result.setStatus(ITestResult.FAILURE);
                    result.setThrowable(new AssertionError(key
                            + " now passes: remove it from tck-known-failures.txt in this commit"));
                }
            }
            case ITestResult.FAILURE -> {
                outcomes.put(key, false);
                if (known) {
                    Throwable original = result.getThrowable();
                    result.setStatus(ITestResult.SKIP);
                    result.setThrowable(new KnownFailure(key, original));
                }
            }
            default -> {
                // skipped by TestNG itself (a failed dependency, a selector): nothing to judge
            }
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        Map<String, int[]> perPackage = new TreeMap<>();
        outcomes.forEach((key, passed) -> {
            String className = key.substring(0, key.indexOf('#'));
            String pkg = className.substring(0, className.lastIndexOf('.')).replace(TCK_TESTS_PACKAGE, "");
            perPackage.computeIfAbsent(pkg, p -> new int[2])[passed ? 0 : 1]++;
        });
        long passed = outcomes.values().stream().filter(p -> p).count();
        StringBuilder summary = new StringBuilder()
                .append("# Erasmus against the Jakarta Validation 3.1 TCK, per package\n")
                .append("# passing  failing  package\n");
        perPackage.forEach((pkg, counts) ->
                summary.append(String.format("%9d %8d  %s%n", counts[0], counts[1], pkg)));
        summary.append(String.format("%9d %8d  TOTAL (%d run)%n", passed, outcomes.size() - passed, outcomes.size()));
        Set<String> stale = new LinkedHashSet<>(knownFailures);
        stale.removeAll(outcomes.keySet());
        if (!stale.isEmpty() && stale.size() < knownFailures.size()) {
            summary.append("\n# Listed in tck-known-failures.txt but not run (renamed, removed, or excluded):\n");
            stale.forEach(key -> summary.append("#   ").append(key).append('\n'));
        }
        Path out = Path.of(System.getProperty(SUMMARY_PROPERTY, "target/tck-summary.txt"));
        try {
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, summary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> load(Path file) {
        try {
            Set<String> keys = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    keys.add(trimmed);
                }
            }
            return Set.copyOf(keys);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the TCK known-failures list " + file.toAbsolutePath(), e);
        }
    }

    /** The reason attached to a listed test that failed as expected. */
    static final class KnownFailure extends RuntimeException {
        KnownFailure(String key, Throwable original) {
            super("known failure, listed in tck-known-failures.txt: " + key
                    + (original == null ? "" : " — " + original), original, false, false);
        }
    }
}
