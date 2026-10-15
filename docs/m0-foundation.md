# EUVCRAFT M0 Foundation Documentation

## Package Layout

```
com.euvcraft.lithmod
├── core                          # Pure logic, no Minecraft/NeoForge imports
│   ├── Wafer.java
│   ├── WaferPacking.java
│   ├── FoupContents.java
│   ├── FoupPublicView.java
│   ├── RunSpec.java
│   ├── Tier.java
│   ├── TierRegistry.java
│   ├── SupportLines.java
│   ├── GenerationMatrix.java
│   ├── GenerationEntry.java
│   ├── DieRules.java
│   ├── DieRule.java
│   ├── DieTypes.java
│   ├── StatRules.java
│   ├── DefaultStatRules.java
│   ├── StatRulesParams.java
│   ├── LineComponents.java
│   ├── LineStats.java
│   ├── UnmetLine.java
│   ├── YieldMath.java
│   ├── YieldCalibration.java
│   ├── RegularizedGamma.java
│   ├── YieldRollEngine.java
│   ├── RollSource.java
│   ├── Rng.java
│   ├── SplitMix64.java
│   ├── ContextualRng.java
│   ├── FabSettings.java
│   ├── FabSettingsProvider.java
│   └── ImportChecker.java
└── mc                            # Minecraft-facing code
    ├── EUVCRAFTMod.java
    ├── FabConfig.java
    ├── FabRandomService.java
    ├── FabRandomData.java
    ├── FabYieldService.java
    ├── FoupItems.java
    ├── FoupContentsCodec.java
    ├── FoupDebugCommand.java
    ├── FoupTooltip.java
    ├── FoupTooltipAppenders.java
    ├── RunSpecCodec.java
    ├── registry
    │   ├── ModItems.java
    │   ├── ModDataComponents.java
    │   └── ModCreativeTabs.java
    ├── data
    │   ├── JsonResourceDataLoader.java
    │   ├── TierDataLoader.java
    │   ├── TierCodec.java
    │   ├── SupportLineDataLoader.java
    │   ├── DieRulesDataLoader.java
    │   └── DieRuleCodec.java
    └── gametest
        ├── EUVCRAFTGameTests.java
        ├── FoupGameTests.java
        ├── YieldGameTests.java
        └── FabRandomGameTests.java
```

## Public API for Later Tasks

### TierRegistry (`com.euvcraft.lithmod.core`)
```java
// Registry access
TierRegistry getRegistry()
Optional<Tier> byIndex(int index)
Optional<Tier> byId(String id)
List<Tier> required()              // excludes optional T6
List<Tier> all()
Tier next(Tier current)
Tier previous(Tier current)
int compareByIndex(Tier a, Tier b)
```

### GenerationMatrix (`com.euvcraft.lithmod.core`)
```java
GenerationEntry get(String line, int tierIndex)
boolean isSatisfied(String line, int tierIndex, int availableGen)
List<UnmetLine> unmet(Tier tier, Map<String, Integer> available)
```

### DieRules (`com.euvcraft.lithmod.core`)
```java
boolean canMake(Tier tier, DieType type, int coreCount)
int effectiveCoreCap(Tier tier, DieType type, int requestedCores)
```

### StatRules (`com.euvcraft.lithmod.core`)
```java
LineStats statsFor(Tier tier, GenerationMatrix matrix, Map<String, Integer> availableGens)
int wafersPerHour(Tier tier, GenerationMatrix matrix, Map<String, Integer> availableGens)
```

### FabSettings (`com.euvcraft.lithmod.core`)
```java
int foupCapacity()                  // default 25
int wafersPerBoule()                // default 25
int rollsPerLayer()                 // default 1
double yieldMultiplier()            // default 1.0, range 0.1..2.0
Map<String, Integer> tierYieldOverrides()  // per-tier yield percent overrides
List<Integer> coreCountOptions()    // default [1,2,4,8,12,24]
Map<String, Integer> comfortableCoreOverrides()  // per-tier comfortable cores
double coreCountPenaltySteepness()  // default 2.0, range 0..10
boolean debugCommands()             // default false
boolean t6Enabled()                 // default true
```

