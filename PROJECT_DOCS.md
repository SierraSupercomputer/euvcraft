# LithoMod - EUV Lithography Progression Mod

## Project Overview

LithoMod is a Minecraft mod implementing a generational chip fabrication tech tree where each generation requires chips from the previous generation to build the next level of lithography equipment. A direct addon for Arcforge (v2.5.1+26.3), using its multiblock framework, machine control system, and resource transfer capabilities.

**Current Status**: Proof of Concept - G0 Contact/Proximity Aligner only
**Target**: NeoForge 1.21.1 (NeoForge 26.3.0.69-beta)
**Mod ID**: `lithomod`
**Package**: `com.sierra.lithomod`
**Arcforge API Dependency**: `arcforge-api-1.2.0+26.3` (compileOnly)

---

## Architecture

### Core Systems

#### 1. Quality System (`com.sierra.lithomod.quality`)
Hidden quality system with random rolls at each processing step that compounds into final chip quality.

**Quality Grades** (hidden until inspection):
- **SALVAGE** (0-20%): Only useful for recycling
- **C_GRADE** (20-40%): Low yield, high defect rate
- **B_GRADE** (40-60%): Acceptable for non-critical use
- **A_GRADE** (60-80%): Good quality, standard production
- **PRIME** (80-95%): High quality, premium bins
- **EXEMPLARY** (95-100%): Best-in-class, reference chips

**Process Steps (G0 - 9 steps)**:
1. Wafer Prep & Clean
2. Photoresist Spin Coat
3. Soft Bake
4. Align & Expose (Contact)
5. Develop
6. Hard Bake
7. Wet Etch
8. Resist Strip
9. Final Inspection

Each step performs a Gaussian roll centered on `baseYield * toolQuality` with σ=0.1. Scores compound multiplicatively.

#### 2. FOUP System (`com.sierra.lithomod.item.FOUPItem`)
Front Opening Unified Pod - stores wafers internally like a shulker box.
- Capacity: 25 wafers (configurable)
- Durability bar shows fill level
- Tooltip shows contents (advanced tooltip for details)
- NBT storage for wafer data including quality results

#### 3. Wafer & Chip Items (`com.sierra.lithomod.item.WaferAndChipItems`)
- **Wafer**: Holds multiple chips with quality distribution, tracks processing state
- **ChipItem**: Individual chip with hidden quality state (ChipQuality + QualityState)
- Quality only revealed on inspection (Shift+Ctrl) or if previously inspected

#### 4. G0 Contact Aligner (`com.sierra.lithomod.block.entity.G0ContactAlignerBlockEntity`)
Multiblock controller with processing logic:
- 6 slots: 5 inputs (wafer, resist, developer, etchant, mask) + 1 FOUP output
- 9 processing steps with configurable durations
- Consumes wafer + chemicals per cycle, mask is reusable
- Outputs processed wafer to FOUP
- Tracks wafers processed, tool quality, multiblock status
- **Extends `CubeMultiblockBlockEntity`** for integration with Arcforge multiblock system

#### 5. Multiblock System (`com.sierra.lithomod.multiblock.G0ContactAlignerMultiblock`)
Uses **Arcforge's custom multiblock framework** (NOT MultiblockLib). The mod directly extends and uses Arcforge's multiblock classes:

**Arcforge Multiblock Integration:**
- Controllers extend `CubeMultiblockBlockEntity` (cube-based multiblock system)
- Casing blocks extend `CubeCasingBlock` (hollow cube structure detection)
- Implements `MultiblockController` interface for formation/status
- Uses `MultiblockPorts` for IO port configuration
- `MultiblockEffects` for visual feedback (outline particles on formation)
- `ShellStructure` available as alternative for non-cube shapes

**Key Arcforge Multiblock Classes We Extend/Use:**
- `net.zagdrath.arcforge.block.multiblock.CubeCasingBlock` - Base for casing blocks
- `net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity` - Base for controller BE
- `net.zagdrath.arcforge.multiblock.CubeMultiblockStructure` - Structure validation
- `net.zagdrath.arcforge.multiblock.MultiblockController` - Controller interface
- `net.zagdrath.arcforge.multiblock.MultiblockPart` - Part block interface
- `net.zagdrath.arcforge.multiblock.PortHolder` - Port-capable block interface
- `net.zagdrath.arcforge.multiblock.MultiblockPorts` - Port management utilities

