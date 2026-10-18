package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.core.DefaultStatRules;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.LineComponents;
import com.euvcraft.lithmod.core.LineLoadout;
import com.euvcraft.lithmod.core.LineStats;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.ProcessRun;
import com.euvcraft.lithmod.core.ProcessRunRequest;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.StatRules;
import com.euvcraft.lithmod.core.SupportLines;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.ComponentCatalog;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.ModDataComponents;
import com.euvcraft.lithmod.mc.ModItems;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.data.TierCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Private persistence helper for the Process Cell controller's run state.
 * <p>
 * Provides a self-contained immutable data API for saving and restoring
 * {@link ProcessRun} snapshots with a validated frozen admission contract.
 * No controller imports, no ValueInput/ValueOutput, no CompoundTag, no reflection.
 */
public final class ProcessCellRunPersistence {

    private ProcessCellRunPersistence() {
    }

    /**
     * Frozen admission contract containing all data needed to reconstruct a
     * {@link ProcessRunRequest} without any global loaders or live definitions.
     */
    public record Contract(
            Tier tier,
            String dieType,
            int usesRemaining,
            int layerTicks,
            int resistPerLayer,
            int energyPerTick,
            LineStats lineStats,
            List<Tier> tiers,
            Map<String, List<Integer>> requiredGenerations,
            ItemStack mask,
            List<ItemStack> components,
            ItemStack resistTemplate,
            UUID lotId,
            ItemStack foupTemplate
    ) {

        private static final List<String> COMPONENT_KINDS = List.of(
                "light_source", "optics", "stage", "cleanroom", "metrology"
        );

        /**
         * Validated constructor with defensive copies and strict validation.
         */
        public Contract {
            Objects.requireNonNull(tier, "tier must not be null");
            Objects.requireNonNull(dieType, "dieType must not be null");
            Objects.requireNonNull(lineStats, "lineStats must not be null");
            Objects.requireNonNull(tiers, "tiers must not be null");
            Objects.requireNonNull(requiredGenerations, "requiredGenerations must not be null");
            Objects.requireNonNull(mask, "mask must not be null");
            Objects.requireNonNull(components, "components must not be null");
            Objects.requireNonNull(resistTemplate, "resistTemplate must not be null");
            Objects.requireNonNull(lotId, "lotId must not be null");
            Objects.requireNonNull(foupTemplate, "foupTemplate must not be null");

            // Validate tier index (M1 playable: 1 or 2)
            if (tier.index() < 1 || tier.index() > 2) {
                throw new IllegalArgumentException("tier index must be 1 or 2, got " + tier.index());
            }

            // Validate die type
            if (!DieTypes.ALL.contains(dieType)) {
                throw new IllegalArgumentException("dieType must be canonical, got " + dieType);
            }

            // Validate uses remaining
            if (usesRemaining <= 0) {
                throw new IllegalArgumentException("usesRemaining must be positive, got " + usesRemaining);
            }

            // Validate layerTicks range
            if (layerTicks < 1 || layerTicks > 72000) {
                throw new IllegalArgumentException("layerTicks must be in [1, 72000], got " + layerTicks);
            }

            // Validate resistPerLayer range
            if (resistPerLayer < 0 || resistPerLayer > 64) {
                throw new IllegalArgumentException("resistPerLayer must be in [0, 64], got " + resistPerLayer);
            }

            // Validate energyPerTick
            if (energyPerTick < 0) {
                throw new IllegalArgumentException("energyPerTick must be >= 0, got " + energyPerTick);
            }

            // Validate defect factor
            if (!Double.isFinite(lineStats.defectFactor()) || lineStats.defectFactor() < 0.0 || lineStats.defectFactor() > 1.0) {
                throw new IllegalArgumentException("defectFactor must be finite in [0.0, 1.0], got " + lineStats.defectFactor());
            }

            // Defensive copy and validate tiers list (must be exactly 8 tiers T1-T8)
            tiers = List.copyOf(tiers);
            TierRegistry tierRegistry;
            try {
                tierRegistry = TierRegistry.build(tiers, "Contract.tiers");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("tiers must form a valid 8-tier registry T1-T8: " + e.getMessage());
            }

            // Validate selected tier equals corresponding saved tier
            Optional<Tier> savedTierOpt = tierRegistry.byIndex(tier.index());
            if (savedTierOpt.isEmpty() || !savedTierOpt.get().id().equals(tier.id())) {
                throw new IllegalArgumentException("selected tier does not match saved tier at index " + tier.index());
            }

            // Defensive copy and validate requiredGenerations
            Map<String, List<Integer>> reqGenCopy = new LinkedHashMap<>();
            for (Map.Entry<String, List<Integer>> e : requiredGenerations.entrySet()) {
                String line = Objects.requireNonNull(e.getKey(), "line key must not be null");
                List<Integer> gens = e.getValue();
                if (gens == null) {
                    throw new IllegalArgumentException("generations for line '" + line + "' must not be null");
                }
                reqGenCopy.put(line, List.copyOf(gens));
            }
            requiredGenerations = Map.copyOf(reqGenCopy);

            // Validate all 5 lines present
            for (String line : SupportLines.ALL) {
                if (!requiredGenerations.containsKey(line)) {
                    throw new IllegalArgumentException("requiredGenerations missing line: " + line);
                }
                List<Integer> gens = requiredGenerations.get(line);
                if (gens.size() != 8) {
                    throw new IllegalArgumentException("requiredGenerations for line '" + line + "' must have 8 entries, got " + gens.size());
                }
                for (int i = 0; i < gens.size(); i++) {
                    Integer gen = gens.get(i);
                    if (gen == null || gen < 1 || gen > 3) {
                        throw new IllegalArgumentException("generation at tier " + (i + 1) + " for line '" + line + "' must be in [1, 3], got " + gen);
                    }
                }
            }

            // Validate mask: exact MASK item, has MASK_DATA with matching dieType/tier, positive uses
            if (!mask.is(ModItems.MASK.get())) {
                throw new IllegalArgumentException("mask must be a MASK item");
            }
            MaskData maskData = mask.get(ModDataComponents.MASK_DATA.get());
            if (maskData == null) {
                throw new IllegalArgumentException("mask must have MASK_DATA");
            }
            if (!maskData.dieType().equals(dieType)) {
                throw new IllegalArgumentException("mask dieType mismatch: contract=" + dieType + ", mask=" + maskData.dieType());
            }
            if (maskData.tierIndex() != tier.index()) {
                throw new IllegalArgumentException("mask tierIndex mismatch: contract=" + tier.index() + ", mask=" + maskData.tierIndex());
            }
            if (maskData.usesRemaining() <= 0) {
                throw new IllegalArgumentException("mask usesRemaining must be positive, got " + maskData.usesRemaining());
            }
            mask = mask.copy(); // defensive copy singleton

            // Validate components: exactly 5 in canonical order
            if (components.size() != 5) {
                throw new IllegalArgumentException("components must have exactly 5 stacks, got " + components.size());
            }
            List<ItemStack> compCopy = new java.util.ArrayList<>(5);
            for (int i = 0; i < 5; i++) {
                ItemStack stack = components.get(i);
                if (stack == null || stack.isEmpty()) {
                    throw new IllegalArgumentException("component " + i + " (" + COMPONENT_KINDS.get(i) + ") must not be empty");
                }
                Optional<String> kind = ComponentCatalog.kindOf(stack);
                if (kind.isEmpty() || !kind.get().equals(COMPONENT_KINDS.get(i))) {
                    throw new IllegalArgumentException("component " + i + " must be " + COMPONENT_KINDS.get(i) + ", got " + kind.orElse("none"));
                }
                if (ComponentCatalog.tierOf(stack) < 1 || ComponentCatalog.tierOf(stack) > 8) {
                    throw new IllegalArgumentException("component " + i + " tier must be in [1, 8], got " + ComponentCatalog.tierOf(stack));
                }
                compCopy.add(stack.copy()); // defensive copy singletons
            }
            components = List.copyOf(compCopy);

            // Validate resistTemplate: actual resist item, tier >= contract tier (matching or better)
            int resistTier = ResistCatalog.tierOf(resistTemplate);
            if (resistTier < 1 || resistTier > 8) {
                throw new IllegalArgumentException("resistTemplate must be a valid resist item with tier in [1, 8], got " + resistTier);
            }
            if (resistTier < tier.index()) {
                throw new IllegalArgumentException("resistTemplate tier " + resistTier + " is below contract tier " + tier.index());
            }
            resistTemplate = resistTemplate.copy(); // defensive copy singleton

            // Validate foupTemplate: actual FOUP item, FOUP_CONTENTS removed by caller
            if (!FoupItems.isFoup(foupTemplate)) {
                throw new IllegalArgumentException("foupTemplate must be a FOUP item");
            }
            if (FoupItems.contentsOf(foupTemplate) != null) {
                throw new IllegalArgumentException("foupTemplate must not carry FOUP_CONTENTS (caller must strip it)");
            }
            foupTemplate = foupTemplate.copy(); // defensive copy singleton

            // Validate GenerationMatrix builds and unmetLines is empty for M1 G1 map
            GenerationMatrix matrix;
            try {
                matrix = GenerationMatrix.build(tierRegistry, requiredGenerations, "Contract.requiredGenerations");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("requiredGenerations invalid: " + e.getMessage());
            }
            Map<String, Integer> m1G1Map = Map.of(
                    SupportLines.SUBSTRATE, 1,
                    SupportLines.MASK_SHOP, 1,
                    SupportLines.DEPOSITION_ETCH, 1,
                    SupportLines.UTILITIES, 1,
                    SupportLines.TEST_PACKAGING, 1
            );
            if (!matrix.unmetLines(m1G1Map, tier).isEmpty()) {
                throw new IllegalArgumentException("M1 G1 map does not satisfy requiredGenerations for tier " + tier.id());
            }

            // Validate five components via LineLoadoutReader and DefaultStatRules against stored lineStats
            LineLoadout loadout = LineLoadoutReader.read(
                    components.get(0), // light_source
                    components.get(1), // optics
                    components.get(2), // stage
                    components.get(3), // cleanroom
                    components.get(4), // metrology
                    resistTemplate,
                    maskData.tierIndex()
            );
            LineComponents lineComponents = loadout.toLineComponents();
            StatRules statRules = new DefaultStatRules(tierRegistry);
            LineStats computedStats = statRules.stats(lineComponents, tier.index());
            boolean lineCanRunTier = statRules.canRunTier(lineComponents, tier.index());

            // Validate stored lineStats matches computed (defectFactor exact match, tiers match)
            if (computedStats.resolutionNm() != lineStats.resolutionNm()
                    || computedStats.overlayNm() != lineStats.overlayNm()
                    || computedStats.throughputWph() != lineStats.throughputWph()
                    || Math.abs(computedStats.defectFactor() - lineStats.defectFactor()) > 1e-9
                    || computedStats.resolutionTier() != lineStats.resolutionTier()
                    || computedStats.overlayTier() != lineStats.overlayTier()
                    || computedStats.throughputTier() != lineStats.throughputTier()) {
                throw new IllegalArgumentException("stored lineStats does not match components + saved registry");
            }
            if (!lineCanRunTier) {
                throw new IllegalArgumentException("components cannot run tier " + tier.index());
            }
        }

        /**
         * Reconstructs a {@link ProcessRunRequest} for the given lot using only
         * frozen contract data (no global loaders, no live definitions).
         *
         * @param lot the current persisted lot (must match lotId and be non-empty)
         * @return a validated ProcessRunRequest ready for {@link ProcessRun#restore}
         * @throws IllegalArgumentException if lot does not match contract
         */
        public ProcessRunRequest request(FoupContents lot) {
            Objects.requireNonNull(lot, "lot must not be null");
            if (lot.isEmpty()) {
                throw new IllegalArgumentException("lot must not be empty");
            }
            if (!lot.lotId().equals(this.lotId)) {
                throw new IllegalArgumentException("lot UUID mismatch: contract=" + this.lotId + ", lot=" + lot.lotId());
            }

            // Reconstruct TierRegistry from saved tiers
            TierRegistry tierRegistry = TierRegistry.build(this.tiers, "Contract.request.tiers");

            // Reconstruct GenerationMatrix from saved requiredGenerations
            GenerationMatrix generationMatrix = GenerationMatrix.build(tierRegistry, this.requiredGenerations, "Contract.request.requiredGenerations");

            // Reconstruct settings: only timing/resist reconstructed; yield settings read by live FabYieldService
            FabSettings settings = FabSettings.defaults().toBuilder()
                    .resistPerLayer(this.resistPerLayer)
                    .layerTicksOverrides(Map.of(this.tier.id(), this.layerTicks))
                    .build();

            // Build installed generations map (all 5 lines at G1 for M1)
            Map<String, Integer> installedGenerations = Map.of(
                    SupportLines.SUBSTRATE, 1,
                    SupportLines.MASK_SHOP, 1,
                    SupportLines.DEPOSITION_ETCH, 1,
                    SupportLines.UTILITIES, 1,
                    SupportLines.TEST_PACKAGING, 1
            );

            // Reconstruct mask data from frozen mask stack
            MaskData maskData = this.mask.get(ModDataComponents.MASK_DATA.get());
            if (maskData == null) {
                throw new IllegalArgumentException("frozen mask missing MASK_DATA");
            }

            return new ProcessRunRequest(
                    lot,
                    this.dieType,
                    this.tier,
                    null, // cores: null for M1
                    maskData.usesRemaining(), // positive accepted uses
                    this.lineStats, // saved LineStats
                    true, // lineCanRunTier (already validated in constructor)
                    true, // dieMakeable (already validated frozen contract)
                    installedGenerations,
                    generationMatrix,
                    settings,
                    this.energyPerTick
            );
        }

        /**
         * Raw unvalidated fields record for codec decoding.
         */
        private record RawContract(
                Tier tier,
                String dieType,
                int usesRemaining,
                int layerTicks,
                int resistPerLayer,
                int energyPerTick,
                LineStats lineStats,
                List<Tier> tiers,
                Map<String, List<Integer>> requiredGenerations,
                ItemStack mask,
                List<ItemStack> components,
                ItemStack resistTemplate,
                UUID lotId,
                ItemStack foupTemplate
        ) {
        }

        /**
         * Codec for Contract, wrapping validated constructor in flatXmap.
         */
        public static final Codec<Contract> CODEC = Codec.of(
                RecordCodecBuilder.create(instance -> instance.group(
                        TierCodec.CODEC.fieldOf("tier").forGetter(RawContract::tier),
                        Codec.STRING.fieldOf("dieType").forGetter(RawContract::dieType),
                        Codec.INT.fieldOf("usesRemaining").forGetter(RawContract::usesRemaining),
                        Codec.INT.fieldOf("layerTicks").forGetter(RawContract::layerTicks),
                        Codec.INT.fieldOf("resistPerLayer").forGetter(RawContract::resistPerLayer),
                        Codec.INT.fieldOf("energyPerTick").forGetter(RawContract::energyPerTick),
                        LineStats.CODEC.fieldOf("lineStats").forGetter(RawContract::lineStats),
                        TierCodec.LIST_CODEC.fieldOf("tiers").forGetter(RawContract::tiers),
                        Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).fieldOf("requiredGenerations").forGetter(RawContract::requiredGenerations),
                        ItemStack.CODEC.fieldOf("mask").forGetter(RawContract::mask),
                        ItemStack.CODEC.listOf().fieldOf("components").forGetter(RawContract::components),
                        ItemStack.CODEC.fieldOf("resistTemplate").forGetter(RawContract::resistTemplate),
                        Codec.STRING.comapFlatMap(
                                s -> {
                                    try {
                                        return DataResult.success(UUID.fromString(s));
                                    } catch (IllegalArgumentException e) {
                                        return DataResult.error(() -> "Invalid UUID: " + s);
                                    }
                                },
                                UUID::toString
                        ).fieldOf("lotId").forGetter(RawContract::lotId),
                        ItemStack.CODEC.fieldOf("foupTemplate").forGetter(RawContract::foupTemplate)
                ).apply(instance, RawContract::new)),
                raw -> {
                    try {
                        return DataResult.success(new Contract(
                                raw.tier(), raw.dieType(), raw.usesRemaining(), raw.layerTicks(),
                                raw.resistPerLayer(), raw.energyPerTick(), raw.lineStats(),
                                raw.tiers(), raw.requiredGenerations(), raw.mask(),
                                raw.components(), raw.resistTemplate(), raw.lotId(), raw.foupTemplate()
                        ));
                    } catch (RuntimeException e) {
                        return DataResult.error(() -> "Contract validation failed: " + e.getMessage());
                    }
                },
                contract -> DataResult.success(new RawContract(
                        contract.tier(), contract.dieType(), contract.usesRemaining(), contract.layerTicks(),
                        contract.resistPerLayer(), contract.energyPerTick(), contract.lineStats(),
                        contract.tiers(), contract.requiredGenerations(), contract.mask(),
                        contract.components(), contract.resistTemplate(), contract.lotId(), contract.foupTemplate()
                ))
        );
    }

    /**
     * Saved state combining a validated Contract with a ProcessRun.Snapshot.
     */
    public record Saved(Contract contract, ProcessRun.Snapshot snapshot) {

        private static final List<String> COMPONENT_KINDS = List.of(
                "light_source", "optics", "stage", "cleanroom", "metrology"
        );

        /**
         * Validated constructor.
         */
        public Saved {
            Objects.requireNonNull(contract, "contract must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }

        /**
         * Restores a {@link ProcessRun} using the current persisted lot.
         *
         * @param lot the current lot from the FOUP slot (inventory is authoritative)
         * @return restored ProcessRun, or throws if snapshot bounds mismatch
         */
        public ProcessRun restore(FoupContents lot) {
            ProcessRunRequest request = contract.request(lot);
            return ProcessRun.restore(request, snapshot);
        }

        /**
         * Raw unvalidated fields record for codec decoding.
         */
        private record RawSaved(Contract contract, ProcessRun.Snapshot snapshot) {
        }

        /**
         * Codec for Saved, for private disk storage only (no network codec).
         */
        public static final Codec<Saved> CODEC = Codec.of(
                RecordCodecBuilder.create(instance -> instance.group(
                        Contract.CODEC.fieldOf("contract").forGetter(RawSaved::contract),
                        ProcessRun.Snapshot.CODEC.fieldOf("snapshot").forGetter(RawSaved::snapshot)
                ).apply(instance, RawSaved::new)),
                raw -> {
                    try {
                        return DataResult.success(new Saved(raw.contract(), raw.snapshot()));
                    } catch (RuntimeException e) {
                        return DataResult.error(() -> "Saved validation failed: " + e.getMessage());
                    }
                },
                saved -> DataResult.success(new RawSaved(saved.contract(), saved.snapshot()))
        );
    }

    /**
     * Seven-field codec for LineStats with validation via flatXmap.
     */
    public static final Codec<LineStats> LINE_STATS_CODEC = Codec.of(
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("resolutionNm").forGetter(LineStats::resolutionNm),
                    Codec.INT.fieldOf("overlayNm").forGetter(LineStats::overlayNm),
                    Codec.INT.fieldOf("throughputWph").forGetter(LineStats::throughputWph),
                    Codec.DOUBLE.fieldOf("defectFactor").forGetter(LineStats::defectFactor),
                    Codec.INT.fieldOf("resolutionTier").forGetter(LineStats::resolutionTier),
                    Codec.INT.fieldOf("overlayTier").forGetter(LineStats::overlayTier),
                    Codec.INT.fieldOf("throughputTier").forGetter(LineStats::throughputTier)
            ).apply(instance, LineStats::new)),
            ls -> DataResult.success(ls), // validation already in LineStats constructor
            ls -> DataResult.success(ls)
    );

    /**
     * Six-field codec for ProcessRun.Snapshot with enum string validation via flatXmap.
     */
    public static final Codec<ProcessRun.Snapshot> SNAPSHOT_CODEC = Codec.of(
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("state").forGetter(s -> s.state().name()),
                    Codec.INT.fieldOf("currentLayer").forGetter(ProcessRun.Snapshot::currentLayer),
                    Codec.INT.fieldOf("progressTicks").forGetter(ProcessRun.Snapshot::progressTicks),
                    Codec.BOOL.fieldOf("resistPaid").forGetter(ProcessRun.Snapshot::resistPaid),
                    Codec.BOOL.fieldOf("maskUseConsumed").forGetter(ProcessRun.Snapshot::maskUseConsumed),
                    Codec.BOOL.fieldOf("finishEventsEmitted").forGetter(ProcessRun.Snapshot::finishEventsEmitted)
            ).apply(instance, (stateStr, currentLayer, progressTicks, resistPaid, maskUseConsumed, finishEventsEmitted) -> {
                ProcessState state;
                try {
                    state = ProcessState.valueOf(stateStr);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid ProcessState: " + stateStr);
                }
                return new ProcessRun.Snapshot(state, currentLayer, progressTicks, resistPaid, maskUseConsumed, finishEventsEmitted);
            })),
            snapshot -> {
                try {
                    return DataResult.success(snapshot);
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "Snapshot validation failed: " + e.getMessage());
                }
            },
            snapshot -> DataResult.success(snapshot)
    );
}