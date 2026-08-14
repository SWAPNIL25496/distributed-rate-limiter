#!/usr/bin/env bash
# subagentStop hook: after phase-executor completes, prompt the parent agent to run doc-sync.
set -euo pipefail

input=$(cat)

status=$(printf '%s' "$input" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("status",""))' 2>/dev/null || true)

if [[ "$status" != "completed" ]]; then
  echo '{}'
  exit 0
fi

python3 -c 'import json; print(json.dumps({
  "followup_message": (
    "**Workflow handoff (post phase-executor):** Invoke the **doc-sync** subagent "
    "(background) to sync implementation status into the planning SDD per "
    "`.cursor/rules/agent/documentation-sync.mdc` and `.cursor/rules/agent/workflow-boundaries.mdc`. "
    "Update the Phase Implementation Status table (Status/Notes only; preserve PR). "
    "Do not invoke **test-runner** until **doc-sync** has finished."
  )
}))'
