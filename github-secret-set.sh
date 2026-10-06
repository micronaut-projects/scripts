#!/usr/bin/env bash
# Create (or update) a GitHub Actions secret in a repository.
#
# Usage:   github-secret-set.sh [-o <org>] <repo> <secret-name> [<secret-value>]
#          -o, --org <org>  GitHub organization (default: micronaut-projects)
#          If <secret-value> is omitted, it is read from stdin (or prompted for
#          on a terminal), which keeps it out of the shell history.
# Example: github-secret-set.sh micronaut-core MY_SECRET s3cr3t
#          github-secret-set.sh -o my-org my-repo MY_SECRET < secret.txt
# Requires: gh (authenticated with permission to set repo secrets)

set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }
usage() { die "usage: $(basename "$0") [-o <org>] <repo> <secret-name> [<secret-value>]"; }

ORG="micronaut-projects"
args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o|--org)  [[ $# -ge 2 ]] || usage; ORG="$2"; shift 2 ;;
    --org=*)   ORG="${1#*=}"; shift ;;
    -h|--help) sed -n '2,10s/^# \{0,1\}//p' "$0"; exit 0 ;;
    --)        shift; args+=("$@"); break ;;
    *)         args+=("$1"); shift ;;
  esac
done

[[ ${#args[@]} -eq 2 || ${#args[@]} -eq 3 ]] || usage

REPO="${args[0]}"
SECRET_NAME="${args[1]}"

[[ -n "$ORG" && -n "$REPO" && -n "$SECRET_NAME" ]] || usage

command -v gh >/dev/null || die "gh not found"

if [[ ${#args[@]} -eq 3 ]]; then
  SECRET_VALUE="${args[2]}"
elif [[ -t 0 ]]; then
  read -r -s -p "Value for $SECRET_NAME: " SECRET_VALUE
  echo
else
  SECRET_VALUE=$(cat)
fi

[[ -n "$SECRET_VALUE" ]] || die "secret value is empty"

full_repo="$ORG/$REPO"

gh repo view "$full_repo" --json name >/dev/null 2>&1 || die "repository $full_repo not found or not accessible"

# Pipe via stdin so the value never appears in gh's process arguments
printf '%s' "$SECRET_VALUE" | gh secret set "$SECRET_NAME" -R "$full_repo" --app actions
echo "Set $SECRET_NAME secret on $full_repo"
