package com.euvcraft.lithmod.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable matrix mapping (tier index, support line) to the required
 * generation (G1-G3) for that line at that tier.
 */
public final class GenerationMatrix {

    public static final List<String> LINE_IDS = SupportLines.ALL;

    /** Nested alias of {@link UnmetLine}. */
    public record UnmetLine(String lineId, int requiredGeneration, int installedGeneration) {
    }

    /** Nested alias of {@link GenerationEntry}. */
    public record Entry(String lineId, int tierIndex, int generation) {
    }

    /** Container returned by various loaders/tests. */
    public record Line(String id, List<Integer> generations) {
    }

    private final TierRegistry tierRegistry;
    private final Map<Integer, Map<String, Integer>> required;
    private final String sourceDescription;

    private GenerationMatrix(TierRegistry tierRegistry,
                            Map<Integer, Map<String, Integer>> required,
                            String sourceDescription,
                            boolean internalMarker) {
        this.tierRegistry = tierRegistry;
        Map<Integer, Map<String, Integer>> copy = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> e : required.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        this.required = Collections.unmodifiableMap(copy);
        this.sourceDescription = sourceDescription == null ? "generation matrix" : sourceDescription;
    }

    /* ---------- public static build factories ---------- */

    public static GenerationMatrix build(TierRegistry tierRegistry, Map<?, ?> data, String sourceDescription) {
        return buildImpl(tierRegistry, toByLine(tierRegistry, data), sourceDescription);
    }

    public static GenerationMatrix build(Map<?, ?> data, TierRegistry tierRegistry, String sourceDescription) {
        return buildImpl(tierRegistry, toByLine(tierRegistry, data), sourceDescription);
    }

    public static GenerationMatrix build(TierRegistry tierRegistry, List<?> data, String sourceDescription) {
        return buildImpl(tierRegistry, toByLineLists(tierRegistry, data), sourceDescription);
    }

    public static GenerationMatrix build(List<?> data, TierRegistry tierRegistry, String sourceDescription) {
        return buildImpl(tierRegistry, toByLineLists(tierRegistry, data), sourceDescription);
    }

    public static GenerationMatrix from(TierRegistry tierRegistry, Map<?, ?> data, String sourceDescription) {
        return build(tierRegistry, data, sourceDescription);
    }

    public static GenerationMatrix from(Map<?, ?> data, TierRegistry tierRegistry, String sourceDescription) {
        return build(data, tierRegistry, sourceDescription);
    }

    public static GenerationMatrix from(TierRegistry tierRegistry, List<?> data, String sourceDescription) {
        return build(tierRegistry, data, sourceDescription);
    }

    public static GenerationMatrix from(List<?> data, TierRegistry tierRegistry, String sourceDescription) {
        return build(data, tierRegistry, sourceDescription);
    }

    /* ---------- constructors for compatibility ---------- */

    public GenerationMatrix(TierRegistry tierRegistry, Map<?, ?> data, String sourceDescription) {
        this(build(tierRegistry, data, sourceDescription));
    }

    public GenerationMatrix(Map<?, ?> data, TierRegistry tierRegistry, String sourceDescription) {
        this(build(data, tierRegistry, sourceDescription));
    }

    public GenerationMatrix(TierRegistry tierRegistry, List<?> data, String sourceDescription) {
        this(build(tierRegistry, data, sourceDescription));
    }

    public GenerationMatrix(List<?> data, TierRegistry tierRegistry, String sourceDescription) {
        this(build(data, tierRegistry, sourceDescription));
    }

    private GenerationMatrix(GenerationMatrix other) {
        this.tierRegistry = other.tierRegistry;
        this.required = other.required;
        this.sourceDescription = other.sourceDescription;
    }

    /* ---------- core implementation ---------- */

