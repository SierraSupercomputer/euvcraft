package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure state machine for one Process Cell run.
 */
public final class ProcessRun {

    private final ProcessRunRequest request;

    private FoupContents lot;
    private ProcessState state;
    private int currentLayer;
    private int progressTicks;
    private boolean resistPaid;
    private boolean maskUseConsumed;
    private boolean finishEventsEmitted;

    private ProcessRun(ProcessRunRequest request) {
        this(request, null);
    }

    private ProcessRun(ProcessRunRequest request, Snapshot snapshot) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.lot = request.lot();
        if (snapshot == null) {
            this.state = ProcessState.IDLE;
            this.currentLayer = (request.lot() == null) ? 0 : request.lot().lotLayer() + 1;
            this.progressTicks = 0;
            this.resistPaid = false;
            this.maskUseConsumed = false;
            this.finishEventsEmitted = false;
        } else {
            this.state = (snapshot.state() == null) ? ProcessState.IDLE : snapshot.state();
            this.currentLayer = snapshot.currentLayer();
            this.progressTicks = snapshot.progressTicks();
            this.resistPaid = snapshot.resistPaid();
            this.maskUseConsumed = snapshot.maskUseConsumed();
            this.finishEventsEmitted = snapshot.finishEventsEmitted();
        }
    }

    public static Object start(ProcessRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        FoupContents lot = request.lot();
        if (lot == null) {
            return RefusalReason.NO_LOT;
        }
        if (lot.isEmpty()) {
            return RefusalReason.EMPTY_LOT;
        }

        Tier tier = request.tier();
        String dieType = request.dieType();
        if (tier == null || dieType == null) {
            return RefusalReason.NO_MASK;
        }

        if (lot.lotLayer() >= tier.layers()) {
            return RefusalReason.LOT_ALREADY_COMPLETE;
        }

        RunSpec spec = lot.run();
        if (spec != null && !spec.matches(tier, dieType, request.cores())) {
            return RefusalReason.SPEC_MISMATCH;
        }

        if (request.usesRemaining() <= 0) {
            return RefusalReason.MASK_SPENT;
        }

        if (!request.dieMakeable()) {
            return RefusalReason.DIE_NOT_MAKEABLE;
        }

        if (!request.lineCanRunTier()) {
            return RefusalReason.LINE_CANNOT_RUN_TIER;
        }

        List<String> unmet = unmetGenerationNames(request);
        if (!unmet.isEmpty()) {
            return RefusalReason.generationUnmet(unmet);
        }

        return new ProcessRun(request);
    }

    private static List<String> unmetGenerationNames(ProcessRunRequest request) {
        if (request.generationMatrix() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UnmetLine unmet : request.generationMatrix().unmetLines(
                request.installedGenerations(), request.tier())) {
            names.add(unmet.lineId());
        }
        return names;
    }

    public ProcessRunRequest request() {
        return request;
    }

    public FoupContents lot() {
        return lot;
    }

    public ProcessState state() {
        return state;
    }

    public int currentLayer() {
        return currentLayer;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public boolean resistPaid() {
        return resistPaid;
    }

    public boolean maskUseConsumed() {
        return maskUseConsumed;
    }

    public boolean finishEventsEmitted() {
        return finishEventsEmitted;
    }

    public ProcessTickResult tick(ProcessEnvironment environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        List<ProcessRunEvent> events = new ArrayList<>();

        if (state == ProcessState.FINISHED) {
            return new ProcessTickResult(ProcessState.FINISHED, events);
        }

        if (!environment.structureFormed()) {
            state = ProcessState.PAUSED_STRUCTURE;
            return new ProcessTickResult(state, events);
        }

        Tier tier = request.tier();
        FabSettings settings = request.settings();
        int layerTicks = (settings == null) ? tier.layerTicks() : tier.layerTicks(settings);

        int resistNeeded = (settings == null) ? 0 : settings.resistPerLayer();
        if (!resistPaid) {
            if (environment.resistAvailable() < resistNeeded) {
                state = ProcessState.PAUSED_NO_RESIST;
                return new ProcessTickResult(state, events);
            }
            if (resistNeeded > 0) {
                environment.consumeResist(resistNeeded);
            }
            resistPaid = true;
        }

        int energyNeeded = request.energyPerTick();
        int extracted = environment.extractEnergy(energyNeeded, true);
        if (extracted < energyNeeded) {
            state = ProcessState.PAUSED_NO_POWER;
            return new ProcessTickResult(state, events);
        }
        environment.extractEnergy(energyNeeded, false);

        progressTicks++;
        if (progressTicks < layerTicks) {
            state = ProcessState.RUNNING;
            return new ProcessTickResult(state, events);
        }

        double defectFactor = 1.0;
        if (request.lineStats() != null) {
            defectFactor = request.lineStats().defectFactor();
        }
        lot = environment.applyLayer(lot, currentLayer, defectFactor);
        events.add(ProcessRunEvent.layerCompleted(currentLayer));
        currentLayer++;
        progressTicks = 0;
        resistPaid = false;

        if (lot.lotLayer() >= tier.layers() || currentLayer > tier.layers()) {
            state = ProcessState.FINISHED;
            if (!maskUseConsumed) {
                events.add(ProcessRunEvent.maskUseConsumed());
                events.add(ProcessRunEvent.finished());
                maskUseConsumed = true;
                finishEventsEmitted = true;
            }
        } else {
            state = ProcessState.RUNNING;
        }

        return new ProcessTickResult(state, events);
    }

    public Snapshot snapshot() {
        return new Snapshot(state, currentLayer, progressTicks, resistPaid,
                maskUseConsumed, finishEventsEmitted);
    }

    public static ProcessRun restore(ProcessRunRequest request, Snapshot snapshot) {
        return new ProcessRun(request, Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    public record Snapshot(
            ProcessState state,
            int currentLayer,
            int progressTicks,
            boolean resistPaid,
            boolean maskUseConsumed,
            boolean finishEventsEmitted) {
    }
}
