package com.euvcraft.lithmod.core.viewer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable validated display DTO for optional recipe viewer.
 * No gameplay authority or private wafer data.
 */
public record ViewerPage(
    String id,
    String machine,
    List<Ingredient> inputs,
    List<Ingredient> outputs,
    List<Ingredient> catalysts,
    List<String> notes,
    long ticks,
    long fePerTick
) {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]{1,192}$");
    private static final Pattern MACHINE_PATTERN = Pattern.compile("^[a-z0-9_]{1,64}$");
    private static final Pattern INGREDIENT_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]{1,192}$");
    private static final int MAX_LIST_SIZE = 12;
    private static final int MAX_NOTES = 8;
    private static final int MAX_NOTE_LENGTH = 160;
    private static final long MAX_TICKS_FE = 1_000_000_000L;
    private static final int MAX_AMOUNT = 1_000_000;
    private static final int MAX_LABEL_LENGTH = 128;
    private static final int MAX_TIER = 8;
    private static final int MAX_CORES = 64;
    private static final int MAX_USES = 1_000_000;

    public ViewerPage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(outputs, "outputs must not be null");
        Objects.requireNonNull(catalysts, "catalysts must not be null");
        Objects.requireNonNull(notes, "notes must not be null");

        if (id.length()>192 || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match namespaced pattern [a-z0-9_.-]+:[a-z0-9_./-]+, max 192 chars");
        }
        if (!MACHINE_PATTERN.matcher(machine).matches()) {
            throw new IllegalArgumentException("machine must match [a-z0-9_]+, max 64 chars");
        }

        validateList(inputs, "inputs");
        validateList(outputs, "outputs");
        validateList(catalysts, "catalysts");

        if (notes.size() > MAX_NOTES) {
            throw new IllegalArgumentException("notes must have at most " + MAX_NOTES + " elements");
        }
        for (int i = 0; i < notes.size(); i++) {
            String note = notes.get(i);
            if (note == null) {
                throw new IllegalArgumentException("notes[" + i + "] must not be null");
            }
            if (note.length() > MAX_NOTE_LENGTH) {
                throw new IllegalArgumentException("notes[" + i + "] must be at most " + MAX_NOTE_LENGTH + " characters");
            }
        }

        if (ticks < 0 || ticks > MAX_TICKS_FE) {
            throw new IllegalArgumentException("ticks must be in range 0.." + MAX_TICKS_FE);
        }
        if (fePerTick < 0 || fePerTick > MAX_TICKS_FE) {
            throw new IllegalArgumentException("fePerTick must be in range 0.." + MAX_TICKS_FE);
        }

        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        catalysts = List.copyOf(catalysts);
        notes = List.copyOf(notes);
    }

    private static void validateList(List<Ingredient> list, String name) {
        if (list.size() > MAX_LIST_SIZE) {
            throw new IllegalArgumentException(name + " must have at most " + MAX_LIST_SIZE + " elements");
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                throw new IllegalArgumentException(name + "[" + i + "] must not be null");
            }
        }
    }

    /**
     * Ingredient kind: ITEM, TAG, FLUID, GAS.
     */
    public enum Kind {
        ITEM, TAG, FLUID, GAS
    }

    /**
     * Nested immutable ingredient record.
     */
    public record Ingredient(
        Kind kind,
        String id,
        int amount,
        String label,
        String family,
        int tier,
        int cores,
        int uses
    ) {
        private static final Pattern FAMILY_PATTERN = Pattern.compile("^(|logic|compute|memory|nand)$");

        public Ingredient {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(family, "family must not be null");

            if (id.length()>192 || !INGREDIENT_ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("id must match namespaced pattern [a-z0-9_.-]+:[a-z0-9_./-]+, max 192 chars");
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                throw new IllegalArgumentException("amount must be in range 1.." + MAX_AMOUNT);
            }
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("label must be at most " + MAX_LABEL_LENGTH + " characters");
            }
            if (!FAMILY_PATTERN.matcher(family).matches()) {
                throw new IllegalArgumentException("family must be empty, logic, compute, memory, or nand");
            }
            if (tier < 0 || tier > MAX_TIER) {
                throw new IllegalArgumentException("tier must be in range 0.." + MAX_TIER);
            }
            if (cores < 0 || cores > MAX_CORES) {
                throw new IllegalArgumentException("cores must be in range 0.." + MAX_CORES);
            }
            if (uses < 0 || uses > MAX_USES) {
                throw new IllegalArgumentException("uses must be in range 0.." + MAX_USES);
            }
        }

        /**
         * Factory for simple item ingredient with defaults.
         */
        public static Ingredient item(String id, int amount) {
            return new Ingredient(Kind.ITEM, id, amount, "", "", 0, 0, 0);
        }

        /**
         * Returns a copy with label changed.
         */
        public Ingredient labelled(String text) {
            Objects.requireNonNull(text, "label text must not be null");
            return new Ingredient(kind, id, amount, text, family, tier, cores, uses);
        }
    }
}