    private static GenerationMatrix buildImpl(TierRegistry tierRegistry,
                                            Map<String, List<Integer>> byLine,
                                            String sourceDescription) {
        Objects.requireNonNull(tierRegistry, "tierRegistry must not be null");
        Objects.requireNonNull(byLine, "generation data must not be null");
        String source = (sourceDescription == null || sourceDescription.isBlank())
            ? "generation matrix" : sourceDescription.trim();

        int tierCount = tierRegistry.all().size();
        if (tierCount == 0) {
            throw new IllegalArgumentException(source + ": tier registry is empty");
        }

        // every known line must be present
        for (String line : LINE_IDS) {
            if (!byLine.containsKey(line)) {
                throw new IllegalArgumentException(source + ": missing line '" + line + "'");
            }
        }
        // unknown lines are invalid
        for (String key : byLine.keySet()) {
            if (key == null || !LINE_IDS.contains(key)) {
                throw new IllegalArgumentException(source + ": unknown line id '" + key + "'");
            }
        }

        Map<Integer, Map<String, Integer>> byTier = new TreeMap<>();
        for (Tier tier : tierRegistry.all()) {
            byTier.put(tier.index(), new LinkedHashMap<>());
        }

        for (String line : LINE_IDS) {
            List<Integer> gens = byLine.get(line);
            if (gens == null) {
                throw new IllegalArgumentException(source + ": line '" + line + "' has no generations");
            }
            if (gens.size() != tierCount) {
                throw new IllegalArgumentException(source + ": line '" + line +
                    "' expected " + tierCount + " generations but got " + gens.size());
            }
            int previous = 0;
            int i = 0;
            for (Tier tier : tierRegistry.all()) {
                Integer gen = gens.get(i++);
                if (gen == null) {
                    throw new IllegalArgumentException(source + ": line '" + line +
                        "', tier " + tier.id() + ": missing generation");
                }
                if (gen < 1 || gen > 3) {
                    throw new IllegalArgumentException(source + ": line '" + line +
                        "', tier " + tier.id() + ": generation " + gen + " out of range [1, 3]");
                }
                if (gen < previous) {
                    throw new IllegalArgumentException(source + ": line '" + line +
                        "': generation decreases at tier " + tier.id() +
                        " (" + previous + " -> " + gen + ")");
                }
                previous = gen;
                byTier.get(tier.index()).put(line, gen);
            }
        }

        // every tier has every line (constructed above); guard anyway
        for (Tier tier : tierRegistry.all()) {
            Map<String, Integer> row = byTier.get(tier.index());
            for (String line : LINE_IDS) {
                if (row.get(line) == null) {
                    throw new IllegalArgumentException(source + ": tier " + tier.id() +
                        ": missing entry for line '" + line + "'");
                }
            }
        }

        Map<Integer, Map<String, Integer>> immutable = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> e : byTier.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return new GenerationMatrix(tierRegistry, Collections.unmodifiableMap(immutable), source, true);
    }

    /* ---------- convenience access ---------- */

