# T3 Support Domain Implementation Report

## Summary

Implemented the purecore support-machine domain for owner-approved T3 as scoped in `build/t3/domain/SCOPE.json`. All four files created and unit tests authored. Execution pending coordinator verification.

## Files Created

| File | Purpose |
|------|---------|
| `src/main/java/com/euvcraft/lithmod/core/WaterTank.java` | Server-agnostic water amount (mB) tank |
| `src/main/java/com/euvcraft/lithmod/core/multiblock/SupportStructureMatcher.java` | Support structure multiblock matcher |
| `src/test/java/com/euvcraft/lithmod/core/WaterTankTest.java` | WaterTank unit tests |
| `src/test/java/com/euvcraft/lithmod/core/multiblock/SupportStructureMatcherTest.java` | SupportStructureMatcher unit tests |

---

## WaterTank

### API

```java
public final class WaterTank {
    public static final int MAX_CAPACITY = 1_000_000;

    public WaterTank(int capacity);                              // empty tank
    public WaterTank(int capacity, int amount);                  // restore from snapshot
    public int capacity();
    public int amount();
    public int fill(int fillAmount, boolean simulate);           // partial fill
    public boolean fillExact(int fillAmount, boolean simulate);  // all-or-nothing fill
    public boolean drainExact(int drainAmount, boolean simulate); // all-or-nothing drain
    public void resize(int newCapacity);                         // shrink/grow, preserves water
    public Snapshot snapshot();                                  // immutable state round-trip

    public record Snapshot(int capacity, int amount) {
        public WaterTank restore();
    }
}
```

### Constraints Enforced

| Constraint | Behavior |
|------------|----------|
| Capacity bounds | `1 .. 1_000_000` mB, constructor/resize throw otherwise |
| Amount bounds | `0 .. 1_000_000` mB, restore throws if negative or > `MAX_CAPACITY` |
| Negative amounts | Rejected on all operations |
| Zero amounts | Valid no-op on all operations |
| Simulate mode | Never mutates state |
| Resize shrink | Preserves stored water even if > new capacity; further fill rejected until room; drain allowed |
| Restore overfull | Amount > capacity but ≤ `MAX_CAPACITY` accepted (data pack shrink scenario) |

### Test Coverage (`WaterTankTest.java`)

- **Constructors & getters**: valid/invalid capacity, valid/invalid restore amount, overfull restore
- **Partial fill**: accepts remaining space, zero no-op, negative throws, simulate no-mutate
- **Exact fill (all-or-nothing)**: fits/overflow, zero no-op, negative throws, simulate no-mutate
- **Exact drain (all-or-nothing)**: enough/not enough, zero no-op, negative throws, simulate no-mutate
- **Resize**: grow, shrink below amount (overfull), shrink to equal, invalid bounds
- **Snapshot round-trip**: normal and overfull states
- **Conservation**: fill→drain, fillExact→drainExact return to original
- **Boundaries**: fill to max, drain to zero, restore at max
- **Overflow safety**: operations near `MAX_CAPACITY` no int overflow
- **Overfull scenarios**: fill rejected until room, drain works, fillExact rejected

---

## SupportStructureMatcher

### Geometry

- **Shape**: 2 wide × 2 tall × 3 deep (12 cells), same as `StructureShape.PROCESS_CELL`
- **Anchor**: Controller at front face (d=0), lower row (v=0), either column (u=0 or u=1)
- **No operating panel** — `OPERATING_PANEL` anywhere causes failure
- **Power ports**: 1 or 2 required, anywhere in the 12 cells
- **All other cells**: `CASING`
- **Extra controller** anywhere → failure
- **Foreign block** (`OTHER`) → failure

### Match Algorithm

```java
public static MatchResult match(MultiblockPos controllerPos, MultiblockDirection facing,
        Function<MultiblockPos, BlockKind> blockKindAt)
```

1. Tries two anchor offsets deterministically: `(u0=0, v0=0)` then `(u0=1, v0=0)`
2. First full match wins (deterministic)
3. Returns `MatchResult` with 12 member positions in canonical order `u*6 + v*3 + d`
   (u outer, v middle, d inner — matches `StructureShape` volume iteration)