**G0 Structure Approach (Frame + Parts):**
1. Player builds a 5×5×3 hollow frame using a **single G0 Machine Frame** block type
2. Upon frame completion (verified by `CubeCasingBlock`), the frame is a hollow structure
3. Right-click the center controller block to open **Assembly GUI** (separate from processing GUI)
4. Assembly GUI has slots for all required components (optics, light sources, electronics, etc.)
5. When all components inserted and "Assemble" pressed, components are consumed
6. Frame multiblock is **replaced** with the "assembled" lithography device multiblock
7. The assembled controller now opens the **Processing GUI** for wafer fabrication

**Note**: All 5×5×3 frame positions use the same `g0_machine_frame` block - no position-specific blocks are needed for construction. The complexity is in resource gathering (crafting parts) rather than spatial assembly.

**Structure Layout** (relative to controller center, facing North):
```
Layer 1 (y=1):          Layer 0 (y=0):
[LS][ ][LS][ ][LS]      [B ][B ][B ][B ][B ]
[ ][O ][C ][O ][MS]     [B ][S ][O ][ ][WLS]
[LS][ ][LS][ ][LS]      [CB][SC][BP][ ][B ]
```
LS=Light Source, O=Optics, C=Controller, MS=Microscope, B=Base, S=Stage, CB=Chemical Bath, SC=Spin Coater, BP=Bake Plate, WLS=Wafer Load Station

**Arcforge Integration Benefits:**
- Full compatibility with Arcforge conduits, item/fluid/energy transfer
- Uses `SideConfig`/`SideMode` for IO configuration
- Integrates with `MachineControl` API (queryable via capabilities)
- Compatible with Arcforge's wrench tool (`useWrench` from `MultiblockPart`)
- Supports Arcforge's settings cards (`SettingsCopyable`)

**Construction Sequence (Frame + Parts):**
1. Place 24 G0 Machine Frame blocks in a hollow 5×5×3 arrangement
2. Frame blocks validate neighbor connectivity and show "Ready for Assembly"
3. Right-click Frame blocks to install specific multiblock parts:
   - Light Sources (4x) - corners of top layer
   - Optics (2x) - side positions on top layer
   - Controller (1x) - center of top layer
   - Additional components on bottom layer
4. Press "Assemble" in the controller UI to consume parts and form the multiblock
5. Framed structure converts to formed multiblock via Arcforge's `CubeMultiblockStructure.rebuild()`

#### 6. Processing & Machine Control

**Recipe System:**
Recipes defined via JSON in `data/lithomod/recipes/` with a custom `G0LithographyRecipe` type:
- Input items: wafer, photoresist, developer, etchant, mask
- Output: chip type(s) based on recipe (not quality-dependent, but quantity may vary)
- Processing time and energy consumption configurable per recipe
- Quality system applies yield multipliers independently of recipe output

**Recipe JSON Structure:**
```json
{
  "type": "lithomod:g0_lithography",
  "inputs": {
    "wafer": "minecraft:silicon_wafer_blank",
    "resist": "lithomod:photoresist",
    "developer": "lithomod:developer",
    "etchant": "lithomod:etchant"
  },
  "mask_slot_required": true,
  "output": {
    "item": "lithomod:chip_g0_logic",
    "min_count": 8,
    "max_count": 12
  },
  "base_process_time": 200,
  "fe_per_tick": 32
}
```
The G0 Contact Aligner registers a `MachineControlSpec` with Arcforge's machine control system:
- **Energy**: Consumer (draws FE per tick during processing)
- **Items**: 
  - Slots 0-4: INPUT (wafer, resist, developer, etchant, mask)
  - Slot 5: CATALYST (mask - not consumed)
  - Slot 6-7: OUTPUT (processed FOUP)
  - Slot 9: UPGRADE (optional speed upgrades)
- **Progress**: Custom progress tracking (9 steps, each with duration)
- **Status**: RUNNING, IDLE, NO_POWER, NO_INPUT, OUTPUT_BLOCKED, NOT_FORMED, FAULT

**Processing Flow:**
1. Player inserts inputs + empty/partial FOUP in output slot
2. Controller validates multiblock formed (via `CubeMultiblockBlockEntity.isFormed()`)
3. Checks energy, consumes wafer + chemicals, starts 9-step process
4. Each step: progress bar advances, step name updates
5. On completion: `QualitySystem.processWafer()` generates distribution
6. Wafer with quality result inserted into FOUP
7. Loop continues if inputs available
8. Uses Arcforge's `autoEject` system for automation compatibility

