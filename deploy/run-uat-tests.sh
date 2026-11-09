#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' 'This legacy UAT script is retired: it writes to fixed plan IDs and uses obsolete APIs.' >&2
printf '%s\n' 'Run the current isolated backend and Playwright acceptance cases instead.' >&2
exit 2
