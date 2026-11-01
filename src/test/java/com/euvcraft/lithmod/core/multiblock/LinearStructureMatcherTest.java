package com.euvcraft.lithmod.core.multiblock;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearStructureMatcherTest {
    private static final MultiblockPos ANCHOR = new MultiblockPos(12, 30, -17);

    private Map<MultiblockPos, BlockKind> fixture(StructureShape s, MultiblockDirection f, int column, boolean waterRequired) {
        var cells = new LinkedHashMap<MultiblockPos, BlockKind>();
        for (int u = 0; u < s.width(); u++)
            for (int v = 0; v < s.height(); v++)
                for (int d = 0; d < s.length(); d++) {
                    var p = ANCHOR.add(f.right().multiply(u - column)).add(new MultiblockPos(0, v, 0)).add(f.back().multiply(d));
                    cells.put(p, BlockKind.CASING);
                }
        cells.put(ANCHOR, BlockKind.CONTROLLER);
        cells.put(ANCHOR.add(new MultiblockPos(0, 1, 0)), BlockKind.OPERATING_PANEL);
        cells.put(ANCHOR.add(f.back()), BlockKind.POWER_PORT);
        if (waterRequired) {
            cells.put(ANCHOR.add(f.back().multiply(2)), BlockKind.WATER_INLET);
        }
        return cells;
    }

    @Test void everyFacingAndFrontColumnAndEveryMissingCell() {
        List<StructureShape> shapes = List.of(
            LinearStructureMatcher.ETCH_3x3x5,
            LinearStructureMatcher.COATER_3x2x5,
            LinearStructureMatcher.WET_CLEANER_2x2x3
        );
        for (var s : shapes)
            for (boolean water : List.of(false, true))
                for (var f : MultiblockDirection.values())
                    for (int column = 0; column < s.width(); column++) {
                        var cells = fixture(s, f, column, water);
                        var result = LinearStructureMatcher.match(ANCHOR, f, s, water, p -> cells.getOrDefault(p, BlockKind.OTHER));
                        assertTrue(result.success(), s + " " + f + " water=" + water + " column=" + column);
                        assertEquals(new ArrayList<>(cells.keySet()), result.memberPositions());
                        assertEquals(s.volume(), new HashSet<>(result.memberPositions()).size());
                        for (var p : List.copyOf(cells.keySet())) {
                            var previous = cells.put(p, BlockKind.OTHER);
                            assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, water, q -> cells.getOrDefault(q, BlockKind.OTHER)).success(), "missing " + p);
                            cells.put(p, previous);
                        }
                    }
    }

    @Test void waterRequiredExactlyOne() {
        var s = LinearStructureMatcher.ETCH_3x3x5;
        var f = MultiblockDirection.EAST;
        // waterRequired=true: zero inlets fails
        var cells = fixture(s, f, 1, true);
        cells.put(ANCHOR.add(f.back().multiply(2)), BlockKind.CASING);
        assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, true, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
        // waterRequired=true: two inlets fails
        cells.clear(); cells.putAll(fixture(s, f, 1, true));
        cells.put(ANCHOR.add(f.back().multiply(3)), BlockKind.WATER_INLET);
        assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, true, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
        // waterRequired=false: one inlet fails
        cells.clear(); cells.putAll(fixture(s, f, 1, false));
        cells.put(ANCHOR.add(f.back().multiply(2)), BlockKind.WATER_INLET);
        assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, false, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
    }

    @Test void onlyOnePanelAndControllerAndOneOrTwoPortsAreAdmitted() {
        var s = LinearStructureMatcher.ETCH_3x3x5;
        var f = MultiblockDirection.EAST;
        for (var wrong : List.of(BlockKind.CONTROLLER, BlockKind.OPERATING_PANEL, BlockKind.FLUID_OUTLET, BlockKind.DIAGNOSTIC_PANEL, BlockKind.OTHER)) {
            var cells = fixture(s, f, 1, false);
            cells.put(ANCHOR.add(f.back().multiply(4)), wrong);
            assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, false, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
        }
        var cells = fixture(s, f, 1, false);
        var second = ANCHOR.add(f.back().multiply(2));
        cells.put(second, BlockKind.POWER_PORT);
        assertTrue(LinearStructureMatcher.match(ANCHOR, f, s, false, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
        cells.put(ANCHOR.add(f.back().multiply(3)), BlockKind.POWER_PORT);
        assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, false, p -> cells.getOrDefault(p, BlockKind.OTHER)).success());
        final var noPorts = fixture(s, f, 1, false);
        noPorts.put(ANCHOR.add(f.back()), BlockKind.CASING);
        assertFalse(LinearStructureMatcher.match(ANCHOR, f, s, false, p -> noPorts.getOrDefault(p, BlockKind.OTHER)).success());
    }

    @Test void invalidShapeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            LinearStructureMatcher.match(ANCHOR, MultiblockDirection.NORTH, new StructureShape(4, 4, 4), false, p -> BlockKind.OTHER));
    }
}
