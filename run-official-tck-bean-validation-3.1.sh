#!/usr/bin/env bash
# shellcheck shell=bash
#
# Runs the official Jakarta Validation 3.1 TCK
# (jakarta.validation:validation-tck-tests:3.1.1, Maven Central) against Erasmus.
#
# Modes:
#   ./run-official-tck-bean-validation-3.1.sh                    # the whole suite
#   ./run-official-tck-bean-validation-3.1.sh all                # same
#   ./run-official-tck-bean-validation-3.1.sh -Dtest=SomeTckTest # one TCK test class
#
# The whole suite runs in plain Java SE (TestNG + Arquillian standalone adapter) in
# about ten seconds, so there is no separate smoke mode.
#
# What it does:
#   1. Installs erasmus-api and erasmus-core locally (./mvnw install -DskipTests).
#   2. Runs ./mvnw -Ptck -pl erasmus-tck test [args...]
#      (erasmus-tck is in-reactor, enabled by the `tck` Maven profile).
#   3. Writes erasmus-tck/target/tck-report.txt (totals) and prints
#      erasmus-tck/target/tck-summary.txt (passing / failing per TCK package).
#
# The run is a ratchet: erasmus-tck/tck-known-failures.txt lists the TCK tests Erasmus
# does not pass yet. A listed test that passes, or an unlisted test that fails, fails
# the run. When a change makes TCK tests pass, remove their lines in the same commit.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TCK_DIR="${ROOT_DIR}/erasmus-tck"
REPORT_FILE="${TCK_DIR}/target/tck-report.txt"
MVN="${ROOT_DIR}/mvnw"

mode="${1:-all}"
shift || true

case "${mode}" in
    all)
        echo "==> Mode: ALL (official Jakarta Validation 3.1 TCK)"
        ;;
    -Dtest=*)
        set -- "${mode}" "$@"
        echo "==> Mode: targeted (${mode})"
        ;;
    *)
        echo "Usage: $0 [all|-Dtest=TckTestName]" >&2
        exit 64
        ;;
esac

echo "==> Step 1/2: install erasmus-api and erasmus-core (./mvnw install -DskipTests)"
( cd "${ROOT_DIR}" && "${MVN}" -ntp -pl erasmus-core -am install -DskipTests )

echo "==> Step 2/2: run erasmus-tck (profile tck)"
mkdir -p "${TCK_DIR}/target"

set +e
( cd "${ROOT_DIR}" && "${MVN}" -ntp -Ptck -pl erasmus-tck test "$@" ) | tee "${REPORT_FILE}.raw"
status=${PIPESTATUS[0]}
set -e

{
    echo "# Erasmus TCK report"
    echo "# Generated $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# Args: $*"
    echo
    grep -E "^\[(INFO|ERROR|WARNING)\] Tests run:" "${REPORT_FILE}.raw" | tail -1 || true
    echo
    echo "Maven exit status: ${status}"
} > "${REPORT_FILE}"

cat "${REPORT_FILE}"
if [ -f "${TCK_DIR}/target/tck-summary.txt" ]; then
    echo
    cat "${TCK_DIR}/target/tck-summary.txt"
fi
exit "${status}"
