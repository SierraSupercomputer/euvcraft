# Task M0-03: Tier Registry - Summary Report

## Overview
Implemented a data-driven definition of lithography tiers T1 through T8 with a clean query API for later tasks.

## Files Created

### Core Package (`com.euvcraft.lithmod.core`)
| File | Description |
|------|-------------|
| `Tier.java` | Record with all tier fields: id, index, displayName, lightSource, resolutionNm, overlayNm, throughputWph, layers, defaultYieldPercent, targetSpeedMhz, comfortableCores (nullable), optional (boolean). Validation in constructor for positive numbers, yield bounds, comfortableCores > 0 when present. |
| `TierRegistry.java` | Immutable registry with: lookup by ID/index, ordered iteration (T1-T8), next/previous navigation with T6 skipping, comparison helpers (isBefore, isAfter, compareByIndex), filter/requiredTiers(), validation on build (unique IDs, contiguous indexes 1-8, exactly 8 tiers, ID/index matching). |

### MC Package (`com.euvcraft.lithmod.mc.data`)
| File | Description |
|------|-------------|
| `TierCodec.java` | Mojang Codec for Tier record, enabling JSON serialization/deserialization. Uses snake_case field names matching data files. |

### Data Resources
| File | Description |
|------|-------------|
| `data/lithmod/tiers/t1.json` - `t8.json` | One file per tier, matching REFERENCE DATA exactly (T1: Contact 60% yield, T8: High-NA EUV 80% yield, etc.) |

### Tests (`com.euvcraft.lithmod.core`)
| File | Description |
|------|-------------|
| `TierRegistryTest.java` | 36 tests covering tier validation, registry building, lookup, next/previous (T6 enabled/disabled), comparison helpers, filter, and reference table verification. |

## Verification Results

```bash
# Full build + core import check
$ ./gradlew build checkCoreImports --no-daemon
BUILD SUCCESSFUL

# Core import check
$ ./gradlew checkCoreImports --no-daemon
Core import check passed: no Minecraft/NeoForge imports found in core package.

# Test results
78 tests completed, 0 failed
```

## Reference Data Verification
All 8 tiers match the specification exactly:

| Tier | Name | Light Source | Res (nm) | Overlay (nm) | WPH | Layers | Yield% | Target MHz | Comfy Cores | Optional |
|------|------|--------------|----------|--------------|-----|--------|--------|------------|-------------|----------|
| T1 | Contact | mercury arc lamp | 5000 | 1000 | 75 | 6 | 60 | 1 | - | false |
| T2 | Stepper | g-line then i-line lamp | 500 | 150 | 45 | 12 | 80 | 15 | - | false |
| T3 | KrF DUV | 248 nm excimer laser | 180 | 40 | 80 | 24 | 90 | 600 | - | false |
| T4 | ArF dry | 193 nm excimer laser | 65 | 15 | 110 | 32 | 92 | 2600 | - | false |
| T5 | ArF immersion | 193 nm, water immersion | 28 | 4 | 250 | 42 | 93 | 3400 | - | false |
| T6 | Multi-patterned | 193 nm, several passes | 7 | 3 | 125 | 70 | 85 | 3900 | 4 | **true** |
| T7 | EUV | 13.5 nm tin plasma | 13 | 2 | 140 | 80 | 85 | 5000 | 8 | false |
| T8 | High-NA EUV | 13.5 nm tin plasma, NA 0.55 | 8 | 1 | 170 | 90 | 80 | 5500 | 12 | false |

## Data Pack Override Support
- JSON files placed in `data/lithmod/tiers/` directory
- Standard NeoForge data pack override mechanism: place `data/lithmod/tiers/tX.json` in a data pack to override
- `TierCodec.CODEC` correctly parses overrides
- Reload listener infrastructure created (`TierDataLoader`) but not registered due to NeoForge 26.3 API differences

## Deviations from Spec
- **Reload listener registration**: Not connected to mod event bus due to NeoForge 26.3 API changes (ResourceLocation, PreparableReloadListener signatures). The codec and data files are fully functional. Registration can be added in a follow-up task.

## Next Steps
1. Register data reload listener once NeoForge 26.3 API is confirmed
2. Apply config overrides (yield multiplier, comfortable core overrides, T6 enabled) in task M0-09
3. Implement die types and stat model in subsequent tasks