#!/usr/bin/env sh
set -eu

fail() {
  printf 'repository policy check failed: %s\n' "$1" >&2
  exit 1
}

require_file() {
  [ -f "$1" ] || fail "missing required file: $1"
}

require_contains() {
  file="$1"
  pattern="$2"
  message="$3"
  grep -Eq "$pattern" "$file" || fail "$message"
}

require_file README.md
require_file CONTRIBUTING.md
require_file SECURITY.md
require_file CHANGELOG.md
require_file CODEOWNERS
require_file .gitignore
require_file .env.example
require_file .github/pull_request_template.md
require_file .github/workflows/repository-policy.yml
require_file docs/notion-delivery-workflow.md
require_file docs/android-adb-install.md
require_file docs/repository-structure.md
require_file docs/adr/README.md
require_file docs/adr/0001-phase-1-e2ee-mandatory.md

require_contains README.md 'docs/adr/0001-phase-1-e2ee-mandatory\.md' 'README must link ADR-0001'
require_contains README.md 'CONTRIBUTING\.md' 'README must link contribution guide'
require_contains README.md 'SECURITY\.md' 'README must link security policy'
require_contains README.md 'adb install' 'README must preserve Android/WearOS ADB install guidance'
require_contains CONTRIBUTING.md 'Status.*Ready' 'CONTRIBUTING must preserve Notion Ready gate'
require_contains SECURITY.md 'Do not open public issues' 'SECURITY must document private vulnerability reporting'
require_contains .github/pull_request_template.md 'Security / privacy / E2EE review' 'PR template must include security/E2EE review prompt'
require_contains .github/pull_request_template.md 'Android / WearOS install impact' 'PR template must include Android/WearOS impact prompt'
require_contains docs/adr/0001-phase-1-e2ee-mandatory.md '^Status: Accepted' 'ADR-0001 must remain accepted'
require_contains docs/repository-structure.md 'apps/android/' 'Repository structure must reserve Android app path'
require_contains docs/repository-structure.md 'apps/wearos/' 'Repository structure must reserve WearOS app path'
require_contains docs/repository-structure.md 'infra/docker/' 'Repository structure must reserve Docker infrastructure path'

# Guard against common accidental live-secret files. Placeholder examples in .env.example are allowed.
if git ls-files | grep -E '(^|/)(\.env$|\.env\.(local|production|prod|staging)$|.*\.(pem|key|jks|keystore)$)' >/dev/null; then
  fail 'tracked secret-like file detected'
fi

printf 'repository policy check passed\n'