### Cell Position Formula

```
worldPos = controllerPos
    + facing.right() * (u - u0)
    + UP * (v - v0)
    + facing.back() * d
```

- `back = -facing` (structure extends behind controller)
- `right` = facing rotated 90° clockwise from above
- `up` = +Y
- **Anchor at d=0** (front face) — no mirror d+1 bug

### Test Coverage (`SupportStructureMatcherTest.java`)

| Category | Tests |
|----------|-------|
| **Success** | All 4 facings × both lower columns (u0=0,1) with 2-port and 1-port fixtures |
| **Member order** | Verifies canonical `u*6+v*3+d` iteration order |
| **Foreign block** | Any `OTHER` cell rejects |
| **Extra controller** | Any second `CONTROLLER` rejects |
| **Absent ports** | 0 ports rejects |
| **Excess ports** | 3 ports rejects |
| **Operating panel** | `OPERATING_PANEL` at any of 12 positions rejects |
| **Upper controller** | Anchor at v0=1 (upper row) rejects |
| **Wrong anchor** | Controller at tested anchor but actual controller elsewhere rejects |
| **Mirrored layout** | Complete valid box built in front of controller (along `facing` not `back`) rejects — proves anchor at d=0 |
| **All 12 single removals** | Each non-anchor, non-port cell removed → rejects |
| **Determinism** | Repeated calls return identical results |
| **Geometry invariants** | Controller in box, no duplicate cells |

---

## Compliance with Scope

| Requirement | Status |
|-------------|--------|
| No Minecraft/NeoForge imports in core | ✅ Verified — only `java.util` and core types |
| WaterTank: positive capacity 1..1_000_000 | ✅ |
| WaterTank: amount 0..1_000_000, int-safe | ✅ |
| WaterTank: constructors, getters, partial fill, exact fill/drain, resize, snapshot | ✅ |
| WaterTank: negative rejects, zero no-op, simulate no-mutate | ✅ |
| WaterTank: resize shrink preserves overfull, rejects fill, allows drain | ✅ |
| WaterTank: restore overfull within hard bound accepted | ✅ |
| WaterTank: tests conservation/partial/exact/simulate/overfull/restore/overflow | ✅ |
| SupportStructureMatcher: 2×2×3, anchor lower front either column | ✅ |
| SupportStructureMatcher: 1 or 2 power ports, remaining casings | ✅ |
| SupportStructureMatcher: NO OperatingPanel | ✅ |
| SupportStructureMatcher: reuses core vector/geometry types | ✅ |
| SupportStructureMatcher: ordered members u*6+v*3+d | ✅ |
| SupportStructureMatcher: checks anchor kind, rejects extra controllers/disallowed | ✅ |
| SupportStructureMatcher: deterministic offset search/fail reason | ✅ |
| SupportStructureMatcher: tests 4 facings × both columns, 12 removals, ports, extraneous controller, panel, upper controller, wrong anchor, mirrored | ✅ |
| No mirror d+1 bug; true anchor at front d=0 | ✅ |
| No unrelated abstractions added | ✅ |

---

## Limitations / Known Gaps

1. **Execution not verified** — Tests authored but not run; coordinator must execute test suite.
2. **WaterTank.resize** — Signature returns `void`; amount preservation on shrink is implicit. If a capacity change notification is needed later, API would need extension.
3. **SupportStructureMatcher** — Only matches the fixed 2×2×3 shape. If other support structure sizes are needed, a `StructureShape` parameter could be added (not in scope).
4. **BlockKind.OPERATING_PANEL** — Reused from existing enum; the matcher explicitly rejects it. No new enum values added.
5. **MatchResult member positions** — Returns world positions. The canonical order is verified but callers should not rely on specific ordering beyond the documented `u*6+v*3+d`.

---

## Next Steps (Coordinator)

1. Run the test suite to verify all tests pass.
2. Confirm no compilation errors.
3. Integrate into the main branch per Git workflow.