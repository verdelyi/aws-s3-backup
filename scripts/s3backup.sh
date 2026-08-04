#!/bin/bash
# Convenience runner for aws-s3-backup: builds if needed, then runs the requested mode.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$REPO_ROOT/build/install/aws-s3-backup/bin/aws-s3-backup"

usage() {
    cat >&2 <<'EOF'
Usage: s3backup.sh --config <config.json> <mode> [args]

Modes:
  --upload-batch              Upload everything listed in the config's batchItems
  --download <key> <dir>      Download one object (or "prefix*") into dir, decrypting if needed
  --delete <key>              Delete one object
  --check [prefix]            Report whether stored objects still have the metadata a
                              restore needs; no contents downloaded. Non-zero if any problem
  --list [prefix] [format]    List objects; format is NICE (default) or SIMPLE
  --new-key                   Generate a new encryption key and write it into the config
                              (needs jq; files under the old key stop decrypting)

Examples:
  s3backup.sh --config cfg.json --upload-batch
  s3backup.sh --config cfg.json --check
  s3backup.sh --config cfg.json --download Documents.zip /tmp/restore
EOF
    exit 1
}

build_if_needed() {
    (cd "$REPO_ROOT" && ./gradlew installDist -q)
}

CONFIG=""
MODE=""
ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --config) [[ $# -ge 2 ]] || usage; CONFIG="$2"; shift 2 ;;
        --upload-batch|--download|--delete|--check|--list|--new-key)
            [[ -z "$MODE" ]] || { echo "ERROR: pick one mode, got $MODE and $1" >&2; usage; }
            MODE="$1"; shift ;;
        -h|--help) usage ;;
        -*) echo "ERROR: unknown option $1" >&2; usage ;;
        *) ARGS+=("$1"); shift ;;
    esac
done

[[ -n "$CONFIG" && -n "$MODE" ]] || usage
[[ -f "$CONFIG" ]] || { echo "ERROR: config file not found: $CONFIG" >&2; exit 1; }

# Each mode declares how many trailing arguments it accepts, so a typo is caught here
# rather than surfacing as a confusing error from the app.
check_argc() {
    local min=$1 max=$2
    if [[ ${#ARGS[@]} -lt $min || ${#ARGS[@]} -gt $max ]]; then
        if [[ $min -eq $max ]]; then
            echo "ERROR: $MODE takes exactly $min argument(s), got ${#ARGS[@]}" >&2
        else
            echo "ERROR: $MODE takes $min to $max arguments, got ${#ARGS[@]}" >&2
        fi
        usage
    fi
}

case "$MODE" in
    --upload-batch) check_argc 0 0 ;;
    --download)     check_argc 2 2 ;;
    --delete)       check_argc 1 1 ;;
    --check)        check_argc 0 1 ;;
    --list)         check_argc 0 2 ;;
    --new-key)      check_argc 0 0; command -v jq >/dev/null || { echo "ERROR: jq is required for --new-key" >&2; exit 1; } ;;
esac

build_if_needed

case "$MODE" in
    --upload-batch) "$BIN" "$CONFIG" UPLOAD-BATCH ;;
    --download)     "$BIN" "$CONFIG" DOWNLOAD "${ARGS[0]}" "${ARGS[1]}" ;;
    --delete)       "$BIN" "$CONFIG" DELETE "${ARGS[0]}" ;;
    --check)        "$BIN" "$CONFIG" CHECK "${ARGS[0]:-}" ;;
    --list)         "$BIN" "$CONFIG" LIST "${ARGS[0]:-}" "${ARGS[1]:-NICE}" ;;
    --new-key)
        NEW_KEY_HEX="$("$BIN" "$CONFIG" KEYGEN | tail -n1)"
        TMP_CONFIG="$(mktemp)"
        jq --arg key "$NEW_KEY_HEX" '.encryptionKeyHex = $key' "$CONFIG" > "$TMP_CONFIG"
        mv "$TMP_CONFIG" "$CONFIG"
        echo "Updated encryptionKeyHex in $CONFIG"
        echo "NOTE: files encrypted with the old key won't decrypt with this one -- keep a backup of the old key if you still need those files."
        ;;
esac
