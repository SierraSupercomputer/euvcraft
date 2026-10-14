package com.euvcraft.lithmod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable collection of {@link DieRule}s bound to a {@link TierRegistry}.
 *
 * Validates the rules against the supplied tier registry and exposes the
 * derived queries defined by the spec. Die types are canonical lowercase
 * string ids ("compute", "logic", "memory", "nand").
 */
public final class DieRules {

    private final TierRegistry tierRegistry;
    private final Map<String, DieRule> rulesByType;
    private final String sourceDescription;

    public DieRules(TierRegistry tierRegistry, List<DieRule> rules) {
        this(tierRegistry, rules, "die rules");
    }

    public DieRules(TierRegistry tierRegistry, List<DieRule> rules, String sourceDescription) {
        Objects.requireNonNull(tierRegistry, "tierRegistry must not be null");
        String source = (sourceDescription == null || sourceDescription.isBlank())
            ? "die rules" : sourceDescription.trim();
        this.tierRegistry = tierRegistry;
        this.sourceDescription = source;

        Map<String, DieRule> built = new LinkedHashMap<>();
        int tierCount = tierRegistry.all().size();
        if (rules != null) {
            for (DieRule rule : rules) {
                Objects.requireNonNull(rule, source + ": rule must not be null");
                String type = rule.dieType();
                if (type == null || type.isBlank()) {
                    throw new IllegalArgumentException(source + ": field 'dieType' must not be blank");
                }
                type = type.trim().toLowerCase(Locale.ROOT);

                if (rule.firstTier() < 1 || rule.firstTier() > tierCount
                        || tierRegistry.byIndex(rule.firstTier()).isEmpty()) {
                    throw new IllegalArgumentException(source + ": field 'firstTier' (" + rule.firstTier() +
                        ") does not exist in TierRegistry for die type '" + type + "'");
                }
                if (rule.capTier() != null) {
                    int cap = rule.capTier();
                    if (cap < 1 || cap > tierCount || tierRegistry.byIndex(cap).isEmpty()) {
                        throw new IllegalArgumentException(source + ": field 'capTier' (" + cap +
                            ") does not exist in TierRegistry for die type '" + type + "'");
                    }
                    if (rule.firstTier() > cap) {
                        throw new IllegalArgumentException(source + ": field 'firstTier' (" + rule.firstTier() +
                            ") must be <= field 'capTier' (" + cap + ") for die type '" + type + "'");
                    }
                }
                DieRule existing = built.put(type, new DieRule(type, rule.firstTier(), rule.capTier(), rule.usesCoreCount()));
                if (existing != null) {
                    throw new IllegalArgumentException(source + ": duplicate die type '" + type + "'");
                }
            }
        }
        this.rulesByType = Collections.unmodifiableMap(built);
    }

    public DieRules(TierRegistry tierRegistry) {
        this(tierRegistry, DieRules.defaultRuleList(), "default die rules");
    }

    public DieRules(List<DieRule> rules, TierRegistry tierRegistry) {
        this(tierRegistry, rules, "die rules");
    }

    public DieRules(List<DieRule> rules, TierRegistry tierRegistry, String sourceDescription) {
        this(tierRegistry, rules, sourceDescription);
    }

    public DieRules(TierRegistry tierRegistry, String sourceDescription) {
        this(tierRegistry, DieRules.defaultRuleList(), sourceDescription);
    }