### FabRandomService (`com.euvcraft.lithmod.mc`)
```java
static SavedDataStorage storage(Level level)
static FabRandomData data(Level level)
static ContextualRng rngFor(Level level, UUID lotId, int waferIndex, int stepIndex)
static ServerLevel requireServerSide(Level level)
static void requireServerSide(boolean clientSide, String levelDescription)
```

### FoupContents (`com.euvcraft.lithmod.core`)
```java
// Immutable record-like class
static FoupContents empty(UUID lotId, int capacity)
FoupContents withWafer(Wafer wafer)
FoupContents withoutWaferAt(int index)
FoupContents withRun(RunSpec spec)
RunSpec run()
UUID lotId()
int capacity()
int size()
int lotLayer()
List<Wafer> wafers()
FoupPublicView publicView()
boolean isEmpty()
boolean isClientView()
```

### RunSpec (`com.euvcraft.lithmod.core`)
```java
RunSpec(int tierIndex, int cores, double effectiveYield, int rollCount)
static RunSpec of(Tier tier, Integer cores, FabSettings settings)
boolean matches(Tier tier, Integer cores)
Integer coresOrNull()
static int NO_CORES = 0
static int coresOrZero(Integer cores)
// CODEC for persistence: in mc package as RunSpecCodec.CODEC
```

### FoupItems (`com.euvcraft.lithmod.mc`)
```java
static int configuredCapacity()
static ItemStack emptyFoup(Item item)
static ItemStack with(Item item, FoupContents contents)
@Nullable FoupContents contentsOf(ItemStack stack)          // diagnostics only
FoupContents trustedContentsOf(ItemStack stack)              // ONLY way to read a lot for gameplay
boolean isFoup(ItemStack stack)
```

### FabYieldService (`com.euvcraft.lithmod.mc`)
```java
// With core count (compute chips T6+)
static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, Integer cores, int layerNumber, double defectFactor)

// Without core count (other die types)
static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, int layerNumber, double defectFactor)

// Judging - rejects client-view lots, lots without run spec, tier/cores mismatch
static boolean[] evaluateLot(FoupContents lot, Tier tier, Integer cores)
static boolean[] evaluateLot(ServerLevel level, FoupContents lot, Tier tier, Integer cores)
static int countPassing(FoupContents lot, Tier tier, Integer cores)
static int countPassing(ServerLevel level, FoupContents lot, Tier tier, Integer cores)
```

### YieldMath (`com.euvcraft.lithmod.core`)
```java
static double passThreshold(double effectiveYield)           // 1 - yield
static boolean passes(double cumulativeRoll, double effectiveYield)
static double clampYield(double yield)                        // into [0.01, 0.99]
static double resolveEffectiveYield(Tier tier, FabSettings settings, Integer cores)
static double effectiveYield(double baseYield, Integer cores, Integer comfortableCores, double steepness)
static double penaltyExponent(int cores, int comfortable, double k)  // package-private
```

### JsonResourceDataLoader (`com.euvcraft.lithmod.mc.data`)
```java
// Generic reload listener base class
protected void onDataReload(Map<Identifier, T> prepared, ResourceManager manager, ProfilerFiller profiler)
```

### RunSpecCodec (`com.euvcraft.lithmod.mc`)
```java
// Mojang codec for RunSpec persistence (keeps core package pure)
static final Codec<RunSpec> CODEC
```

### FoupContentsCodec (`com.euvcraft.lithmod.mc`)
```java
// Persistent form: everything, rolls, flags and optional run spec included
static final Codec<FoupContents> CODEC

// Network form: public view only (capacity, count, layer)
static final StreamCodec<ByteBuf, FoupContents> STREAM_CODEC

// Client view marker
static final UUID CLIENT_VIEW_LOT_ID
static final float CLIENT_VIEW_ROLL
static boolean isClientView(FoupContents contents)
static FoupContents fromPublicView(int capacity, int waferCount, int lotLayer)
```

