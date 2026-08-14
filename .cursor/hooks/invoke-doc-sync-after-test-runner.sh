#!/usr/bin/env bash
# subagentStop hook: after test-runner completes, prompt the parent agent to run doc-sync again.
set -euo pipefail

input=$(cat)

status=$(printf '%s' "$input" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("status",""))' 2>/dev/null || true)

if [[ "$status" != "completed" ]]; then
  echo '{}'
  exit 0
fi

python3 -c 'import json; print(json.dumps({
  "followup_message": (
    "**Workflow handoff (post test-runner):** Invoke the **doc-sync** subagent "
    "(background) to sync verification status into the planning SDD per "
    "`.cursor/rules/agent/documentation-sync.mdc`. "
    "Link the test report under `docs/sdd/test-reports/`, update verification status, "
    "coverage notes, failures, and residual risk. "
    "Do not mark a phase **Verified** without command-output evidence from the test report."
  )
}))'
