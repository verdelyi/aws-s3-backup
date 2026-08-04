#!/bin/bash
# Downloads a single object (or a "prefix*" wildcard of objects) from S3 to a local directory.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() {
    echo "Usage: $0 --config <config.json> <s3-key> <target-dir>"
    exit 1
}

CONFIG=""
ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --config) CONFIG="$2"; shift 2 ;;
        *) ARGS+=("$1"); shift ;;
    esac
done
[[ -z "$CONFIG" || ${#ARGS[@]} -ne 2 ]] && usage

build_if_needed
"$BIN" "$CONFIG" DOWNLOAD "${ARGS[0]}" "${ARGS[1]}"
