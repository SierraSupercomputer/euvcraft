# Repository instructions

This is SierraSupercomputer's owner-managed EUVCRAFT project.

- Use Git author and committer SierraSupercomputer with 195608235+SierraSupercomputer@users.noreply.github.com for coordinator-created commits. Never substitute Ethan, fabricated noreply accounts, OpenCode or orchestrator identities.
- Run node tools/install-github-attribution.mjs in a fresh clone before committing/pushing. This installs a local owner check and repository-only identity; it preserves unrelated existing hooks by refusing to overwrite them.
- The pre-push check must remain enabled. It checks every newly reachable commit, including ancestors from old branches. Correct attribution before pushing; do not bypass the check.
- Historical worktrees/branches from before the October23 attribution correction are inspection archives. Do not merge their old history back. Recreate work from current main or carefully replay only intended changes with the correct identity.
- The Codex coordinator owns Git, verification and backups. Workers edit only their assigned scope and never commit. Follow the coordinator's authoritative M1 decisions; older root planning documents are historical.
