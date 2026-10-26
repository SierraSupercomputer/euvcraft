package com.euvcraft.lithmod.core.multiblock;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneStructureMatcherTest {
    private static final MultiblockPos ANCHOR = new MultiblockPos(41, -12, 300);
    private static final StructureShape SHAPE = ZoneStructureMatcher.ZONE_SHAPE;

    /** Full valid 3x3x5 zone: controller at any lower-front column, panel above it,
     *  one water inlet behind the controller and one power port directly behind it. */
    private Map<MultiblockPos, BlockKind> fixture(MultiblockDirection f, int column) {
        var cells = new LinkedHashMap<MultiblockPos, BlockKind>();
        for (int u=0; u<SHAPE.width(); u++) for (int v=0; v<SHAPE.height(); v++) for (int d=0; d<SHAPE.length(); d++) {
            var p = ANCHOR.add(f.right().multiply(u-column)).add(new MultiblockPos(0,v,0)).add(f.back().multiply(d));
            cells.put(p, BlockKind.CASING);
        }
        cells.put(ANCHOR, BlockKind.CONTROLLER);
        cells.put(ANCHOR.add(new MultiblockPos(0,1,0)), BlockKind.OPERATING_PANEL);
        cells.put(ANCHOR.add(f.back().multiply(2)), BlockKind.WATER_INLET);
        cells.put(ANCHOR.add(f.back()), BlockKind.POWER_PORT);
        return cells;
    }

    private static MatchResult match(Map<MultiblockPos, BlockKind> cells, MultiblockDirection f) {
        return ZoneStructureMatcher.match(ANCHOR, f, p -> cells.getOrDefault(p, BlockKind.OTHER));
    }

    @Test void everyFacingAndFrontColumnAcceptsAndEveryMissingCellRejects() {
        for (var f : MultiblockDirection.values()) for (int column=0; column<SHAPE.width(); column++) {
            var cells = fixture(f, column);
            var result = match(cells, f);
            assertTrue(result.success(), f+" column "+column);
            assertEquals(new ArrayList<>(cells.keySet()), result.memberPositions(),
                "member order must be canonical u outer, v middle, d inner");
            assertEquals(SHAPE.volume(), new HashSet<>(result.memberPositions()).size());
            for (var p : List.copyOf(cells.keySet())) {
                var previous = cells.put(p, BlockKind.OTHER);
                assertFalse(match(cells, f).success(), "missing "+p+" ("+f+" column "+column+")");
                cells.put(p, previous);
            }
        }
    }

    @Test void exactlyOneInletAndOneOrTwoPortsAreAdmitted() {
        var f = MultiblockDirection.SOUTH;
        var cells = fixture(f, 1);
        cells.put(ANCHOR.add(f.back().multiply(3)), BlockKind.POWER_PORT);
        assertTrue(match(cells, f).success(), "two power ports must be accepted");
        cells.put(ANCHOR.add(f.back().multiply(4)), BlockKind.POWER_PORT);
        assertFalse(match(cells, f).success(), "three power ports must be refused");
        var noPorts = fixture(f, 2);
        noPorts.put(ANCHOR.add(f.back()), BlockKind.CASING);
        assertFalse(match(noPorts, f).success(), "zero power ports must be refused");
        var twoInlets = fixture(f, 0);
        twoInlets.put(ANCHOR.add(f.back().multiply(3)), BlockKind.WATER_INLET);
        assertFalse(match(twoInlets, f).success(), "two water inlets must be refused");
        var noInlet = fixture(f, 0);
        noInlet.put(ANCHOR.add(f.back().multiply(2)), BlockKind.CASING);
        assertFalse(match(noInlet, f).success(), "zero water inlets must be refused");
    }

    @Test void inletAndPanelPositionsAreReserved() {
        var f = MultiblockDirection.EAST;
        var atPanel = fixture(f, 0);
        atPanel.put(ANCHOR.add(new MultiblockPos(0,1,0)), BlockKind.WATER_INLET);
        assertFalse(match(atPanel, f).success(), "water inlet must not replace the operating panel");
        var atController = fixture(f, 0);
        atController.put(ANCHOR, BlockKind.WATER_INLET);
        assertFalse(match(atController, f).success(), "water inlet must not replace the controller");
        var panelBeside = fixture(f, 0);
        panelBeside.put(ANCHOR.add(f.right()), BlockKind.OPERATING_PANEL);
        assertFalse(match(panelBeside, f).success(), "operating panel only sits directly above the controller");
    }

    @Test void extraControllerPanelInletAndForeignKindsAreRefused() {
        var f = MultiblockDirection.WEST;
        var extraController = fixture(f, 1);
        extraController.put(ANCHOR.add(f.back().multiply(3)), BlockKind.CONTROLLER);
        assertFalse(match(extraController, f).success(), "extra controller must be refused");
        var extraPanel = fixture(f, 1);
        extraPanel.put(ANCHOR.add(f.back()), BlockKind.OPERATING_PANEL);
        assertFalse(match(extraPanel, f).success(), "second operating panel must be refused");
        var extraInlet = fixture(f, 1);
        extraInlet.put(ANCHOR.add(f.back().multiply(4)), BlockKind.WATER_INLET);
        assertFalse(match(extraInlet, f).success(), "second water inlet must be refused");
        var foreignKind = fixture(f, 1);
        foreignKind.put(ANCHOR.add(f.back().multiply(4)), BlockKind.FLUID_OUTLET);
        assertFalse(match(foreignKind, f).success(), "fluid outlet is not part of the zone vocabulary");
        var missingPanel = fixture(f, 2);
        missingPanel.put(ANCHOR.add(new MultiblockPos(0,1,0)), BlockKind.CASING);
        assertFalse(match(missingPanel, f).success(), "missing operating panel must be refused");
    }

    @Test void wrongDimensionsAreRefused() {
        var f = MultiblockDirection.NORTH;
        // Narrow structure: the u=2 column is missing, so those cells report OTHER.
        var narrow = new LinkedHashMap<MultiblockPos, BlockKind>();
        for (int u=0; u<2; u++) for (int v=0; v<SHAPE.height(); v++) for (int d=0; d<SHAPE.length(); d++) {
            narrow.put(ANCHOR.add(f.right().multiply(u)).add(new MultiblockPos(0,v,0)).add(f.back().multiply(d)), BlockKind.CASING);
        }
        narrow.put(ANCHOR, BlockKind.CONTROLLER);
        narrow.put(ANCHOR.add(new MultiblockPos(0,1,0)), BlockKind.OPERATING_PANEL);
        narrow.put(ANCHOR.add(f.back().multiply(2)), BlockKind.WATER_INLET);
        narrow.put(ANCHOR.add(f.back()), BlockKind.POWER_PORT);
        assertFalse(match(narrow, f).success(), "2-wide volume must be refused");
        // Shallow structure: depth 2 leaves d=2..4 cells as OTHER.
        var shallow = fixture(f, 0);
        for (int u=0; u<SHAPE.width(); u++) for (int v=0; v<SHAPE.height(); v++) for (int d=2; d<SHAPE.length(); d++) {
            shallow.put(ANCHOR.add(f.right().multiply(u)).add(new MultiblockPos(0,v,0)).add(f.back().multiply(d)), BlockKind.OTHER);
        }
        assertFalse(match(shallow, f).success(), "2-deep volume must be refused");
        // Short structure: no top layer, so the v=2 cells report OTHER.
        var short3 = fixture(f, 1);
        for (int u=0; u<SHAPE.width(); u++) for (int d=0; d<SHAPE.length(); d++) {
            short3.put(ANCHOR.add(f.right().multiply(u-1)).add(new MultiblockPos(0,2,0)).add(f.back().multiply(d)), BlockKind.OTHER);
        }
        assertFalse(match(short3, f).success(), "2-tall volume must be refused");
    }
}
