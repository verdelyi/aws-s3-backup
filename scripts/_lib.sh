#!/bin/bash
# Shared helpers, sourced by the other scripts in this directory. Not meant to be run directly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BIN="$REPO_ROOT/build/install/aws-s3-backup/bin/aws-s3-backup"

build_if_needed() {
    (cd "$REPO_ROOT" && ./gradlew installDist -q)
}
