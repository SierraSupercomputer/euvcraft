package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Immutable registry of lithography tiers.
 * Provides lookup by id and index, ordered iteration, and next/previous navigation
 * with optional T6 skipping.
 */
public final class TierRegistry {
    private final List<Tier> tiersByIndex;
    private final Map<String, Tier> tiersById;
    private final Map<Integer, Tier> tiersByIndexMap;

    private TierRegistry(List<Tier> tiers) {
        Map<String, Tier> idMap = new HashMap<>();
        Map<Integer, Tier> indexMap = new HashMap<>();
        for (Tier tier : tiers) {
            idMap.put(tier.id(), tier);
            indexMap.put(tier.index(), tier);
        }
        this.tiersByIndex = Collections.unmodifiableList(new ArrayList<>(tiers));
        this.tiersById = Collections.unmodifiableMap(idMap);
        this.tiersByIndexMap = Collections.unmodifiableMap(indexMap);
    }

    /**
     * Builds a TierRegistry from a list of tiers, validating the data.
     * 
     * @param tiers the list of tiers to register
     * @param sourceDescription description of the data source (for error messages)
     * @return a new TierRegistry
     * @throws IllegalArgumentException if validation fails
     */
    public static TierRegistry build(List<Tier> tiers, String sourceDescription) {
        Objects.requireNonNull(tiers, "tiers list must not be null");
        Objects.requireNonNull(sourceDescription, "sourceDescription must not be null");

        if (tiers.isEmpty()) {
            throw new IllegalArgumentException(sourceDescription + ": no tiers provided");
        }

        // Check for duplicate IDs
        Map<String, Tier> idMap = new HashMap<>();
        for (Tier tier : tiers) {
            Tier existing = idMap.put(tier.id(), tier);
            if (existing != null) {
                throw new IllegalArgumentException(sourceDescription + ": duplicate tier id '" + tier.id() + "'");
            }
        }

        // Check for duplicate indexes
        Map<Integer, Tier> indexMap = new HashMap<>();
        for (Tier tier : tiers) {
            Tier existing = indexMap.put(tier.index(), tier);
            if (existing != null) {
                throw new IllegalArgumentException(sourceDescription + ": duplicate tier index " + tier.index() + " (ids: '" + existing.id() + "' and '" + tier.id() + "')");
            }
        }

        // Check for contiguous indexes starting at 1
        int maxIndex = tiers.stream().mapToInt(Tier::index).max().orElse(0);
        for (int i = 1; i <= maxIndex; i++) {
            if (!indexMap.containsKey(i)) {
                throw new IllegalArgumentException(sourceDescription + ": missing tier index " + i + " (indexes must be contiguous starting at 1)");
            }
        }

        // Sort by index
        List<Tier> sorted = new ArrayList<>(tiers);
        sorted.sort((a, b) -> Integer.compare(a.index(), b.index()));

        // Validate exactly 8 tiers (T1-T8)
        if (sorted.size() != 8) {
            throw new IllegalArgumentException(sourceDescription + ": expected exactly 8 tiers (T1-T8), got " + sorted.size());
        }

        // Validate IDs match expected pattern T1-T8
        for (int i = 0; i < sorted.size(); i++) {
            String expectedId = "T" + (i + 1);
            Tier tier = sorted.get(i);
            if (!tier.id().equals(expectedId)) {
                throw new IllegalArgumentException(sourceDescription + ": tier at index " + tier.index() + " has id '" + tier.id() + "', expected '" + expectedId + "'");
            }
            if (tier.index() != i + 1) {
                throw new IllegalArgumentException(sourceDescription + ": tier '" + tier.id() + "' has index " + tier.index() + ", expected " + (i + 1));
            }
        }

        return new TierRegistry(sorted);
    }

    /**
     * Creates a builder for constructing a TierRegistry programmatically (mainly for tests).
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the tier with the given ID, or empty if not found.
     */
    public Optional<Tier> byId(String id) {
        return Optional.ofNullable(tiersById.get(id));
    }

    /**
     * Returns the tier with the given index (1-8), or empty if not found.
     */
    public Optional<Tier> byIndex(int index) {
        return Optional.ofNullable(tiersByIndexMap.get(index));
    }

    /**
     * Returns the tier with the given ID, throwing if not found.
     */
    public Tier getById(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("No tier with id: " + id));
    }

    /**
     * Returns the tier with the given index, throwing if not found.
     */
    public Tier getByIndex(int index) {
        return byIndex(index).orElseThrow(() -> new IllegalArgumentException("No tier with index: " + index));
    }

    /**
     * Returns all tiers in index order (T1 to T8).
     */
    public List<Tier> all() {
        return tiersByIndex;
    }

    /**
     * Returns the next tier after the given one, optionally skipping T6 if disabled.
     * Returns empty if the given tier is the last available.
     */
    public Optional<Tier> next(Tier tier, boolean tier6Enabled) {
        int currentIdx = tier.index();
        for (int i = currentIdx + 1; i <= 8; i++) {
            Tier candidate = tiersByIndexMap.get(i);
            if (candidate != null && (tier6Enabled || !candidate.optional())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the previous tier before the given one, optionally skipping T6 if disabled.
     * Returns empty if the given tier is the first available.
     */
    public Optional<Tier> previous(Tier tier, boolean tier6Enabled) {
        int currentIdx = tier.index();
        for (int i = currentIdx - 1; i >= 1; i--) {
            Tier candidate = tiersByIndexMap.get(i);
            if (candidate != null && (tier6Enabled || !candidate.optional())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the next tier after the given index, optionally skipping T6 if disabled.
     */
    public Optional<Tier> next(int index, boolean tier6Enabled) {
        return byIndex(index).flatMap(t -> next(t, tier6Enabled));
    }

    /**
     * Returns the previous tier before the given index, optionally skipping T6 if disabled.
     */
    public Optional<Tier> previous(int index, boolean tier6Enabled) {
        return byIndex(index).flatMap(t -> previous(t, tier6Enabled));
    }

    /**
     * Compares two tiers by index. Returns negative if a < b, positive if a > b, 0 if equal.
     * Null-safe: null is considered less than any tier.
     */
    public int compareByIndex(Tier a, Tier b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return Integer.compare(a.index(), b.index());
    }

    /**
     * Returns true if tier a is strictly before tier b in index order.
     */
    public boolean isBefore(Tier a, Tier b) {
        return compareByIndex(a, b) < 0;
    }

    /**
     * Returns true if tier a is strictly after tier b in index order.
     */
    public boolean isAfter(Tier a, Tier b) {
        return compareByIndex(a, b) > 0;
    }

    /**
     * Returns a stream of tiers matching the given predicate.
     */
    public List<Tier> filter(Predicate<Tier> predicate) {
        return tiersByIndex.stream().filter(predicate).toList();
    }

    /**
     * Returns all non-optional tiers (i.e., tiers available when T6 is disabled).
     */
    public List<Tier> requiredTiers() {
        return tiersByIndex.stream().filter(t -> !t.optional()).toList();
    }

    @Override
    public String toString() {
        return "TierRegistry{" + tiersByIndex + "}";
    }

    /**
     * Builder for constructing a TierRegistry programmatically.
     */
    public static final class Builder {
        private final List<Tier> tiers = new ArrayList<>();

        private Builder() {}

        public Builder add(Tier tier) {
            tiers.add(tier);
            return this;
        }

        public TierRegistry build(String sourceDescription) {
            return TierRegistry.build(tiers, sourceDescription);
        }
    }
}