    public TierRegistry tierRegistry() {
        return tierRegistry;
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public List<DieRule> rules() {
        return List.copyOf(rulesByType.values());
    }

    public Map<String, DieRule> rulesByType() {
        return rulesByType;
    }

    public DieRule ruleFor(String dieType) {
        String type = normalize(dieType);
        DieRule rule = rulesByType.get(type);
        if (rule == null) {
            throw new IllegalArgumentException(sourceDescription + ": unknown die type '" + type + "'");
        }
        return rule;
    }

    public boolean hasRule(String dieType) {
        try {
            return rulesByType.containsKey(normalize(dieType));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean canMake(String dieType, int tierIndex) {
        DieRule rule = rulesByType.get(normalize(dieType));
        if (rule == null) return false;
        return tierIndex >= rule.firstTier();
    }

    public boolean canMake(String dieType, Tier tier) {
        return tier == null ? false : canMake(dieType, tier.index());
    }

    public int effectiveTier(String dieType, int tierIndex) {
        DieRule rule = ruleFor(dieType);
        if (rule.capTier() == null) return tierIndex;
        return Math.min(tierIndex, rule.capTier());
    }

    public int effectiveTier(String dieType, Tier tier) {
        return effectiveTier(dieType, tier == null ? 0 : tier.index());
    }

    public boolean isCapped(String dieType, int tierIndex) {
        return effectiveTier(dieType, tierIndex) != tierIndex;
    }

    public boolean isCapped(String dieType, Tier tier) {
        return isCapped(dieType, tier == null ? 0 : tier.index());
    }

    public int targetSpeedMhz(String dieType, int tierIndex) {
        int eff = effectiveTier(dieType, tierIndex);
        return tierRegistry.getByIndex(eff).targetSpeedMhz();
    }

    public int targetSpeedMhz(String dieType, Tier tier) {
        return targetSpeedMhz(dieType, tier == null ? 0 : tier.index());
    }

    public Tier requirements(String dieType, int tierIndex) {
        int eff = effectiveTier(dieType, tierIndex);
        return tierRegistry.getByIndex(eff);
    }

    public Tier requirements(String dieType, Tier tier) {
        return requirements(dieType, tier == null ? 0 : tier.index());
    }

    public boolean coreCountsAvailable(String dieType, int tierIndex) {
        DieRule rule = rulesByType.get(normalize(dieType));
        if (rule == null || !rule.usesCoreCount()) return false;
        Tier tier = tierRegistry.byIndex(tierIndex).orElse(null);
        return tier != null && tier.comfortableCores() != null;
    }

    public boolean coreCountsAvailable(String dieType, Tier tier) {
        return tier == null ? false : coreCountsAvailable(dieType, tier.index());
    }

    public boolean usesCoreCount(String dieType) {
        return ruleFor(dieType).usesCoreCount();
    }

    public Integer capTier(String dieType) {
        return ruleFor(dieType).capTier();
    }

    public int firstTier(String dieType) {
        return ruleFor(dieType).firstTier();
    }

    public String availabilityTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("Die availability (rows=die, cols=tiers):\n");
        sb.append(String.format("%-10s", "die"));
        for (Tier t : tierRegistry.all()) {
            sb.append(String.format(" %4s", t.id()));
        }
        sb.append('\n');
        for (String type : DieTypes.ALL) {
            sb.append(String.format("%-10s", type));
            for (Tier t : tierRegistry.all()) {
                boolean can = canMake(type, t.index());
                sb.append(String.format(" %4s", can ? "yes" : "-"));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DieRules{");
        sb.append(sourceDescription).append(", registry=").append(tierRegistry.all().size()).append(" tiers, rules=");
        List<String> parts = new ArrayList<>();
        for (DieRule r : rulesByType.values()) {
            parts.add(r.dieType() + "(first=" + r.firstTier() + ", cap=" + r.capTier() + ", cores=" + r.usesCoreCount() + ")");
        }
        sb.append(parts).append("}");
        return sb.toString();
    }

    // ---- static factories ----

    public static List<DieRule> defaultRuleList() {
        return List.of(
            new DieRule(DieTypes.LOGIC, 1, 5, false),
            new DieRule(DieTypes.MEMORY, 2, null, false),
            new DieRule(DieTypes.COMPUTE, 3, null, true),
            new DieRule(DieTypes.NAND, 3, null, false)
        );
    }

    public static DieRules defaults(TierRegistry tierRegistry) {
        return new DieRules(tierRegistry, defaultRuleList(), "default die rules");
    }

    public static DieRules defaultRules(TierRegistry tierRegistry) {
        return defaults(tierRegistry);
    }

    public static DieRules build(TierRegistry tierRegistry, List<DieRule> rules, String sourceDescription) {
        return new DieRules(tierRegistry, rules, sourceDescription);
    }

    public static DieRules build(List<DieRule> rules, TierRegistry tierRegistry, String sourceDescription) {
        return new DieRules(rules, tierRegistry, sourceDescription);
    }

    public static DieRules build(TierRegistry tierRegistry, Map<?, ?> data, String sourceDescription) {
        if (data == null) {
            throw new IllegalArgumentException((sourceDescription == null ? "die rules" : sourceDescription) + ": no data provided");
        }
        List<DieRule> parsed = new ArrayList<>();
        for (Map.Entry<?, ?> e : data.entrySet()) {
            if (e.getValue() instanceof DieRule dr) {
                parsed.add(dr);
            } else if (e.getValue() instanceof Map<?, ?> m) {
                String dieType = String.valueOf(e.getKey());
                Object first = m.get("first_tier");
                if (first == null) first = m.get("firstTier");
                if (first == null) first = m.get("first");
                Object cap = m.get("cap_tier");
                if (cap == null) cap = m.get("capTier");
                if (cap == null) cap = m.get("cap");
                Object cores = m.get("uses_core_count");
                if (cores == null) cores = m.get("usesCoreCount");
                int firstTier = toIntScalar(first);
                Integer capTier = (cap == null) ? null : toIntScalar(cap);
                boolean uses = toBoolean(cores);
                parsed.add(new DieRule(dieType, firstTier, capTier, uses));
            } else {
                throw new IllegalArgumentException((sourceDescription == null ? "die rules" : sourceDescription) +
                    ": cannot parse die rule from " + e);
            }
        }
        return new DieRules(tierRegistry, parsed, sourceDescription);
    }

    public static DieRules build(Map<?, ?> data, TierRegistry tierRegistry, String sourceDescription) {
        return build(tierRegistry, data, sourceDescription);
    }

    public static DieRules of(TierRegistry tierRegistry, List<DieRule> rules) {
        return build(tierRegistry, rules, "die rules");
    }

    public static DieRules from(TierRegistry tierRegistry, List<DieRule> rules, String sourceDescription) {
        return build(tierRegistry, rules, sourceDescription);
    }

    public static DieRules from(List<DieRule> rules, TierRegistry tierRegistry, String sourceDescription) {
        return build(rules, tierRegistry, sourceDescription);
    }

    // ---- helpers ----

    private static String normalize(String dieType) {
        if (dieType == null) throw new IllegalArgumentException("dieType must not be null");
        String s = dieType.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) throw new IllegalArgumentException("dieType must not be blank");
        return s;
    }

    private static int toIntScalar(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) throw new IllegalArgumentException("missing numeric value");
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("yes") || s.equals("1");
    }
}
