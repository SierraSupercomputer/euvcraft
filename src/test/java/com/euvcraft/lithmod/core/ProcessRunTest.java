package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-000000000808");
    private static final long SALT = 0x12345L;

    private static Tier t1() { return ReferenceTiers.byId("T1"); }
    private static Tier t2() { return ReferenceTiers.byId("T2"); }

    private static FabSettings settings(Map<String, Integer> overrides) {
        return FabSettings.builder().layerTicksOverrides(overrides).build();
    }

    private static FoupContents freshLot(int count) {
        FoupContents lot = FoupContents.empty(LOT, Math.max(count, 1));
        for (int i = 0; i < count; i++) {
            lot = lot.withWafer(Wafer.unprocessed());
        }
        return lot;
    }

    private static LineStats healthy() {
        return new LineStats(100, 10, 10, 1.0, 2, 2, 2);
    }

    private static class FakeEnv implements ProcessEnvironment {
        boolean formed = true;
        int energy;
        int resist;
        final Tier tier;
        final String dieType;
        final Integer cores;
        final FabSettings settings;
        final List<Integer> applied = new ArrayList<>();
        final long salt;

        FakeEnv(Tier tier, String dieType, Integer cores, FabSettings settings, int energy, int resist) {
            this.tier = tier;
            this.dieType = dieType;
            this.cores = cores;
            this.settings = settings;
            this.energy = energy;
            this.resist = resist;
            this.salt = SALT;
        }

        @Override
        public boolean structureFormed() { return formed; }

        @Override
        public int extractEnergy(int maxAmount, boolean simulate) {
            if (energy >= maxAmount) {
                if (!simulate) energy -= maxAmount;
                return maxAmount;
            }
            int out = energy;
            if (!simulate) energy = 0;
            return out;
        }

        @Override
        public int resistAvailable() { return resist; }

        @Override
        public void consumeResist(int amount) { resist -= amount; }

        @Override
        public FoupContents applyLayer(FoupContents lot, int layerNumber, double defectFactor) {
            applied.add(layerNumber);
            return YieldRollEngine.applyLayerStep(lot, tier, dieType, cores, layerNumber, defectFactor,
                    settings, (wi, si) -> new ContextualRng(salt, lot.lotId(), wi, si));
        }
    }

    private static int runToFinish(ProcessRun run, FakeEnv env, int limit) {
        int ticks = 0;
        while (run.state() != ProcessState.FINISHED && ticks < limit) {
            run.tick(env);
            ticks++;
        }
        return ticks;
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {
        @Test
        void noLot() {
            ProcessRunRequest req = new ProcessRunRequest(null, "logic", t1(), null, 4, healthy(), true, true, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.NO_LOT, ProcessRun.start(req));
        }

        @Test
        void emptyLot() {
            ProcessRunRequest req = new ProcessRunRequest(FoupContents.empty(LOT, 25), "logic", t1(), null, 4, healthy(), true, true, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.EMPTY_LOT, ProcessRun.start(req));
        }

        @Test
        void lotAlreadyComplete() {
            FoupContents lot = freshLot(2);
            Tier t1 = t1();
            FabSettings s = settings(Map.of());
            FoupContents done = YieldRollEngine.applyLayerStep(lot, t1, "logic", null, 1, 1.0, s,
                    (wi, si) -> new ContextualRng(SALT, lot.lotId(), wi, si));
            ProcessRunRequest req = new ProcessRunRequest(done, "logic", t1, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            assertSame(RefusalReason.LOT_ALREADY_COMPLETE, ProcessRun.start(req));
        }

        @Test
        void specMismatch() {
            FoupContents lot = freshLot(2);
            Tier t1 = t1();
            FabSettings s = settings(Map.of());
            FoupContents started = YieldRollEngine.applyLayerStep(lot, t1, "logic", null, 1, 1.0, s,
                    (wi, si) -> new ContextualRng(SALT, lot.lotId(), wi, si));
            ProcessRunRequest req = new ProcessRunRequest(started, "memory", t2(), null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            Object result = ProcessRun.start(req);
            assertInstanceOf(RefusalReason.class, result);
            assertEquals(RefusalReason.Code.SPEC_MISMATCH, ((RefusalReason) result).code());
        }

        @Test
        void noMask() {
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), null, t1(), null, 4, healthy(), true, true, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.NO_MASK, ProcessRun.start(req));
        }

        @Test
        void maskSpent() {
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", t1(), null, 0, healthy(), true, true, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.MASK_SPENT, ProcessRun.start(req));
        }

        @Test
        void dieNotMakeable() {
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", t1(), null, 4, healthy(), true, false, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.DIE_NOT_MAKEABLE, ProcessRun.start(req));
        }

        @Test
        void lineCannotRunTier() {
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", t1(), null, 4, healthy(), false, true, installed(), matrix(), settings(Map.of()), 5);
            assertSame(RefusalReason.LINE_CANNOT_RUN_TIER, ProcessRun.start(req));
        }

        @Test
        void generationUnmetUsingCustomMatrix() {
            Tier tier = t1();
            TierRegistry reg = TierRegistry.build(ReferenceTiers.all(), "test");
            Map<String, List<Integer>> m = new java.util.LinkedHashMap<>();
            m.put("substrate", List.of(2, 2, 2, 2, 2, 2, 2, 2));
            for (String l : List.of("mask_shop", "deposition_etch", "utilities", "test_packaging")) {
                m.put(l, List.of(1, 1, 1, 1, 1, 1, 1, 1));
            }
            GenerationMatrix gm = GenerationMatrix.build(reg, m, "custom");
            Map<String, Integer> installed = Map.of(
                    "substrate", 1, "mask_shop", 1, "deposition_etch", 1, "utilities", 1, "test_packaging", 1);
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 4, healthy(), true, true,
                    installed, gm, settings(Map.of()), 5);
            Object result = ProcessRun.start(req);
            assertInstanceOf(RefusalReason.class, result);
            RefusalReason r = (RefusalReason) result;
            assertEquals(RefusalReason.Code.GENERATION_UNMET, r.code());
            assertFalse(r.unmetLines().isEmpty());
            assertTrue(r.unmetLines().contains("substrate"));
        }
    }

    @Nested
    @DisplayName("runs")
    class Runs {
        @Test
        void t1FinishesAfterExpectedTicks() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(3), "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 100, 10);
            int ticks = runToFinish(run, env, 100);
            assertEquals(4, ticks);
            assertEquals(List.of(1), env.applied);
            assertEquals(1, run.lot().lotLayer());
            assertEquals(ProcessState.FINISHED, run.state());
        }

        @Test
        void t2FinishesAfterExpectedTicks() {
            Tier tier = t2();
            FabSettings s = settings(Map.of("T2", 2));
            FoupContents lot = freshLot(3);
            ProcessRunRequest req = new ProcessRunRequest(lot, "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 1000, 100);
            int ticks = runToFinish(run, env, 1000);
            assertEquals(12 * 2, ticks);
            assertEquals(12, env.applied.size());
            List<Integer> expected = new ArrayList<>();
            for (int i = 1; i <= 12; i++) expected.add(i);
            assertEquals(expected, env.applied);
            assertEquals(12, run.lot().lotLayer());
        }

        @Test
        void maskConsumedOnceWithEvents() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 2));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 1, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 100, 10);
            int maskCount = 0, finishCount = 0, layerCount = 0;
            while (run.state() != ProcessState.FINISHED) {
                ProcessTickResult r = run.tick(env);
                for (ProcessRunEvent e : r.events()) {
                    switch (e.type()) {
                        case MASK_USE_CONSUMED -> maskCount++;
                        case FINISHED -> finishCount++;
                        case LAYER_COMPLETED -> layerCount++;
                    }
                }
            }
            assertEquals(1, maskCount);
            assertEquals(1, finishCount);
            assertEquals(1, layerCount);
        }

        @Test
        void pauseNoPowerDoesNotAdvance() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 0, 10);
            ProcessTickResult r = run.tick(env);
            assertEquals(ProcessState.PAUSED_NO_POWER, r.state());
            assertEquals(0, run.progressTicks());
            assertTrue(env.applied.isEmpty());
            // resume
            env.energy = 100;
            ProcessTickResult r2 = run.tick(env);
            assertEquals(ProcessState.RUNNING, r2.state());
            assertEquals(1, run.progressTicks());
        }

        @Test
        void pauseNoResistDoesNotAdvance() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 100, 0);
            ProcessTickResult r = run.tick(env);
            assertEquals(ProcessState.PAUSED_NO_RESIST, r.state());
            assertEquals(0, run.progressTicks());
            assertEquals(100, env.energy); // no energy consumed
            env.resist = 10;
            ProcessTickResult r2 = run.tick(env);
            assertEquals(ProcessState.RUNNING, r2.state());
            assertEquals(1, run.progressTicks());
        }

        @Test
        void pauseStructureDoesNotAdvance() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 100, 10);
            env.formed = false;
            ProcessTickResult r = run.tick(env);
            assertEquals(ProcessState.PAUSED_STRUCTURE, r.state());
            env.formed = true;
            ProcessTickResult r2 = run.tick(env);
            assertEquals(ProcessState.RUNNING, r2.state());
        }

        @Test
        void resistConsumedOncePerLayer() {
            Tier tier = t2();
            FabSettings s = settings(Map.of("T2", 2));
            FoupContents lot = freshLot(2);
            ProcessRunRequest req = new ProcessRunRequest(lot, "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 1000, 12); // one resist per layer for all 12 layers
            runToFinish(run, env, 1000);
            assertEquals(ProcessState.FINISHED, run.state());
            assertEquals(0, env.resist, "all layer resists should be consumed exactly once");
            assertEquals(12, env.applied.size());
        }

        @Test
        void snapshotMidRunProducesIdenticalResult() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            FoupContents lot = freshLot(2);
            ProcessRunRequest req = new ProcessRunRequest(lot, "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);

            ProcessRun runA = (ProcessRun) ProcessRun.start(req);
            FakeEnv envA = new FakeEnv(tier, "logic", null, s, 100, 10);
            runToFinish(runA, envA, 100);

            ProcessRun runB = (ProcessRun) ProcessRun.start(req);
            FakeEnv envB = new FakeEnv(tier, "logic", null, s, 100, 10);
            runB.tick(envB);
            runB.tick(envB);
            ProcessRun.Snapshot snap = runB.snapshot();
            ProcessRun restored = ProcessRun.restore(req, snap);
            FakeEnv envC = new FakeEnv(tier, "logic", null, s, 100, 10);
            // align env lot id? applied rolls depend lot id, same lot; env applies same
            int remaining = runToFinish(restored, envC, 100);
            assertEquals(2, remaining, "restored should finish in remaining ticks");
            // compare wafer cumulative rolls
            List<Wafer> a = runA.lot().wafers();
            List<Wafer> b = restored.lot().wafers();
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).cumulativeRoll(), b.get(i).cumulativeRoll(),
                        "wafer " + i + " cumulative roll mismatch");
                assertEquals(a.get(i).layerIndex(), b.get(i).layerIndex());
            }
        }

        @Test
        void partWayLotContinuesFromLayer() {
            Tier tier = t2();
            FabSettings s = settings(Map.of("T2", 2));
            FoupContents lot = freshLot(2);
            // apply 5 layers
            FoupContents progressed = lot;
            for (int l = 1; l <= 5; l++) {
                progressed = YieldRollEngine.applyLayerStep(progressed, tier, "logic", null, l, 1.0, s,
                        (wi, si) -> new ContextualRng(SALT, lot.lotId(), wi, si));
            }
            assertEquals(5, progressed.lotLayer());
            ProcessRunRequest req = new ProcessRunRequest(progressed, "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 1000, 100);
            int ticks = runToFinish(run, env, 1000);
            assertEquals(7 * 2, ticks, "remaining 7 layers * 2 ticks");
            assertEquals(12, run.lot().lotLayer());
            // applied should be layers 6..12
            List<Integer> expected = new ArrayList<>();
            for (int i = 6; i <= 12; i++) expected.add(i);
            assertEquals(expected, env.applied);
        }
    }

    @Nested
    @DisplayName("snapshot")
    class SnapshotOps {
        @Test
        void restoreMidLayerKeepsResistPaidFlag() {
            Tier tier = t1();
            FabSettings s = settings(Map.of("T1", 4));
            ProcessRunRequest req = new ProcessRunRequest(freshLot(2), "logic", tier, null, 4, healthy(), true, true, installed(), matrix(), s, 5);
            ProcessRun run = (ProcessRun) ProcessRun.start(req);
            FakeEnv env = new FakeEnv(tier, "logic", null, s, 100, 10);
            // tick twice: progressTicks=2, resist paid already first tick
            run.tick(env);
            run.tick(env);
            assertTrue(run.resistPaid());
            ProcessRun.Snapshot snap = run.snapshot();
            ProcessRun restored = ProcessRun.restore(req, snap);
            assertEquals(2, restored.progressTicks());
            assertTrue(restored.resistPaid(), "resistPaid flag should survive restore");
        }
    }

    private static Map<String, Integer> installed() {
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        SupportLines.ALL.forEach(line -> values.put(line, 1));
        return values;
    }

    private static GenerationMatrix matrix() {
        Map<String, List<Integer>> values = new java.util.LinkedHashMap<>();
        SupportLines.ALL.forEach(line -> values.put(line, java.util.Collections.nCopies(8, 1)));
        return GenerationMatrix.build(TierRegistry.build(ReferenceTiers.all(), "test"), values, "T1/T2 fixture");
    }

    private static ProcessRunRequest request(FoupContents lot, Tier tier, FabSettings settings) {
        return new ProcessRunRequest(lot, "logic", tier, null, 4, healthy(), true, true,
            installed(), matrix(), settings, 5);
    }

    @Test
    void actualEnergyWithdrawalMustPayBeforeProgress() {
        FabSettings settings = settings(Map.of("T1", 1));
        ProcessRun run = (ProcessRun) ProcessRun.start(request(freshLot(2), t1(), settings));
        FakeEnv env = new FakeEnv(t1(), "logic", null, settings, 100, 10) {
            @Override public int extractEnergy(int amount, boolean simulate) {
                return simulate ? amount : 0;
            }
        };
        assertEquals(ProcessState.PAUSED_NO_POWER, run.tick(env).state());
        assertEquals(0, run.progressTicks());
        assertEquals(0, run.lot().lotLayer());
        assertTrue(env.applied.isEmpty());
    }

    @Test
    void restoredMultiLayerRunMatchesUninterruptedResourcesAndWafers() {
        FabSettings settings = settings(Map.of("T2", 2));
        FoupContents lot = freshLot(2);
        ProcessRun reference = (ProcessRun) ProcessRun.start(request(lot, t2(), settings));
        FakeEnv expected = new FakeEnv(t2(), "logic", null, settings, 120, 12);
        assertEquals(24, runToFinish(reference, expected, 100));

        ProcessRun partial = (ProcessRun) ProcessRun.start(request(lot, t2(), settings));
        FakeEnv initial = new FakeEnv(t2(), "logic", null, settings, 120, 12);
        partial.tick(initial); partial.tick(initial); partial.tick(initial);
        assertEquals(1, partial.lot().lotLayer());
        assertEquals(1, partial.progressTicks());
        int savedEnergy = initial.energy;
        initial.energy = 0;
        assertEquals(ProcessState.PAUSED_NO_POWER, partial.tick(initial).state());
        ProcessRun.Snapshot snapshot = partial.snapshot();
        ProcessRunRequest resumedRequest = request(partial.lot(), t2(), settings);
        ProcessRun restored = ProcessRun.restore(resumedRequest, snapshot);
        FakeEnv resumed = new FakeEnv(t2(), "logic", null, settings, savedEnergy, initial.resist);
        assertEquals(21, runToFinish(restored, resumed, 100));
        assertEquals(reference.lot().wafers(), restored.lot().wafers());
        assertEquals(reference.lot().run(), restored.lot().run());
        assertEquals(reference.lot().lotId(), restored.lot().lotId());
        assertEquals(expected.energy, resumed.energy);
        assertEquals(expected.resist, resumed.resist);
        assertEquals(12, initial.applied.size() + resumed.applied.size());
        // A corrupted paid-resist flag must not turn the same persisted progress into a free/duplicate charge.
        assertThrows(IllegalArgumentException.class, () -> ProcessRun.restore(resumedRequest,
            new ProcessRun.Snapshot(snapshot.state(), snapshot.currentLayer(), snapshot.progressTicks(),
                false, false, false)));
    }

    @Test
    void restoreRejectsStaleLotInsteadOfSkippingCompletedLayers() {
        FabSettings settings = settings(Map.of("T2", 2));
        ProcessRunRequest original = request(freshLot(2), t2(), settings);
        ProcessRun run = (ProcessRun) ProcessRun.start(original);
        FakeEnv env = new FakeEnv(t2(), "logic", null, settings, 120, 12);
        run.tick(env); run.tick(env); run.tick(env);
        assertThrows(IllegalArgumentException.class, () -> ProcessRun.restore(original, run.snapshot()));
    }

    @Test
    void restoreRejectsOutOfRangeProgress() {
        FabSettings settings = settings(Map.of("T1", 2));
        ProcessRunRequest request = request(freshLot(2), t1(), settings);
        assertThrows(IllegalArgumentException.class, () -> ProcessRun.restore(request,
            new ProcessRun.Snapshot(ProcessState.RUNNING, 1, 2, true, false, false)));
    }

    @Test
    void finishedSnapshotsCannotReplayMaskOrFinishEvents() {
        FabSettings settings = settings(Map.of("T1", 1));
        ProcessRun run = (ProcessRun) ProcessRun.start(request(freshLot(2), t1(), settings));
        FakeEnv env = new FakeEnv(t1(), "logic", null, settings, 100, 10);
        assertEquals(3, run.tick(env).events().size());
        ProcessRun restored = ProcessRun.restore(request(run.lot(), t1(), settings), run.snapshot());
        int energy = env.energy, resist = env.resist;
        assertTrue(restored.tick(env).events().isEmpty());
        assertEquals(energy, env.energy); assertEquals(resist, env.resist);
        assertThrows(IllegalArgumentException.class, () -> ProcessRun.restore(request(run.lot(), t1(), settings),
            new ProcessRun.Snapshot(ProcessState.FINISHED, 2, 0, false, false, false)));
    }

    @Test
    void missingRequestDependenciesCannotBypassGenerationOrResistRules() {
        FabSettings settings = settings(Map.of());
        assertThrows(NullPointerException.class, () -> new ProcessRunRequest(freshLot(2), "logic", t1(), null,
            4, healthy(), true, true, installed(), null, settings, 5));
        assertThrows(NullPointerException.class, () -> new ProcessRunRequest(freshLot(2), "logic", t1(), null,
            4, healthy(), true, true, installed(), matrix(), null, 5));
        assertThrows(IllegalArgumentException.class, () -> new ProcessRunRequest(freshLot(2), "logic", t1(), null,
            4, healthy(), true, true, installed(), matrix(), settings, -1));
    }

    @Test
    void environmentMustActuallyApplyTheRequestedLayer() {
        FabSettings settings = settings(Map.of("T1", 1));
        ProcessRun run = (ProcessRun) ProcessRun.start(request(freshLot(2), t1(), settings));
        FakeEnv env = new FakeEnv(t1(), "logic", null, settings, 100, 10) {
            @Override public FoupContents applyLayer(FoupContents lot, int layer, double factor) { return lot; }
        };
        assertThrows(IllegalStateException.class, () -> run.tick(env));
        assertNotEquals(ProcessState.FINISHED, run.state());
    }

    @Test
    void nonFiniteStatisticsCannotEnterAProcessRequest() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessRunRequest(freshLot(2), "logic", t1(), null,
            4, new LineStats(100, 10, 10, Double.NaN, 2, 2, 2), true, true,
            installed(), matrix(), settings(Map.of()), 5));
    }

    @Test
    void runningSnapshotCannotClaimPaidResistBeforeAnyProgress() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessRun.Snapshot(ProcessState.RUNNING, 2, 0, true, false, false));
        assertDoesNotThrow(() ->
            new ProcessRun.Snapshot(ProcessState.RUNNING, 2, 0, false, false, false));
    }

    @Test
    void powerPausedSnapshotMustRetainItsPaidResist() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessRun.Snapshot(ProcessState.PAUSED_NO_POWER, 1, 0, false, false, false));
        assertDoesNotThrow(() ->
            new ProcessRun.Snapshot(ProcessState.PAUSED_NO_POWER, 1, 0, true, false, false));
    }

    @Test void exposureCountsOnlyPaidTicksAndSurvivesResume() {
        FabSettings settings=settings(Map.of("T2",2));
        ProcessRun run=(ProcessRun)ProcessRun.start(request(freshLot(2),t2(),settings));
        FakeEnv env=new FakeEnv(t2(),"logic",null,settings,120,12){
            @Override public FoupContents processingTick(FoupContents lot){return lot.withRoomExposure(lot.roomExposure().sample(750),0);}
        };
        env.energy=0;run.tick(env);assertEquals(0,run.lot().roomExposure().samples());
        env.energy=120;run.tick(env);assertEquals(1,run.lot().roomExposure().samples());
        var restored=ProcessRun.restore(request(run.lot(),t2(),settings),run.snapshot());
        runToFinish(restored,env,100);
        assertEquals(24,restored.lot().roomExposure().samples());
        assertEquals(750,restored.lot().roomExposure().meanPermille());
        assertEquals(0,restored.lot().roomYieldPermille());
    }

}
