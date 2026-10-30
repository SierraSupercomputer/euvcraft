# NeoForge 26.3.0.99 compatibility

The development checkout now targets NeoForge **26.3.0.99** for Minecraft 26.3 and Java 25. The released 0.7.0 JAR remains the earlier 26.3.0.69-beta build; this local upgrade does not replace that release or tag.

WorldEdit 7.4.6 requires NeoForge 26.3.0.83 or newer. It failed dependency checks on the old development loader. Version 26.3.0.99 is the latest Minecraft 26.3 build in the official Maven metadata inspected October 30, 2026.

## Results

- Fresh Minecraft/NeoForge artifacts generated for 26.3.0.99; no old-loader artifact substitution.
- 1,402 unit cases in 174 classes pass without failures, errors or skips; core imports and JAR packaging pass.
- All 347 existing project GameTests pass, retaining every case from the T7 baseline.
- WorldEdit 7.4.6 loads with Arcforge 2.8.1+26.3, Encoded Logistics 1.5.0+26.3 and EUVCRAFT in the isolated shipping-mode test server. Its required shipping probe passes and the server shuts down normally.
- Arcforge API 1.2.0 remains compile-only, absent from main/test runtime and the actual server launch.
- No Java compatibility patch was necessary. Production change is the loader version; documentation explains the development/release distinction.

## Trying WorldEdit

After closing your current client normally, put the Minecraft 26.3 NeoForge WorldEdit JAR in run/mods and launch runClient again. Gradle will prepare the upgraded loader on the next launch. Other runtime dependencies are already supplied by the build; do not duplicate them in run/mods or add the developer API JAR.

The existing client, its build outputs and worlds were left untouched during verification. WorldEdit was installed only in the isolated test checkout. Graphical client startup and interactive WorldEdit operations still need a manual check; dedicated-server loading does not establish that those interactions work. No GitHub push, release replacement, player-world conversion or gameplay rebalance is included.

Development JAR SHA-256: 583406bd5085c65957ce6005e3d35b1ef7a0b2a0243a4462c13ff223cfd4fa95. Its embedded mod version remains 0.7.0 and it requires NeoForge 26.3.0.99 or newer. Use the explicitly named development artifact instead of confusing it with the published 0.7.0 download.