---

## Configuration (`Config.java`)

All G0 settings configurable via `lithomod-common.toml`:
- `wafersPerFOUP`: 25 (1-100)
- `chipsPerWafer`: 100 (1-10000)
- `baseYield`: 0.15 (0.01-1.0)
- `processSteps`: 9 (1-20)
- `enableQualityVariance`: true
- `fePerTick`: 32 (FE consumption per tick during processing)
- `baseProcessTime`: 200 (base ticks per step)

---

## Items & Blocks

### Blocks (Multiblock Components)
| Block | Registry Name | Purpose | Extends | Properties |
|-------|---------------|---------|---------|------------|
| G0 Machine Frame | `g0_machine_frame` | Hollow frame construction | `CubeCasingBlock` | `formed` (false) |
| G0 Contact Aligner | `g0_contact_aligner` | Assembled controller | `CubeCasingBlock` | `formed=true`, `stage=idle/running/complete` |
| G0 Wafer Stage | `g0_wafer_stage` | Internal component (not placed) | N/A | Used in Assembly GUI only |
| G0 Optics | `g0_optics` | Internal component (not placed) | N/A | Used in Assembly GUI only |
| G0 Light Source | `g0_light_source` | Internal component (not placed) | N/A | Used in Assembly GUI only |
| G0 Electronics | `g0_electronics` | Internal component (not placed) | N/A | Used in Assembly GUI only |

**Note**: Frame construction uses a single block. Components are items installed via GUI, not placed blocks.

### Items
| Item | Registry Name | Stack Size | Purpose |
|------|---------------|------------|---------|
| Empty FOUP | `foup_empty` | 1 | Wafer transport |
| Silicon Wafer Blank | `silicon_wafer_blank` | 64 | Raw wafer input |
| Photoresist | `photoresist` | 64 | Consumed per wafer |
| Developer | `developer` | 64 | Consumed per wafer |
| Etchant | `etchant` | 64 | Consumed per wafer |
| Mask Blank | `mask_blank` | 16 | Crafting ingredient |
| Patterned Mask (G0) | `patterned_mask_g0` | 1 | Reusable, not consumed |
| **Optical Lens** | `optical_lens` | 64 | Assembly component - Optics (×2) |
| **Mercury Arc Lamp** | `mercury_arc_lamp` | 64 | Assembly component - Light Source (×4) |
| **Precision Electronics** | `precision_electronics` | 64 | Assembly component - Electronics (×4) |
| Logic Chip (G0) | `chip_g0_logic` | 64 | Product |
| DRAM Chip (G0) | `chip_g0_dram` | 64 | Product |
| NAND Flash Chip (G0) | `chip_g0_nand` | 64 | Product |
| Analog Chip (G0) | `chip_g0_analog` | 64 | Product |
| Controller Chip (G0) | `chip_g0_controller` | 64 | Product |

---

## Creative Tab
All items/blocks in "LithoMod" tab (icon: Empty FOUP)

---

## Development Notes

### Arcforge Dependency Setup
Add to `build.gradle`:
```gradle
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    
    // Arcforge API for machine capabilities, multiblock base classes
    compileOnly files("arcforge/arcforge-api-1.2.0+26.3.jar")
    
    // Full Arcforge for runtime (addon requires full mod)
    localRuntime files("arcforge/arcforge-2.5.1+26.3.jar")
}
```

### MultiblockLib Integration
**NOTE**: Project uses **Arcforge's multiblock system**, NOT MultiblockLib.
The original PROJECT_DOCS.md reference to MultiblockLib was based on incorrect assumption - Arcforge has its own comprehensive multiblock framework.

**Key Arcforge Multiblock Pattern:**
1. **Casing Block**: Extend `CubeCasingBlock`, implement `MultiblockPart` and `PortHolder`
   - Override `structure()` and `blockEntityType()`
   - Handle `setPlacedBy`, `onPlace`, `tick`, `affectNeighborsAfterRemoval`
   
2. **Controller Block Entity**: Extend `CubeMultiblockBlockEntity<T>`, implement custom processing
   - Constructor passes energy, lane configs, slot filters
   - Override `serverTick` for processing logic
   - Override `status()` for MachineControlSpec integration
   - Use `items`, `itemInput`, `itemOutput`, `itemAutomation`, `energy` fields
   
