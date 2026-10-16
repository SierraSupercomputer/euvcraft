package com.euvcraft.lithmod.core;

/**
 * One event emitted by a {@link ProcessRun} tick.
 */
public record ProcessRunEvent(Type type, int layerNumber) {

    public enum Type {
        LAYER_COMPLETED,
        MASK_USE_CONSUMED,
        FINISHED
    }

    public ProcessRunEvent {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (layerNumber < 0) {
            throw new IllegalArgumentException("layerNumber must be >= 0, got " + layerNumber);
        }
    }

    public static ProcessRunEvent layerCompleted(int layerNumber) {
        return new ProcessRunEvent(Type.LAYER_COMPLETED, layerNumber);
    }

    public static ProcessRunEvent maskUseConsumed() {
        return new ProcessRunEvent(Type.MASK_USE_CONSUMED, 0);
    }

    public static ProcessRunEvent finished() {
        return new ProcessRunEvent(Type.FINISHED, 0);
    }
}
