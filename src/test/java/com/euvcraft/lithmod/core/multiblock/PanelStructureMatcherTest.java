package com.euvcraft.lithmod.core.multiblock;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelStructureMatcherTest {
    private static final MultiblockPos ANCHOR = new MultiblockPos(12, 30, -17);
    private Map<MultiblockPos, BlockKind> fixture(StructureShape s, MultiblockDirection f, int column) {
        var cells = new LinkedHashMap<MultiblockPos, BlockKind>();
        for (int u=0; u<s.width(); u++) for (int v=0; v<s.height(); v++) for(int d=0; d<s.length(); d++) {
            var p = ANCHOR.add(f.right().multiply(u-column)).add(new MultiblockPos(0,v,0)).add(f.back().multiply(d));
            cells.put(p, BlockKind.CASING);
        }
        cells.put(ANCHOR, BlockKind.CONTROLLER);
        cells.put(ANCHOR.add(new MultiblockPos(0,1,0)), BlockKind.OPERATING_PANEL);
        cells.put(ANCHOR.add(f.back()), BlockKind.POWER_PORT);
        return cells;
    }
    @Test void everyFacingAndFrontColumnAndEveryMissingCell() {
        for (var s : List.of(new StructureShape(2,2,3), new StructureShape(3,3,5)))
            for (var f : MultiblockDirection.values()) for (int column=0;column<s.width();column++) {
                var cells = fixture(s,f,column);
                var result = PanelStructureMatcher.match(ANCHOR,f,s,p->cells.getOrDefault(p,BlockKind.OTHER));
                assertTrue(result.success(), s+" "+f+" "+column);
                assertEquals(new ArrayList<>(cells.keySet()), result.memberPositions());
                assertEquals(s.volume(), new HashSet<>(result.memberPositions()).size());
                for (var p : List.copyOf(cells.keySet())) {
                    var previous=cells.put(p,BlockKind.OTHER);
                    assertFalse(PanelStructureMatcher.match(ANCHOR,f,s,q->cells.getOrDefault(q,BlockKind.OTHER)).success(), "missing "+p);
                    cells.put(p,previous);
                }
            }
    }
    @Test void onlyOnePanelAndControllerAndOneOrTwoPortsAreAdmitted() {
        var s=new StructureShape(3,3,5);var f=MultiblockDirection.EAST;
        for(var wrong:List.of(BlockKind.CONTROLLER,BlockKind.OPERATING_PANEL,BlockKind.WATER_INLET,BlockKind.OTHER)) {
            var cells=fixture(s,f,1);cells.put(ANCHOR.add(f.back().multiply(4)),wrong);
            assertFalse(PanelStructureMatcher.match(ANCHOR,f,s,p->cells.getOrDefault(p,BlockKind.OTHER)).success());
        }
        var cells=fixture(s,f,1);var second=ANCHOR.add(f.back().multiply(2));cells.put(second,BlockKind.POWER_PORT);
        assertTrue(PanelStructureMatcher.match(ANCHOR,f,s,p->cells.getOrDefault(p,BlockKind.OTHER)).success());
        cells.put(ANCHOR.add(f.back().multiply(3)),BlockKind.POWER_PORT);
        assertFalse(PanelStructureMatcher.match(ANCHOR,f,s,p->cells.getOrDefault(p,BlockKind.OTHER)).success());
        final var noPorts=fixture(s,f,1);noPorts.put(ANCHOR.add(f.back()),BlockKind.CASING);
        assertFalse(PanelStructureMatcher.match(ANCHOR,f,s,p->noPorts.getOrDefault(p,BlockKind.OTHER)).success());
    }
}
