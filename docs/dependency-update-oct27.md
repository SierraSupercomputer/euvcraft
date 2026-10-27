# Dependency update — October27

Owner supplied Arcforge2.8.1+26.3, Encoded Logistics1.5.0+26.3 and developer API1.2.0+26.3 in UPDATED_MODS. Original input files are preserved; the drop-off folder is locally ignored by Git. Runtime jars in libs replace the older2.5.1/1.2.1 versions; Git/backups retain the originals. The API input filename has been normalized by removing the download suffix (1), without changing its bytes.

The API jar is compileOnly and must never be installed in a client/server mods folder, included on the runtime classpath, or embedded in the mod jar. Arcforge supplies these API classes at runtime. Full runtime jars remain on the compile classpath too because existing native conduit/port fixtures intentionally use actual Arcforge implementations. No fixture/source-set refactor in this dependency update.

Required dependency minimums now match the tested2.8.1/1.5.0 runtime versions. Both new tooltip classes still contain the exact onTooltip StringSplitter.splitLines call intercepted by the existing required async-safe redirect, so that compatibility patch is retained. Its actual descriptor/instruction was checked against the supplied jar bytecode; graphical client acceptance is separate.

Validation in progress: actual main/test runtime classpath guards, compile, units, native pipe/port/resource/FOUP regressions, shipping and packaging checks. No execution pass claimed yet. No client installation or user-world edit.

Supplied SHA256:

- arcforge-2.8.1+26.3.jar: 7d5ba791bf18bd72162e7b6c7145c3d0558446cbf1c1d4689b03d6e7faadc27c
- encodedlogistics-1.5.0+26.3.jar: bd90a075913489dc98541fa8260c4acb94505fcc815bb8a99e68a336ad244965
- arcforge-api-1.2.0+26.3.jar: 52c31d1691c2d3e1a67e51ac5cd378e6c7375d7127bed6cb60ff32d6666218fc
