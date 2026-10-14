package com.euvcraft.lithmod.core;

/**
 * Enum form of the die types. The canonical core APIs work with strings internally
 * but this enum is provided for callers that prefer typed constants. Its string key
 * is the lowercase die-type name.
 */
public enum DieType {
    COMPUTE(DieTypes.COMPUTE),
    LOGIC(DieTypes.LOGIC),
    MEMORY(DieTypes.MEMORY),
    NAND(DieTypes.NAND);

    private final String key;

    DieType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String value() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