3. **Structure Validation**:
   - Uses `CubeMultiblockStructure.findCenter()` and `findController()`
   - Formation checked on neighbor changes + periodic (via `getTicker`)
   - `onStructureChanged()` called on formation/breakage

4. **Multiblock Ports**:
   - `PortHolder` blocks define IO port positions
   - `MultiblockPorts.get()` validates ports against structure bounds
   - `SideMode` maps to Arcforge's standard modes (NONE, INPUT, OUTPUT, ENERGY, etc.)

### Quality System Implementation Details
- `QualityState` tracks compounded score + per-step scores
- Gaussian roll via Box-Muller transform
- `WaferQualityResult` aggregates distribution across all chips on wafer
- NBT serialization for persistence
- Quality affects final chip distribution within each wafer

### FOUP Storage
- Uses item NBT with `Wafers` list tag
- Each wafer stored as compound with UUID, generation, type, quality, progress
- Durability bar repurposed for fill level visualization

### Arcforge Machine Control Integration
Implement `ControlStateHolder` to expose machine state:
```java
public class G0ContactAlignerBlockEntity extends CubeMultiblockBlockEntity implements ControlStateHolder {
    public MachineControlState machineControlState() { return controlState; }
    
    // Register control spec during construction
    static {
        MachineControls.register(G0ContactAlignerBlockEntity.class, builder -> builder
            .primary(be -> be.isFormed() && be.hasValidRecipe())
            .energy(EnergyRole.CONSUMER, be -> be.getEnergy(), be -> be.getUsage())
            .slot(0, SlotRole.INPUT)  // Wafer
            .slot(1, SlotRole.INPUT)  // Photoresist
            .slot(2, SlotRole.INPUT)  // Developer
            .slot(3, SlotRole.INPUT)  // Etchant
            .slot(4, SlotRole.CATALYST) // Mask
            .slot(6, SlotRole.OUTPUT)  // Output FOUP
            .progress(be -> OptionalDouble.of(be.getProgressPercent()), 
                     be -> OptionalInt.of(be.getTicksRemaining()))
            .build());
    }
}
```

### Processing Flow
1. Player inserts inputs + empty/partial FOUP in output slot
2. Controller validates multiblock formed (via `CubeMultiblockBlockEntity.isFormed()`)
3. Consumes wafer + chemicals, starts 9-step process
4. Each step: progress bar advances, step name updates
5. On completion: `QualitySystem.processWafer()` generates distribution
6. Wafer with quality result inserted into FOUP
7. Loop continues if inputs available

---

## Future Generations (Planned)

| Gen | Lithography | Key Requirement | Bootstraps From |
|-----|-------------|-----------------|-----------------|
| G0 | Contact/Proximity (365nm) | None (manual) | - |
| G1 | 1:1 Projection (365nm) | Auto-alignment sensors | G0 Controller chips |
| G2 | i-line Stepper (365nm, 4:1) | Laser interferometry | G1 Logic/Controller |
| G3 | KrF Scanner (248nm) | Dual-stage, OPC compute | G2 Controller/Logic |
| G4 | ArF Immersion (193nm, 1.35NA) | Planar motors, SMO | G3 Logic/Controller/DRAM |
| G5 | Multi-patterning / EUV Pilot | Actinic inspection | G4 All types |
| G6 | EUV 0.33NA HVM | H₂ plasma control | G5 All types |
| G7 | High-NA EUV 0.55NA | Anamorphic optics | G6 All types |

**Bootstrapping Rule**: Building Gen N+1 tools requires consuming Gen N chips as crafting ingredients (embedded controllers).

**Generation Scaling Plan**:
- Each generation adds complexity to the 9-step quality pipeline
- Later generations introduce new process steps (deposition, etching, cleaning)
- FE consumption scales by tier (G0: 32 FE/tick, G1: 64, etc.)
- Structure sizes grow: G0 (5×5×3), G1 (~7×7×4), G2 (~9×9×5+), etc.
- Quality distribution tightens with better tools (lower sigma)

---

## Testing Checklist (PoC - G0 Only)

