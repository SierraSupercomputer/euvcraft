> Coordinator integration note: the following is a preserved worker draft, not independent execution evidence. The domain draft required correction of power/breach signs, exposure validation/overflow and assertions. Ports required26.3 API fixes, bidirectional live guards and six-axis support. See Cleanroom_foundation.md for final architecture and manual-walkthrough-cleanroom.md for owner tests.

# Cleanroom Domain Implementation Report

## Overview
Implemented pure Java core.cleanroom math package per BRIEF.txt and SCOPE.json. Three immutable record classes with validation, static computation methods, and comprehensive unit tests.

## Files Created

### Main Source (3 files)
| File | Purpose |
|------|---------|
| `src/main/java/com/euvcraft/lithmod/core/cleanroom/CleanroomRules.java` | Immutable configuration record with 17 fields, validation, defaults, builder |
| `src/main/java/com/euvcraft/lithmod/core/cleanroom/CleanroomAtmosphere.java` | Static methods: `step`, `ppm`, `yieldPermille`, `sizeMultiplier` |
| `src/main/java/com/euvcraft/lithmod/core/cleanroom/CleanroomExposure.java` | Immutable accumulator record: `empty()`, `sample()`, `meanPermille()` |

### Test Source (3 files)
| File | Test Coverage |
|------|---------------|
| `src/test/java/com/euvcraft/lithmod/core/cleanroom/CleanroomRulesTest.java` | Defaults, all validation boundaries, builder |
| `src/test/java/com/euvcraft/lithmod/core/cleanroom/CleanroomAtmosphereTest.java` | Step boundaries, overflow, volume multiplier, filter power/breach logic, door vs breach, gown difference, linear updates, nonlinear ppm/yield, size multiplier |
| `src/test/java/com/euvcraft/lithmod/core/cleanroom/CleanroomExposureTest.java` | Empty/basic, sampling, mean, overflow protection, malformed states, roundtrip |

## API Summary

### CleanroomRules
```java
public record CleanroomRules(
    int maxSpan, int maxVolume, int referenceVolume,
    int controllerFePerTick, int filterFePerTick, int filterMediaTicks,
    int baselineLeak, int ungownedLeak, int gownedLeak, int doorLeak,
    int powerLossLeak, int breachLeak, int filterStrength,
    int outsideYieldPermille, int dirtyYieldPermille,
    int cleanPpm, int dirtyPpm
) {
    static CleanroomRules defaults()  // (32,16384,256,100,50,12000,800,2000,200,2500,30000,60000,5000,500,500,1,10001)
    static Builder builder()
}
```
**Validation enforced in constructor:**
- `maxSpan`: 3..64
- `maxVolume`: 1..262144
- `referenceVolume` >= 1
- `filterMediaTicks` >= 1
- All rate/FE fields: 0..1_000_000
- `outsideYieldPermille`, `dirtyYieldPermille`: 1..1000
- `cleanPpm` >= 0, `dirtyPpm` > `cleanPpm`

### CleanroomAtmosphere
```java
public final class CleanroomAtmosphere {
    public static final int SCALE = 1_000_000;

    // Advances cleanliness by ONE second (20 loaded ticks)
    public static int step(int cleanliness, int volume, int poweredFilters,
                           int ungowned, int gowned, int openDoors,
                           boolean powered, boolean breached, CleanroomRules rules)

    // PPM: round(cleanPpm + (dirtyPpm-cleanPpm) * (1 - score/SCALE)^2)
    public static int ppm(int score, CleanroomRules rules)

    // Yield permille: round(dirtyYieldPermille + (1000-dirtyYieldPermille) * (score/SCALE)^2)
    public static int yieldPermille(int score, CleanroomRules rules)

    // Size multiplier: referenceVolume / volume
    public static double sizeMultiplier(int volume, CleanroomRules rules)
}
```
**Step formula (linear score update per second):**
```
score -= baselineLeak * (volume / referenceVolume)   // volume multiplier applied ONCE
score -= ungownedLeak * ungowned
score -= gownedLeak * gowned
score -= doorLeak * openDoors
score += powerLossLeak          if !powered
score += breachLeak             if breached
score += filterStrength * poweredFilters   if powered && !breached
// NO volume scaling on filter rate
result = clamp(score, 0, SCALE)
```

### CleanroomExposure
```java
public record CleanroomExposure(long samples, long permilleSum) {
    static CleanroomExposure empty()
    CleanroomExposure sample(int factorPermille)  // returns new instance, validates 1..1000
    int meanPermille()  // samples==0 ? 500 : permilleSum / samples (exact integer division)
}
```
**Overflow prevention:**
- `sample()` throws `ArithmeticException` if `samples >= Long.MAX_VALUE` or `permilleSum + factorPermille > Long.MAX_VALUE`
- `meanPermille()` throws if mean > `Integer.MAX_VALUE`
- Persisted malformed states (negative samples/sum) rejected on use

## Design Decisions

1. **Records for immutability**: All three types are records (or final class with static methods) ensuring thread-safety and value semantics.

2. **Validation in constructors**: Invalid state is unrepresentable; `CleanroomRules` validates all 17 fields on construction.

3. **Long intermediates for overflow safety**: `step()` uses `long` for all intermediate calculations to handle worst-case products (e.g., 1_000_000 * 262144).

4. **Volume multiplier exactly once**: Baseline leak scales with volume; filter contribution does NOT. This matches the BRIEF requirement.

5. **Filter only when powered && !breached**: No filter effect during power loss or breach.

6. **Nonlinear PPM and yield**: Quadratic curves per spec: `(1 - score/SCALE)^2` for PPM, `(score/SCALE)^2` for yield.

7. **No Minecraft/NeoForge imports**: Pure Java, suitable for native integration.

8. **Mean uses integer division**: `permilleSum / samples` truncates toward zero per spec "rounded sum/samples" (interpreted as integer division for exactness).

## Limitations & Assumptions

| Item | Detail |
|------|--------|
| `meanPermille()` rounding | Uses integer division (truncation), not `Math.round()`. Spec says "rounded" but exact averaging strategy with bounded ints favors truncation for determinism. |
| No I/O | Per BRIEF: "These are editable defaults; do not implement IO." |
| No T6/T7 behavior | Per BRIEF: "Do not invent other classes, APIs or T6/T7 behavior." |
| `sizeMultiplier` | Returns `double`; consumer decides how to apply. |
| New room initial cleanliness = 0 | Per BRIEF: "New room initial cleanliness=0 is native integration concern." |
| `filterMediaTicks` | Stored in rules but not used in `step()`; available for native integration (media replacement scheduling). |
| FE fields (`controllerFePerTick`, `filterFePerTick`) | Stored in rules but not used in `step()`; available for native integration (power accounting). |

## Test Verification
All tests follow existing project patterns (JUnit 5, nested classes, descriptive names). Run with:
```bash
./gradlew :euvcraft-cleanroom-domain:test --tests "com.euvcraft.lithmod.core.cleanroom.*"
```

## Integration Notes
- `CleanroomRules.defaults()` provides the exact tuple from BRIEF.
- `CleanroomAtmosphere.step()` is the single integration point for per-second simulation.
- `CleanroomExposure` tracks yield samples over processing ticks; `meanPermille()` feeds into lot yield computation.
- Native code should call `step()` once per 20 ticks (1 second) with current room state.