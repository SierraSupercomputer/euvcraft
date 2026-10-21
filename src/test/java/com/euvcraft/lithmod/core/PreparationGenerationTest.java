package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PreparationGenerationTest {
    private FoupContents fresh() {
        return FoupContents.of(UUID.randomUUID(), 25, List.of(new Wafer(0, 1f, 0)), WaferSubstrate.PURE);
    }
    @Test void preparationPreservesActualLotAndPrivateWafers() {
        var source = fresh(); var result = source.withPreparationGeneration(2);
        assertEquals(1, source.preparationGeneration()); assertEquals(2, result.preparationGeneration());
        assertEquals(source.lotId(), result.lotId()); assertEquals(source.wafers(), result.wafers());
        assertEquals(source.capacity(), result.capacity()); assertEquals(WaferSubstrate.PURE, result.grade());
        assertEquals(2, result.publicView().preparationGeneration());
    }
    @Test void emptyCarrierCannotRetainPreparationOrAcceptMixedWafers() {
        var prepared = fresh().withPreparationGeneration(2);
        assertThrows(IllegalStateException.class, () -> prepared.withWafer(new Wafer(0, 1f, 0)));
        assertThrows(IllegalStateException.class, () -> prepared.withWafers(List.of(new Wafer(0, 1f, 0))));
        var emptied = prepared.withoutWaferAt(0);
        assertEquals(1, emptied.preparationGeneration()); assertTrue(emptied.isEmpty());
        assertEquals(1, emptied.withWafer(new Wafer(0, 1f, 0)).preparationGeneration());
    }
    @Test void preparationIsAnAdmissionOperationAndSurvivesProcessing() {
        var prepared = fresh().withPreparationGeneration(2);
        assertThrows(IllegalArgumentException.class, () -> prepared.withPreparationGeneration(2));
        var spec = new RunSpec(DieTypes.LOGIC, 4, 0, .92, 32);
        assertEquals(2, prepared.withRun(spec).preparationGeneration());
        assertThrows(IllegalArgumentException.class, () -> fresh().withRun(spec).withPreparationGeneration(2));
        var old = new FoupContents(UUID.randomUUID(), 25, List.of(new Wafer(1, .5f, 0)), spec, WaferSubstrate.PURE);
        assertEquals(1, old.preparationGeneration());
        assertThrows(IllegalArgumentException.class, () -> old.withPreparationGeneration(2));
        var processed = new FoupContents(prepared.lotId(), 25, List.of(new Wafer(1, .5f, 0)), spec, WaferSubstrate.PURE, 2);
        assertEquals(2, processed.preparationGeneration());
    }
    @Test void malformedOrIneligiblePreparationCannotEnterCoreState() {
        var id = UUID.randomUUID(); var wafers = fresh().wafers();
        for (int generation : new int[]{0, 3, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new FoupContents(id, 25, wafers, null, WaferSubstrate.PURE, generation));
        assertThrows(IllegalArgumentException.class, () -> new FoupContents(id, 25, wafers, null, WaferSubstrate.ORDINARY, 2));
        assertThrows(IllegalArgumentException.class, () -> new FoupContents(id, 25, List.of(), null, WaferSubstrate.PURE, 2));
        assertThrows(IllegalArgumentException.class, () -> FoupContents.empty(id, 25).withPreparationGeneration(2));
        assertThrows(IllegalArgumentException.class, () -> new FoupContents(id, 25, wafers).withPreparationGeneration(2));
    }
    @Test void everyRealYieldLayerRetainsPreparationAndLotIdentity() {
        var original=fresh().withPreparationGeneration(2); var lot=original;
        var tier=ReferenceTiers.byId("T4");
        RollSource rolls=(wafer,step)->new ContextualRng(45L,original.lotId(),wafer,step);
        for(int layer=1;layer<=tier.layers();layer++) {
            lot=YieldRollEngine.applyLayerStep(lot,tier,DieTypes.LOGIC,null,layer,1.0,FabSettings.defaults(),rolls);
            assertEquals(2,lot.preparationGeneration()); assertEquals(original.lotId(),lot.lotId());
            assertEquals(layer,lot.lotLayer());
        }
        assertNotNull(lot.run());
    }
}
