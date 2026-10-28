package com.euvcraft.lithmod.mc.multiblock.g3;

/**
 * G3 machine kinds.
 */
public enum G3MachineKind {
    /** Reticle writer: 3x3x5, writing + metrology modules. */
    RETICLE_WRITER("g3_reticle_writer"),

    /** Deposition etcher: 5x5x7, deposition + rinse + vacuum modules. */
    DEPOSITION_ETCHER("g3_deposition_etcher");

    private final String id;

    G3MachineKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static G3MachineKind ofMachine(String id) {
        for (G3MachineKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return null;
    }
}