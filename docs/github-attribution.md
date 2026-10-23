# GitHub commit attribution

Only SierraSupercomputer has repository access. An older local Git identity used an email linked to the unrelated Ethan account. The owner authorized correcting479 local/agent-authored commits. All484 historical file trees, messages, dates and merge relationships are preserved; the owner's5 original GitHub commits and signatures are unchanged. Previous histories and the old-to-new map remain in the coordinator's backups. GitHub may cache its contributor display after a history change.

Future coordinator commits must honor the repository's local user.name/user.email. In this repository run:

```powershell
node tools/install-github-attribution.mjs
```

This sets the owner's GitHub noreply identity and installs a pre-push hook in the common Git hooks directory, covering linked worktrees too. The hook checks all commits newly reachable on each pushed ref, so a correctly authored tip cannot silently reintroduce old Ethan/agent commits. An existing hook or custom hooksPath is preserved and requires explicit chaining. Hooks are local: repeat installation in any other clone.

Policy: tools/github-attribution.json. Checks: node --test tools/github-attribution.test.mjs. No Minecraft source, recipe, asset or built jar changes are needed for the metadata correction. The coordinator's historical verification receipts retain their original executed commit IDs; the attribution map explains their corrected equivalents.
