# M0-01: Project Scaffold and Build Check - COMPLETION REPORT

**Date:** 2026-10-13  
**Project:** EUVCRAFT (mod id: `lithmod`)  
**Root Package:** `com.euvcraft.lithmod`  
**Target:** NeoForge 26.3 / Minecraft 26.3  

---

## Summary

Successfully created a buildable NeoForge 26.3 mod project that depends on Encoded Logistics and Arcforge, with proper package separation (core vs mc), JUnit 5 test setup, and automated core import verification.

---

## Files Created/Modified

### Build Configuration
| File | Changes |
|------|---------|
| `gradle.properties` | Updated `mod_id=lithmod`, `mod_name=EUVCRAFT`, `mod_group_id=com.euvcraft.lithmod` |
| `build.gradle` | Added local JAR dependencies (EL, Arcforge), JUnit 5, `checkCoreImports` task |
| `settings.gradle` | Unchanged (uses foojay resolver) |
| `.github/workflows/build.yml` | CI config matching MDK template |

### Mod Metadata
| File | Changes |
|------|---------|
| `src/main/templates/META-INF/neoforge.mods.toml` | Added required dependencies: `encodedlogistics` (after), `arcforge` (after) |

### Source Code
| File | Purpose |
|------|---------|
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | Mod entry class; logs "EUVCRAFT mod loading..." |
| `src/main/java/com/euvcraft/lithmod/core/ImportChecker.java` | Verifies no `net.minecraft.*` or `net.neoforged.*` imports in core |
| `src/test/java/com/euvcraft/lithmod/core/CoreTest.java` | Basic JUnit 5 tests (2 tests) |

### Resources
| File | Purpose |
|------|---------|
| `src/main/resources/assets/lithmod/lang/en_us.json` | Basic language entry |

### Local Dependencies (in `./libs/`)
- `encodedlogistics-1.2.1+26.3.jar`
- `arcforge-2.5.1+26.3.jar`
- `arcforge-api-1.2.0+26.3.jar`

---

## Package Structure

```
com.euvcraft.lithmod
├── core/          # Pure logic - NO Minecraft/NeoForge imports (enforced)
│   ├── ImportChecker.java
│   └── (CoreTest.java in test/)
└── mc/            # Minecraft-facing code
    └── EUVCRAFTMod.java
```

---

## Verification Results

| Command | Result |
|---------|--------|
| `./gradlew build` | ✅ BUILD SUCCESSFUL |
| `./gradlew test` | ✅ 2 tests passed (`testBasicMath`, `testCorePackageLoads`) |
| `./gradlew checkCoreImports` | ✅ Passes - no forbidden imports in core |
| Core import check with Minecraft import added | ✅ **Fails** as expected: "Forbidden Minecraft import" |
| `./gradlew runClient` | ✅ Dev client launches with all 3 mods loaded |

### Dev Client Log Evidence
```
[22:26:32] [modloading-worker-0/INFO] [ne.za.en.EncodedLogistics/]: Arcforge integration enabled (Arcforge 2.5.1+26.3, machine control API 1.1.0).
[22:26:35] [modloading-worker-0/INFO] [ne.za.ar.Arcforge/]: Arcforge initialized
[22:26:35] [modloading-worker-0/INFO] [co.eu.li.mc.EUVCRAFTMod/]: EUVCRAFT mod loading...
```

---

## Compliance with Requirements

| Requirement | Status |
|-------------|--------|
| Buildable NeoForge 26.3 mod | ✅ |
| Depends on Encoded Logistics & Arcforge | ✅ |
| Mod ID `lithmod` | ✅ |
| Two packages: `core` (no MC/NF imports) and `mc` | ✅ |
| Automated import check (`checkCoreImports`) | ✅ |
| JUnit 5 unit tests | ✅ |
| NeoForge GameTest support (standard approach) | ✅ |
| Placeholder mod entry class with log line | ✅ |
| CI configuration matching other repos | ✅ |
| Core import check fails when MC import added | ✅ Verified |

---

## Deviations from Spec

**None** - All requirements met as specified.

---

## Next Steps (M0-02)

The foundation is ready. Next task should implement in `com.euvcraft.lithmod.core`:
- Config system (tier data, yield %, core counts, etc.)
- Tier data model (T1–T8 with all reference data)
- Stat model / die rules
- Server-side randomness source
- Yield roll engine (per-wafer cumulative rolls vs effective yield)

All numeric defaults must come from config/data files - nothing hardcoded.

---

## Notes

- First dev client run takes ~2 minutes (Minecraft/NeoForge artifact download + recompile); subsequent runs are faster
- Configuration cache enables fast incremental builds
- `checkCoreImports` task runs on compiled sources from `compileJava` with working dir = project root