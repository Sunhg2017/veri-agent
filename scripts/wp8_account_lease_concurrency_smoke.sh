#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

main() {
  mvn -B -pl platform-api \
    -Dtest=TestAccountLeaseServiceTest#rejectsSecondActiveLeaseUntilRelease,DbProfileRepositoryContractTest#testDataRepositoryPersistsLeasesAndCleanupTasksThroughJdbc \
    test
}

main "$@"