- [ ] G0 Machine Frame forms correctly in 5×5×3 hollow pattern
- [ ] Right-click controller opens Assembly GUI with component slots
- [ ] Assembly consumes components and replaces multiblock with assembled controller
- [ ] Assembled controller now opens Processing GUI
- [ ] Processing GUI shows 6 item slots + energy bar
- [ ] Input validation works (only correct items in slots)
- [ ] Processing consumes wafer + chemicals, not mask
- [ ] Progress bar advances through 9 steps
- [ ] Quality result generated and stored in output FOUP
- [ ] FOUP tooltip shows wafer count + contents
- [ ] Chip items show quality on Shift+Ctrl
- [ ] Tool quality affects yield distribution
- [ ] Config values affect behavior
- [ ] Creative tab contains all items
- [ ] Arcforge conduits can insert/extract items from controller
- [ ] Energy is consumed during processing
- [ ] MachineControl capability is exposed and readable by Arcforge probes
- [ ] G0 Lithography recipe JSON loads correctly

---

## Build & Run

```bash
# Build
./gradlew build

# Run client
./gradlew runClient

# Run server
./gradlew runServer

# Generate runs
./gradlew genEclipseRuns / ./gradlew genIntellijRuns
```

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| NeoForge | 1.21.1-26.3.0.69-beta | Mod loader |
| Architectury API | 11.0.0 | Cross-platform utilities |
| Arcforge API | 1.2.0+26.3 | Multiblock framework, machine control, resource transfer |
| Arcforge (Full) | 2.5.1+26.3 | Runtime dependency (mod is an addon) |

---

## File Structure

```
src/main/
├── java/com/sierra/lithomod/
│   ├── LithoMod.java                 # Main entry point
│   ├── Config.java                   # Configuration
│   ├── quality/
│   │   └── QualitySystem.java        # Quality rolls, grades, wafer results
│   ├── item/
│   │   ├── ModItems.java             # Item registration
│   │   └── WaferAndChipItems.java    # Wafer, ChipItem, FOUPItem
│   ├── block/
│   │   ├── ModBlocks.java            # Block registration
│   │   ├── G0ContactAlignerBlock.java # Controller block (extends CubeCasingBlock)
│   │   ├── G0MachineFrameBlock.java  # Frame block (extends CubeCasingBlock)
│   │   ├── part/
│   │   │   └── G0CasingBlock.java    # Base class for G0 multiblock parts
│   │   └── entity/
│   │       ├── ModBlockEntities.java # BlockEntity registration
│   │       └── G0ContactAlignerBlockEntity.java # Extends CubeMultiblockBlockEntity
│   ├── multiblock/
│   │   └── G0MultiblockStructure.java # Wraps CubeMultiblockStructure for G0
│   ├── menu/
│   │   ├── ModMenus.java             # Menu registration
│   │   └── G0ContactAlignerMenu.java # Container/menu
│   ├── recipe/
│   │   ├── ModRecipes.java           # Recipe type registration
│   │   ├── G0LithographyRecipe.java  # Lithography recipe
│   │   └── G0LithographyRecipeSerializer.java # JSON serializer
│   └── client/
│       ├── LithoModClient.java       # Client event handlers
│       └── screen/
│           └── G0ContactAlignerScreen.java # GUI screen
└── resources/
    ├── assets/lithomod/
    │   ├── lang/en_us.json           # Translations
    │   ├── blockstates/*.json        # Block state mappings
    │   ├── models/block/*.json       # Block models
    │   ├── models/item/*.json       # Item models
    │   ├── textures/gui/             # GUI textures
    │   └── textures/block/           # Block textures
    ├── data/lithomod/
    │   ├── recipes/*.json            # Crafting recipes
    │   ├── tags/
    │   │   ├── items/*.json          # Item tags
    │   │   └── blocks/*.json         # Block tags
    │   └── neoforge/
    │       └── tags/                 # Forge tags for compatibility
    └── META-INF/
        ├── accesstransformer.cfg     # If needed for Arcforge internals
        └── neoforge.mods.toml        # Mod metadata with arcforge dependency
```

---

## Known Issues / TODOs

1. **Textures**: All block/item models use placeholder textures - need actual PNG files
2. **GUI Texture**: `g0_contact_aligner.png` (176×166) needed in `assets/lithomod/textures/gui/`
3. **Complex Models**: Frame and component blocks need proper texture assignments
4. **JEI Integration**: Recipe category for lithography processing
5. **Patchouli**: Guide book for tech tree progression
6. **Sound Events**: Machine processing sounds
7. **Particles**: Processing visual effects
8. **Arcforge Probe Compatibility**: Ensure MachineControl is properly exposed for WTHIT-like mods

---

## License

MIT License - See LICENSE file