### TierDataLoader (`com.euvcraft.lithmod.mc.data`)
```java
static TierRegistry getRegistry()
```

### SupportLineDataLoader (`com.euvcraft.lithmod.mc.data`)
```java
static GenerationMatrix getMatrix()
```

### DieRulesDataLoader (`com.euvcraft.lithmod.mc.data`)
```java
static DieRules getRules()
```

### TierCodec (`com.euvcraft.lithmod.mc.data`)
```java
static final Codec<Tier> CODEC
```

### DieRuleCodec (`com.euvcraft.lithmod.mc.data`)
```java
static final Codec<DieRule> CODEC
```

## Data File Locations and Formats

### Tier Data: `data/lithmod/tiers/t1.json` ... `t8.json`
```json
{
  "id": "T3",
  "index": 3,
  "name": "KrF DUV",
  "lightSource": "248 nm excimer laser",
  "resolutionNm": 180,
  "overlayNm": 40,
  "wafersPerHour": 80,
  "layers": 24,
  "defaultYieldPercent": 90.0,
  "targetSpeedMhz": 600,
  "comfortableCores": null
}
```

### Support Line Matrix: `data/lithmod/support_lines/matrix.json`
```json
{
  "substrate":       [1, 1, 2, 2, 2, 3, 3, 3],
  "mask_shop":       [1, 1, 2, 2, 2, 2, 3, 3],
  "deposition_etch": [1, 1, 1, 2, 2, 2, 3, 3],
  "utilities":       [1, 1, 1, 1, 2, 2, 3, 3],
  "test_packaging":  [1, 1, 1, 2, 2, 2, 2, 3]
}
```

### Die Rules: `data/lithmod/die_rules/rules.json`
```json
{
  "logic":     { "firstTier": 1, "lastTier": 8, "coreCounts": [] },
  "memory":    { "firstTier": 2, "lastTier": 8, "coreCounts": [] },
  "compute":   { "firstTier": 3, "lastTier": 8, "coreCounts": [1,2,4,8,12,24] },
  "nand":      { "firstTier": 3, "lastTier": 8, "coreCounts": [] }
}
```

## Config Keys

All under `lithmod` namespace, type `LOCAL`:

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `general.foupCapacity` | int | 25 | 1..64 | Wafers per FOUP |
| `general.wafersPerBoule` | int | 25 | 1..64 | For future boule mechanic |
| `general.rollsPerLayer` | int | 1 | 1..8 | Rolls drawn per wafer per layer |
| `general.yieldMultiplier` | double | 1.0 | 0.1..2.0 | Global yield scaling |
| `general.tierYieldOverrides` | list<string> | [] | T1..T8, 1..99 | Per-tier yield overrides (format: "T3=85") |
| `compute.coreCountOptions` | list<int> | [1,2,4,8,12,24] | 1..256 | Available core counts for compute |
| `compute.comfortableCoreOverrides` | list<string> | [] | T6..T8, 1..256 | Per-tier comfortable cores (format: "T6=8") |
| `compute.coreCountPenaltySteepness` | double | 2.0 | 0..10 | Core count penalty exponent multiplier |
| `features.tier6Enabled` | boolean | true | - | When false, skips T6; tiers go T5 -> T7 |
| `features.debugCommands` | boolean | false | - | Enable /debug foup commands (effective gate: level 3) |
| `power.fePowerMultiplier` | double | 1.0 | 0..100 | Global multiplier on every fab machine's FE cost |

## Creative-Mode Findings (Accepted Limitation)

The mod is not intended for creative worlds. The following behaviors are documented, not fixed:

