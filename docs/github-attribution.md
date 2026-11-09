# GitHub commit attribution

Set up the repository identity and pre-push check in each clone.

~~~powershell
node tools/install-github-attribution.mjs
~~~

The installer sets the repository-local identity to SierraSupercomputer and installs the check in the common hooks directory for the clone and its linked worktrees. If the clone already uses a custom hooks path or a different pre-push hook, chain the checks as directed by the installer.

The check validates authors, committers and co-author trailers on commits newly reachable from the pushed refs. Start contributions from current main so the push uses the corrected attribution history.

~~~powershell
node --test tools/github-attribution.test.mjs
~~~
