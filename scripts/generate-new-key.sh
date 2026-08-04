#!/bin/bash
# Generates a new encryption key and writes it into the config file's "encryptionKeyHex" field.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() {
    echo "Usage: $0 --config <config.json>"
    exit 1
}

CONFIG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --config) CONFIG="$2"; shift 2 ;;
        *) usage ;;
    esac
done
[[ -z "$CONFIG" ]] && usage

command -v jq >/dev/null || { echo "ERROR: jq is required" >&2; exit 1; }

build_if_needed
NEW_KEY_HEX="$("$BIN" "$CONFIG" KEYGEN | tail -n1)"

TMP_CONFIG="$(mktemp)"
jq --arg key "$NEW_KEY_HEX" '.encryptionKeyHex = $key' "$CONFIG" > "$TMP_CONFIG"
mv "$TMP_CONFIG" "$CONFIG"
echo "Updated encryptionKeyHex in $CONFIG"
echo "NOTE: files encrypted with the old key won't decrypt with this one -- keep a backup of the old key if you still need those files."