1. **Creative inventory replacement**: A creative-mode player can replace a FOUP stack via `ServerboundSetCreativeModeSlotPacket`. The server rebuilds wafers with `Wafer.NO_ROLLS` (1.0f), which passes every yield check. `FoupItems.trustedContentsOf()` detects and rejects client-view lots with wafers, returning a fresh empty lot and logging a warning.

2. **Creative middle-click duplication**: `AbstractContainerMenu` handles `ContainerInput.CLONE` when `player.hasInfiniteMaterials()` by calling `Slot.safeClone()`, which for a FOUP (max stack size 1) creates a complete duplicate with the same hidden rolls and lotId. This is a genuine server lot, not a client view, so `trustedContentsOf` cannot detect it.

3. **Saved-data alternative (not implemented)**: Store rolls in `SavedData` keyed by `lotId`, consume the record at packaging. A duplicated lot would find no record and be treated as new. Requires garbage collection for scrapped lots and world deletion.

## Known Limitations

1. **Cross-session roll reproducibility**: Depends on `StrictMath.pow` bit-identity across JVM builds. A boundary-case wafer could flip on a JVM update.

2. **Defect factor mid-run**: Per-layer defect factor is allowed; rolls calibrated at different weights coexist in one lot. Confirm before machines exist.

3. **Config change during lot processing**: `rollsPerLayer` change affects calibration; whether to refuse config changes while lots are in flight is undecided.

4. **Lower clamp at 1%**: Unreachable from shipped config (smallest multiplier 0.1 × smallest tier 60% = 6%). Only matters for data packs with very low `default_yield_percent`.

5. **Tier/cores mismatch refusal**: A lot processed at one tier and judged at another is now refused. Test station should confirm this is the final rule.

6. **RunSpec CODEC in mc package**: `RunSpec` in core uses `RunSpecCodec.CODEC` from mc package to keep core free of Mojang imports.

## Deviations from Task Specs

| Spec | What Was Done | Reason |
|------|---------------|--------|
| `applyLayerStep` signature | 5-arg overload delegates to 6-arg with `cores=null` | 5-arg signature can't carry core count; rolls must calibrate against effective yield |
| Cache key | `(rollCount, yield)` instead of `(tier, rollCount, yield)` | Tier only contributes through layer count and resolved yield; keying on pair covers settings changes between layers |
| `evaluateLot` behavior | Also refuses lot with no run spec or mismatched tier/cores | A lot without spec has no rolls multiplied in; mismatch means calibration was wrong |
| Empty lot | Refused at layer step | No wafers to roll; accepting would consume cycle without advancing layer |
| Network rolls | Kept server-only; `applyLayerStep` takes `ServerLevel` | `evaluateLot` rejects client-view lots; only server has real rolls |
| Gamma quantile | 15-term Lanczos `logGamma` + bisection | Pinned by `P(1,x)=1-e^-x`, `P(2,x)=1-e^-x(1+x)`, quantile inversion at every tier |
| Numeric constants | Core uses tier data and settings | Algorithm constants (bisection tolerance, iteration caps) are numerical hygiene, not gameplay tuning |
| Dependencies | None added | ~200 lines of gamma code vs. library dependency |

## 26.3 API Notes

| Feature | Discovery | Source Report |
|---------|-----------|---------------|
| Reload listener signature | `SimpleJsonResourceReloadListener.apply(Map<Identifier,T>, ResourceManager, ProfilerFiller)` | M0-03b |
| Config type constants | `ModConfig.Type.LOCAL` (no `SERVER` type in 26.3) | M0-03c |
| Saved data API | `SavedData` extends `SavedData`; attached via `overworld().getDataStorage()`; type id determines file name | M0-07 |
| Game test registration | `RegisterGameTestsEvent`, `TestEnvironmentDefinition`, `FunctionGameTestInstance`, `DeferredRegister<TEST_FUNCTION>` | M0-03c |
| Codec forms | `RecordCodecBuilder.create(instance -> instance.group(...).apply(instance, ...))` works on classpath | M0-07b |
| Item models | `minecraft:item/generated` with `layer0` texture; may need explicit model file in 1.21.4+ | M0-08 |
| Data component | `ItemStack.CODEC` for NBT; `StreamCodec<ByteBuf, T>` for network | M0-08 |
| Container NBT | `ContainerHelper.saveAllItems/loadAllItems` with `TagValueOutput/Input` | M0-08 |
| Creative packet | `ServerboundSetCreativeModeSlotPacket` uses `ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC` | M0-08 |
| Slot cloning | `Slot.safeClone()` → `item.copyWithCount(item.getMaxStackSize())` | M0-08 |
| StrictMath | Used for `pow`, `log`, `abs`, `exp` in calibration for cross-JVM reproducibility | M0-09b |

