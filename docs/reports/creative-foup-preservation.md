# Creative FOUP transport repair

Implemented, awaiting owner playtest. Creative E-inventory replacement packets previously returned public-only component projections, losing processed-lot rolls and frozen run contracts. The codec now appends a bounded optional full payload only within a connection-derived Creative packet scope. Default/Survival encode stays public; unauthorized decode skips the bounded private bytes without parsing them. Local and remote packet encoders/decoders use exception-safe nested scopes. The vanilla Creative handler independently checks current permission.

A successful game-mode change refreshes inventory/current menu, including unchanged stacks and cursor, so Survival-to-Creative supplies full state and the reverse transition redacts client state. Persistent CODEC remains unchanged. Exact copies preserve all saved outcomes, no rerolls. Previously lost processed data cannot be recovered. Matching client/server mod builds required. Reflow mechanics unchanged.

Free GLM supplied a time-limited code/test draft; coordinator corrected API imports/access, unsigned flags, borrowed-buffer ownership, length framing, assertions, added mode refresh and actual local/remote packet regressions. No worker execution claims accepted. Build/test evidence and final hash are held by coordinator. See ../manual-walkthrough-creative-foup.md.
