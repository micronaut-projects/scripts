#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECTS_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

MODULES_FILE="${MODULES_FILE:-${PROJECTS_DIR}/micronaut-docs-index/modules.yml}"
JAR_FILE="${JAR_FILE:-${SCRIPT_DIR}/build/libs/github-actions-canceller-0.1-all.jar}"
OWNER="${OWNER:-micronaut-projects}"
EXCLUDED_REPOSITORY="micronaut-core"
DRY_RUN=false

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Cancel all cancellable GitHub Actions workflow runs for repositories listed in
micronaut-docs-index/modules.yml, except ${EXCLUDED_REPOSITORY}.

Options:
  -d, --dry-run              List runs without cancelling them.
  -m, --modules-file FILE    Use FILE instead of the default modules.yml.
      --jar FILE             Use FILE instead of the default fat JAR.
      --owner OWNER          Use OWNER instead of ${OWNER}.
  -h, --help                 Show this help message.

The GitHub token must be available in GITHUB_TOKEN.
EOF
}

while (($# > 0)); do
    case "$1" in
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -m|--modules-file)
            if (($# < 2)); then
                echo "Error: $1 requires a file path." >&2
                exit 2
            fi
            MODULES_FILE="$2"
            shift 2
            ;;
        --jar)
            if (($# < 2)); then
                echo "Error: --jar requires a file path." >&2
                exit 2
            fi
            JAR_FILE="$2"
            shift 2
            ;;
        --owner)
            if (($# < 2)); then
                echo "Error: --owner requires an owner name." >&2
                exit 2
            fi
            OWNER="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! -r "$MODULES_FILE" ]]; then
    echo "Error: modules file is not readable: $MODULES_FILE" >&2
    exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
    echo "Error: canceller JAR not found: $JAR_FILE" >&2
    echo "Build it first with: ./gradlew shadowJar" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Error: java is required but was not found on PATH." >&2
    exit 1
fi

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
    echo "Error: GITHUB_TOKEN is not set." >&2
    exit 1
fi

mapfile -t repositories < <(
    awk -v excluded="$EXCLUDED_REPOSITORY" '
        /^[[:space:]]*slug:[[:space:]]*/ {
            value = $0
            sub(/^[[:space:]]*slug:[[:space:]]*/, "", value)
            sub(/[[:space:]]+#.*$/, "", value)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            gsub(/"/, "", value)
            gsub(/\047/, "", value)
            sub(/\r$/, "", value)
            if (value != "" && value != excluded) {
                print value
            }
        }
    ' "$MODULES_FILE" | sort -u
)

if ((${#repositories[@]} == 0)); then
    echo "Error: no slug entries found in $MODULES_FILE (after excluding $EXCLUDED_REPOSITORY)." >&2
    exit 1
fi

if [[ "$DRY_RUN" == true ]]; then
    mode="DRY RUN"
    action_args=(-d)
else
    mode="CANCEL"
    action_args=()
fi

echo "$mode mode: processing ${#repositories[@]} repositories for $OWNER"

failures=0
for repository in "${repositories[@]}"; do
    echo
    echo "==> $OWNER/$repository"
    if ! java -jar "$JAR_FILE" -o "$OWNER" -r "$repository" "${action_args[@]}"; then
        echo "Error: failed to process $OWNER/$repository" >&2
        failures=$((failures + 1))
    fi
done

if ((failures > 0)); then
    echo
    echo "Completed with $failures failed repository operation(s)." >&2
    exit 1
fi

echo
echo "Completed successfully."