## Open Design Decisions

1. **Cross-session reproducibility**: `StrictMath` used; boundary wafer could flip on JVM update. **Status: Open** (Owner: M1)
2. **Defect factor mid-run**: Per-layer allowed; coexisting different weights in one lot. **Status: Open** (Owner: M1)
3. **Config change during in-flight lots**: `rollsPerLayer` change makes existing lots more expensive to finish. Whether to refuse is undecided. **Status: Open** (Owner: M1)
4. **Lower yield clamp at 1%**: Unreachable from shipped config. **Status: Accepted limitation** (Owner: M1)
5. **Tier/cores mismatch refusal**: Now enforced; test station to confirm final rule. **Status: Open** (Owner: M1)
6. **Creative mode protection**: `trustedContentsOf` blocks client-view injection but not server-side middle-click duplication. **Status: Documented limitation** (Owner: M1)
7. **Saved-data keyed by lotId**: Proposed alternative with duplicate rule; not implemented. **Status: Open** (Owner: M1)
8. **`rollsPerLayer` derived from RunSpec**: At later layers, read from stored spec rather than live settings. **Status: Decided** (Owner: M0-09b)
9. **RunSpec CODEC in mc package**: To keep core free of Mojang imports. **Status: Decided** (Owner: M0-09b)
10. **Arcforge test flake**: `arcforge:creosote_moves` fails intermittently; pre-existing, not caused by this mod. **Status: Documented** (Owner: N/A)
11. **Speed-to-throughput mapping**: Tier `wafersPerHour` and `targetSpeedMhz` are placeholders; no formula links clock speed to throughput. **Status: Open** (Owner: M1)
12. **Default numbers acceptance**: All tier parameters (yield, layers, throughput, speed) are placeholder values from the spec; not validated against real-world data. **Status: Open** (Owner: M1)
13. **Die type and core count travel from mask to lot**: Where/how the mask shop chooses die type and core count and writes it into the lot is not designed. **Status: Open** (Owner: M1)
14. **Arcforge reuse**: Whether to depend on Arcforge machines/power or build independent implementations is undecided. **Status: Open** (Owner: M1)
15. **FOUP transit timing**: Whether lot layer advances on time (tick-based) or only on machine interaction is undecided. **Status: Open** (Owner: M1)
16. **Scrap returns**: Whether failed wafers return to a scrap FOUP or are deleted is undecided. **Status: Open** (Owner: M1)
17. **`withoutWaferAt` behavior**: Whether it should throw on invalid index or return unchanged lot is undecided (M0-08 review Q2). **Status: Open** (Owner: M1)
18. **Config type LOCAL vs SYNCED**: FabConfig uses LOCAL (per-side files, no sync); SYNCED would sync effective values to clients. Switch is a one-line change but untested. **Status: Open** (Owner: M1)
19. **Co-mod jar plan**: Encoded Logistics and Arcforge jars are committed in `libs/`; no published coordinates found. Whether to publish or keep local is undecided. **Status: Open** (Owner: M1)
20. **Per-layer defect factor**: Current engine allows defectFactor to vary per layer; rolls calibrated at different weights coexist. Confirm before machines exist. **Status: Open** (Owner: M1)