package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure, server-independent state machine for one Process Cell run. */
public final class ProcessRun implements ProcessStartResult {
    private final ProcessRunRequest request;
    private FoupContents lot;
    private ProcessState state;
    private int currentLayer;
    private int progressTicks;
    private boolean resistPaid;

    private ProcessRun(ProcessRunRequest request) {
        this.request = request;
        this.lot = request.lot();
        this.state = ProcessState.IDLE;
        this.currentLayer = lot.lotLayer() + 1;
    }

    public static ProcessStartResult start(ProcessRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RefusalReason refusal = refusal(request);
        return refusal == null ? new ProcessRun(request) : refusal;
    }

    private static RefusalReason refusal(ProcessRunRequest request) {
        FoupContents lot = request.lot();
        if (lot == null) return RefusalReason.NO_LOT;
        if (lot.isEmpty()) return RefusalReason.EMPTY_LOT;
        Tier tier = request.tier();
        String dieType = request.dieType();
        if (tier == null || dieType == null) return RefusalReason.NO_MASK;
        if (lot.lotLayer() >= tier.layers()) return RefusalReason.LOT_ALREADY_COMPLETE;
        if (lot.run() != null && !lot.run().matches(tier, dieType, request.cores())) {
            return RefusalReason.SPEC_MISMATCH;
        }
        if (request.usesRemaining() == 0) return RefusalReason.MASK_SPENT;
        if (!request.dieMakeable()) return RefusalReason.DIE_NOT_MAKEABLE;
        if (!request.lineCanRunTier()) return RefusalReason.LINE_CANNOT_RUN_TIER;
        List<String> unmet = request.generationMatrix()
            .unmetLines(request.installedGenerations(), tier).stream().map(UnmetLine::lineId).toList();
        return unmet.isEmpty() ? null : RefusalReason.generationUnmet(unmet);
    }

    public ProcessRunRequest request() { return request; }
    public FoupContents lot() { return lot; }
    public ProcessState state() { return state; }
    public int currentLayer() { return currentLayer; }
    public int progressTicks() { return progressTicks; }
    public boolean resistPaid() { return resistPaid; }
    public boolean maskUseConsumed() { return state == ProcessState.FINISHED; }
    public boolean finishEventsEmitted() { return state == ProcessState.FINISHED; }

    public ProcessTickResult tick(ProcessEnvironment environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        List<ProcessRunEvent> events = new ArrayList<>();
        if (state == ProcessState.FINISHED) return new ProcessTickResult(state, events);
        if (!environment.structureFormed()) {
            state = ProcessState.PAUSED_STRUCTURE;
            return new ProcessTickResult(state, events);
        }

        int layerTicks = request.tier().layerTicks(request.settings());
        int resistNeeded = request.settings().resistPerLayer();
        if (!resistPaid) {
            if (environment.resistAvailable() < resistNeeded) {
                state = ProcessState.PAUSED_NO_RESIST;
                return new ProcessTickResult(state, events);
            }
            if (resistNeeded > 0) environment.consumeResist(resistNeeded);
            resistPaid = true;
        }

        int energyNeeded = request.energyPerTick();
        if (environment.extractEnergy(energyNeeded, true) < energyNeeded
                || environment.extractEnergy(energyNeeded, false) < energyNeeded) {
            state = ProcessState.PAUSED_NO_POWER;
            return new ProcessTickResult(state, events);
        }
        if (progressTicks + 1 < layerTicks) {
            progressTicks++;
            state = ProcessState.RUNNING;
            return new ProcessTickResult(state, events);
        }

        FoupContents next = environment.applyLayer(lot, currentLayer, request.lineStats().defectFactor());
        if (next == null || next.lotLayer() != currentLayer || !next.lotId().equals(lot.lotId())
                || next.capacity() != lot.capacity() || next.size() != lot.size()
                || next.run() == null || !next.run().matches(request.tier(), request.dieType(), request.cores())
                || !next.grade().equals(lot.grade())
                || next.preparationGeneration() != lot.preparationGeneration()) {
            throw new IllegalStateException("applyLayer returned a different lot, did not apply the requested layer, or changed the wafer grade or preparation generation");
        }
        lot = next;
        events.add(ProcessRunEvent.layerCompleted(currentLayer));
        currentLayer++;
        progressTicks = 0;
        resistPaid = false;
        if (lot.lotLayer() == request.tier().layers()) {
            state = ProcessState.FINISHED;
            events.add(ProcessRunEvent.maskUseConsumed());
            events.add(ProcessRunEvent.finished());
        } else {
            state = ProcessState.RUNNING;
        }
        return new ProcessTickResult(state, events);
    }

    public Snapshot snapshot() {
        return new Snapshot(state, currentLayer, progressTicks, resistPaid,
            maskUseConsumed(), finishEventsEmitted());
    }

    /** Restore with the current persisted lot and the same run settings, not the original fresh lot. */
    public static ProcessRun restore(ProcessRunRequest request, Snapshot snapshot) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        FoupContents lot = request.lot();
        Tier tier = request.tier();
        if (lot == null || lot.isEmpty() || tier == null || request.dieType() == null
                || snapshot.currentLayer() != lot.lotLayer() + 1
                || snapshot.progressTicks() >= tier.layerTicks(request.settings())) {
            throw new IllegalArgumentException("Snapshot does not match the persisted lot or run settings");
        }
        if (snapshot.state() == ProcessState.FINISHED) {
            if (lot.lotLayer() != tier.layers() || lot.run() == null
                    || !lot.run().matches(tier, request.dieType(), request.cores())) {
                throw new IllegalArgumentException("Finished snapshot requires a matching completed lot");
            }
        } else if (refusal(request) != null) {
            throw new IllegalArgumentException("Restored request does not satisfy the run's start conditions");
        }
        ProcessRun run = new ProcessRun(request);
        run.state = snapshot.state();
        run.currentLayer = snapshot.currentLayer();
        run.progressTicks = snapshot.progressTicks();
        run.resistPaid = snapshot.resistPaid();
        return run;
    }

    /** Terminal flags retain the existing snapshot shape and are validated against state. */
    public record Snapshot(ProcessState state, int currentLayer, int progressTicks,
            boolean resistPaid, boolean maskUseConsumed, boolean finishEventsEmitted) {
        public Snapshot {
            Objects.requireNonNull(state, "snapshot state must not be null");
            boolean finished = state == ProcessState.FINISHED;
            if (currentLayer < 1 || progressTicks < 0 || (progressTicks > 0 && !resistPaid)
                    || maskUseConsumed != finished || finishEventsEmitted != finished
                    || (finished && (progressTicks != 0 || resistPaid))
                    || (state == ProcessState.IDLE && (progressTicks != 0 || resistPaid))
                    || (state == ProcessState.PAUSED_NO_RESIST && (progressTicks != 0 || resistPaid))
                    || (state == ProcessState.RUNNING && progressTicks == 0 && resistPaid)
                    || (state == ProcessState.PAUSED_NO_POWER && !resistPaid)) {
                throw new IllegalArgumentException("Inconsistent process snapshot");
            }
        }
    }
}