    public TierRegistry tierRegistry() {
        return tierRegistry;
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public Set<String> lines() {
        return LINE_IDS instanceof Set ? (Set<String>) (Set<?>) LINE_IDS : new java.util.LinkedHashSet<>(LINE_IDS);
    }

    public List<String> lineIds() {
        return LINE_IDS;
    }

    public int requiredGeneration(String lineId, Tier tier) {
        return requiredGeneration(lineId, tier.index());
    }

    public int requiredGeneration(String lineId, int tierIndex) {
        if (lineId == null || !LINE_IDS.contains(lineId)) {
            throw new IllegalArgumentException(sourceDescription + ": unknown line id '" + lineId + "'");
        }
        Map<String, Integer> row = required.get(tierIndex);
        if (row == null) {
            throw new IllegalArgumentException(sourceDescription + ": unknown tier index " + tierIndex);
        }
        Integer v = row.get(lineId);
        if (v == null) {
            throw new IllegalArgumentException(sourceDescription + ": tier index " + tierIndex +
                ": missing entry for line '" + lineId + "'");
        }
        return v;
    }

    public boolean isSatisfied(String lineId, int installedGeneration, Tier tier) {
        return isSatisfied(lineId, installedGeneration, tier.index());
    }

    public boolean isSatisfied(String lineId, int installedGeneration, int tierIndex) {
        return installedGeneration >= requiredGeneration(lineId, tierIndex);
    }

    public List<com.euvcraft.lithmod.core.UnmetLine> unmetLines(Map<String, Integer> installedGenerations, Tier tier) {
        return unmetLines(installedGenerations, tier.index());
    }

    public List<com.euvcraft.lithmod.core.UnmetLine> unmetLines(Map<String, Integer> installedGenerations, int tierIndex) {
        List<com.euvcraft.lithmod.core.UnmetLine> result = new ArrayList<>();
        for (String line : LINE_IDS) {
            int requiredGen = requiredGeneration(line, tierIndex);
            int installed = 0;
            if (installedGenerations != null) {
                Object raw = installedGenerations.get(line);
                if (raw instanceof Number n) {
                    installed = n.intValue();
                } else if (raw != null) {
                    try {
                        installed = Integer.parseInt(String.valueOf(raw).trim());
                    } catch (NumberFormatException e) {
                        installed = 0;
                    }
                }
            }
            if (installed < requiredGen) {
                result.add(new com.euvcraft.lithmod.core.UnmetLine(line, requiredGen, installed));
            }
        }
        return List.copyOf(result);
    }

    /** Alias returning top-level UnmetLine. */
    public List<com.euvcraft.lithmod.core.UnmetLine> unmetLinesTopLevel(Map<String, Integer> installedGenerations, Tier tier) {
        List<com.euvcraft.lithmod.core.UnmetLine> out = new ArrayList<>();
        for (com.euvcraft.lithmod.core.UnmetLine u : unmetLines(installedGenerations, tier)) {
            out.add(new com.euvcraft.lithmod.core.UnmetLine(u.lineId(), u.requiredGeneration(), u.installedGeneration()));
        }
        return List.copyOf(out);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GenerationMatrix{");
        for (int idx : required.keySet()) {
            sb.append("T").append(idx).append(required.get(idx)).append(", ");
        }
        if (!required.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }

    /* ---------- conversion helpers ---------- */

    private static Map<String, List<Integer>> toByLineLists(TierRegistry tiers, List<?> data) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();

        // Determine style: line definitions vs (line,tier,generation) entries
        boolean anyLineDef = false;
        boolean anyEntry = false;
        if (data != null) {
            for (Object o : data) {
                if (o == null) continue;
                if (isLineDefinition(o)) anyLineDef = true;
                else if (isEntry(o)) anyEntry = true;
            }
        }
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("generation matrix: no data provided");
        }
        if (anyLineDef && anyEntry) {
            throw new IllegalArgumentException("generation matrix: mixed line definitions and tier entries");
        }

        if (anyLineDef) {
            for (Object o : data) {
                if (o == null) continue;
                String id = stringValue(o, "id", "line", "lineId", "line_id", "name");
                Object rawGens = invoke(o, "generations", "getGenerations", "tiers", "getTiers", "values", "getValues", "gens", "getGens");
                List<Integer> gens = (rawGens == null) ? null : toIntList(rawGens);
                if (id == null || gens == null) {
                    throw new IllegalArgumentException("generation matrix: invalid line definition " + o);
                }
                result.put(id, new ArrayList<>(gens));
            }
            return result;
        }

        // aggregate entries
        int tierCount = tiers.all().size();
        for (String line : LINE_IDS) {
            result.put(line, new ArrayList<>(Collections.nCopies(tierCount, null)));
        }
        boolean foundAny = false;
        for (Object o : data) {
            if (o == null) continue;
            String line = stringValue(o, "line", "lineId", "line_id", "id", "supportLine", "support_line");
            Integer tier = intValue(o, "tier", "tierIndex", "tier_index", "index");
            Integer gen = intValue(o, "generation", "gen", "value", "required", "requiredGeneration", "required_generation");
            if (line == null || tier == null || gen == null) {
                // maybe the whole data was a list of lines already handled; rethrow below
                throw new IllegalArgumentException("generation matrix: invalid entry " + o);
            }
            int tierIndex = normalizeTierIndex(tier, tiers);
            if (!result.containsKey(line)) {
                throw new IllegalArgumentException("generation matrix: unknown line id '" + line + "'");
            }
            result.get(line).set(tierIndex - 1, gen);
            foundAny = true;
        }
        if (!foundAny) {
            throw new IllegalArgumentException("generation matrix: no entries provided");
        }
        // reject nulls
        for (String line : LINE_IDS) {
            List<Integer> gens = result.get(line);
            for (int i = 0; i < gens.size(); i++) {
                if (gens.get(i) == null) {
                    throw new IllegalArgumentException("generation matrix: line '" + line +
                        "' missing entry for tier index " + (i + 1));
                }
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, List<Integer>> toByLine(TierRegistry tiers, Map<?, ?> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("generation matrix: no data provided");
        }
        Object firstKey = data.keySet().iterator().next();
        boolean byTier = !(firstKey instanceof String) || LINE_IDS.contains(firstKey) == false && firstKey instanceof Number;
        // A root map is byLine if its keys are (mostly) the known line ids.
        int lineKeyCount = 0;
        for (Object k : data.keySet()) {
            if (k instanceof String && LINE_IDS.contains(k)) lineKeyCount++;
        }
        boolean looksByLine = lineKeyCount > 0 || data.keySet().stream().allMatch(k -> k instanceof String);
        if (looksByLine && lineKeyCount == data.size()) {
            // by line
            Map<String, List<Integer>> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : data.entrySet()) {
                String line = String.valueOf(e.getKey());
                result.put(line, toIntList(e.getValue()));
            }
            return result;
        }

        // by tier: keys are tier indices/Tiers, values are maps line->gen
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        int tierCount = tiers.all().size();
        for (String line : LINE_IDS) {
            result.put(line, new ArrayList<>(Collections.nCopies(tierCount, null)));
        }
        for (Map.Entry<?, ?> e : data.entrySet()) {
            Object key = e.getKey();
            int rawIndex;
            if (key instanceof Tier t) rawIndex = t.index();
            else if (key instanceof Number n) rawIndex = n.intValue();
            else if (key instanceof String s) {
                Optional<Tier> ot = tiers.byId(s);
                if (ot.isPresent()) rawIndex = ot.get().index();
                else {
                    try { rawIndex = Integer.parseInt(s.trim()); }
                    catch (NumberFormatException ex) { throw new IllegalArgumentException("generation matrix: cannot resolve tier from key " + key); }
                }
            } else {
                throw new IllegalArgumentException("generation matrix: cannot resolve tier from key " + key);
            }
            int tierIndex = normalizeTierIndex(rawIndex, tiers);
            Object value = e.getValue();
            if (!(value instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("generation matrix: tier entry value must be a map: " + value);
            }
            for (Map.Entry<?, ?> le : m.entrySet()) {
                String line = String.valueOf(le.getKey());
                if (!result.containsKey(line)) {
                    throw new IllegalArgumentException("generation matrix: unknown line id '" + line + "'");
                }
                result.get(line).set(tierIndex - 1, toIntScalar(le.getValue()));
            }
        }
        for (String line : LINE_IDS) {
            for (int i = 0; i < result.get(line).size(); i++) {
                if (result.get(line).get(i) == null) {
                    throw new IllegalArgumentException("generation matrix: line '" + line +
                        "' missing entry for tier index " + (i + 1));
                }
            }
        }
        return result;
    }

    /* ---------- reflection helpers ---------- */

    private static boolean isLineDefinition(Object o) {
        if (o instanceof Map<?, ?> m) {
            return m.containsKey("generations") || m.containsKey("tiers") || m.containsKey("values") || m.containsKey("gens");
        }
        return hasMethod(o, "generations", "getGenerations", "tiers", "getTiers", "values", "getValues", "gens", "getGens")
            && hasMethod(o, "id", "getId", "line", "getLine", "lineId", "getLineId", "line_id", "name", "getName");
    }

    private static boolean isEntry(Object o) {
        if (o instanceof Map<?, ?> m) {
            boolean hasLine = m.containsKey("line") || m.containsKey("lineId") || m.containsKey("line_id")
                || m.containsKey("id") || m.containsKey("supportLine") || m.containsKey("support_line");
            boolean hasTier = m.containsKey("tier") || m.containsKey("tierIndex") || m.containsKey("tier_index")
                || m.containsKey("index");
            boolean hasGen = m.containsKey("generation") || m.containsKey("gen") || m.containsKey("value")
                || m.containsKey("required") || m.containsKey("requiredGeneration")
                || m.containsKey("required_generation");
            return hasLine && hasTier && hasGen;
        }
        boolean hasLine = hasMethod(o, "lineId", "getLineId", "line_id", "line", "getLine", "id", "getId", "supportLine", "getSupportLine");
        boolean hasTier = hasMethod(o, "tierIndex", "getTierIndex", "tier_index", "tier", "getTier", "index", "getIndex");
        boolean hasGen = hasMethod(o, "generation", "getGeneration", "gen", "getGen", "value", "getValue",
            "required", "getRequired", "requiredGeneration", "getRequiredGeneration");
        return hasLine && hasTier && hasGen;
    }

    private static String stringValue(Object o, String... keys) {
        if (o instanceof Map<?, ?> m) {
            for (String k : keys) {
                if (m.containsKey(k) && m.get(k) != null) return String.valueOf(m.get(k));
            }
            return null;
        }
        for (String name : keys) {
            Object v = invoke(o, name, getterName(name));
            if (v != null) return String.valueOf(v);
        }
        return null;
    }

    private static Integer intValue(Object o, String... keys) {
        if (o instanceof Map<?, ?> m) {
            for (String k : keys) {
                if (m.containsKey(k) && m.get(k) != null) {
                    Object v = m.get(k);
                    if (v instanceof Number n) return n.intValue();
                    try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { /*next*/ }
                }
            }
            return null;
        }
        for (String name : keys) {
            Object v = invoke(o, name, getterName(name));
            if (v != null) {
                if (v instanceof Number n) return n.intValue();
                try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { /*next*/ }
            }
        }
        return null;
    }

    private static Object invoke(Object o, String... names) {
        for (String name : names) {
            if (name == null) continue;
            try {
                Method m = o.getClass().getMethod(name);
                return m.invoke(o);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String getterName(String name) {
        if (name.startsWith("get")) return name;
        if (name.isEmpty()) return name;
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static boolean hasMethod(Object o, String... names) {
        for (String name : names) {
            try {
                o.getClass().getMethod(name);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                o.getClass().getMethod(getterName(name));
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return false;
    }

    private static List<Integer> toIntList(Object value) {
        if (value instanceof List<?> list) {
            List<Integer> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Number n) out.add(n.intValue());
                else if (o == null) out.add(null);
                else out.add(Integer.parseInt(String.valueOf(o).trim()));
            }
            return out;
        }
        if (value instanceof int[] arr) {
            List<Integer> out = new ArrayList<>();
            for (int v : arr) out.add(v);
            return out;
        }
        if (value instanceof Integer[] arr) {
            return new ArrayList<>(List.of(arr));
        }
        if (value instanceof Object[] arr) {
            List<Integer> out = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof Number n) out.add(n.intValue());
                else if (o == null) out.add(null);
                else out.add(Integer.parseInt(String.valueOf(o).trim()));
            }
            return out;
        }
        throw new IllegalArgumentException("generation matrix: expected a list of generations but got " + value);
    }

    private static int toIntScalar(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) throw new IllegalArgumentException("generation matrix: null generation");
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static Tier keyToTier(Object key, TierRegistry tiers) {
        if (key instanceof Tier t) return t;
        if (key instanceof Number n) {
            Optional<Tier> t = tiers.byIndex(n.intValue());
            if (t.isPresent()) return t.get();
        }
        if (key instanceof String s) {
            Optional<Tier> t = tiers.byId(s);
            if (t.isPresent()) return t.get();
            try {
                Optional<Tier> t2 = tiers.byIndex(Integer.parseInt(s.trim()));
                if (t2.isPresent()) return t2.get();
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("generation matrix: cannot resolve tier from key " + key);
    }

    private static int normalizeTierIndex(int index, TierRegistry tiers) {
        // support 0-based keys if they span 0..size-1
        int size = tiers.all().size();
        if (index == 0 && size > 0) {
            // ambiguous; assume 1-based not provided. But try treat as first tier.
            // Only shift if no tier 0 exists (it never does) and tier 1 exists.
            if (tiers.byIndex(0).isEmpty() && tiers.byIndex(1).isPresent()) {
                return 1;
            }
        }
        if (index < 1 || index > size) {
            throw new IllegalArgumentException("generation matrix: tier index " + index + " out of range [1, " + size + "]");
        }
        return index;
    }

    /** Accessor for an immutable view (already immutable). */
    public GenerationMatrix toImmutable() {
        return this;
    }
}
