#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

printf '%s\n' 'Running isolated MySQL migration acceptance (V001-V065 and V020-V065).'
printf '%s\n' 'Testcontainers creates temporary containers; this script never touches deploy Compose volumes.'
cd "$repo_dir/backend"
./mvnw test -Dtest=MigrationAcceptanceTest
printf '%s\n' 'Migration acceptance passed. Full Docker/API/browser UAT remains a separate gate.'
