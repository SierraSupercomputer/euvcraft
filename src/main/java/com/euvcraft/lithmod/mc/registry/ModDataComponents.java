package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MaskData;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

/**
 * Data components registered by EUVCRAFT.
 *
 * <p>One component holds a whole sealed lot. Its two codecs are the only place where
 * the difference between "stored" and "sent" is decided, so nothing else in the mod
 * has to remember which fields are hidden.</p>
 */
public final class ModDataComponents {

    /** Registry of EUVCRAFT data components. */
    public static final DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EUVCRAFTMod.MODID);

    /**
     * The sealed lot carried by a FOUP item.
     *
     * <p>{@link FoupContentsCodec#CODEC} is the persistent form and keeps the hidden
     * rolls and flags; {@link FoupContentsCodec#STREAM_CODEC} is the network form and
     * keeps only {@link FoupContents#publicView()}.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoupContents>> FOUP_CONTENTS =
        DATA_COMPONENTS.registerComponentType("foup_contents", builder -> builder
            .persistent(FoupContentsCodec.CODEC)
            .networkSynchronized(FoupContentsCodec.STREAM_CODEC));

    /**
     * The node tier a photoresist is rated for.
     *
     * <p>The tier is part of the item rather than of a stack: a resist stack can never
     * hold two tiers at once, so a resist tier that depends on the stack could only
     * ever come from the item. Any stack without this component is not a resist, which
     * is what {@link com.euvcraft.lithmod.mc.ResistCatalog#tierOf} answers with 0.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RESIST_TIER =
        DATA_COMPONENTS.registerComponentType("resist_tier", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The design a photomask item carries.
     *
     * <p>Unlike the FOUP lot this component has no hidden half, so
     * {@link MaskDataCodec#CODEC} and {@link MaskDataCodec#STREAM_CODEC} persist
     * and send the same three public values, and the tooltip of a mask can read
     * the component the client was given.</p>
     *
     * <p>A stack without this component is not a patterned mask: a bare
     * {@code mask} item has no design until something writes one, and the machine
     * has to keep rejecting that stack rather than guess a die type.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaskData>> MASK_DATA =
        DATA_COMPONENTS.registerComponentType("mask_data", builder -> builder
            .persistent(MaskDataCodec.CODEC)
            .networkSynchronized(MaskDataCodec.STREAM_CODEC));

    /**
     * The designed core count a mask-design or a produced compute chip carries.
     *
     * <p>This is a stack component, not part of the {@link MaskData} record or its codecs
     * (which are deliberately untouched): the count travels with a stack so a mask-design
     * core count frozen by the e-beam writer survives the mask item's own public codec and
     * later carries the packaged compute chip/CPU core count. A stack without this
     * component carries no designed core count; consumers default to 1 for compute dies,
     * exactly like the pre-T6 masks.</p>
     *
     * <p>Only designed compute dies carry it: a non-compute die has none, and consumer
     * admission (Process Cell, Test and Package) refuses a T6 compute count that is not
     * one of the configured {@code coreCountOptions}.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CORE_COUNT =
        DATA_COMPONENTS.registerComponentType("core_count", builder -> builder
            .persistent(Codec.intRange(1, 256))
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Narrow pickup marker: no inventory, energy or job data is copied to block loot. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CONTAMINATED_EQUIPMENT =
        DATA_COMPONENTS.registerComponentType("contaminated_equipment", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.euvcraft.lithmod.mc.routing.FoupRoutingData>> FOUP_ROUTING =
        DATA_COMPONENTS.registerComponentType("foup_routing", builder -> builder.persistent(com.euvcraft.lithmod.mc.routing.FoupRoutingData.CODEC).networkSynchronized(com.euvcraft.lithmod.mc.routing.FoupRoutingData.STREAM_CODEC));

    private static final Codec<java.util.Map<String,java.util.List<Integer>>> NEXT_STEPS_CODEC =
        Codec.unboundedMap(Codec.STRING,Codec.intRange(1,7).listOf()).comapFlatMap(map->{
            if(map.size()>8||map.entrySet().stream().anyMatch(e->!e.getKey().matches("[a-z0-9_]{1,64}")||e.getValue().size()>7||e.getValue().stream().distinct().count()!=e.getValue().size()))return com.mojang.serialization.DataResult.error(()->"Invalid next-step summary");
            var copy=new java.util.LinkedHashMap<String,java.util.List<Integer>>();map.forEach((k,v)->copy.put(k,java.util.List.copyOf(v)));return com.mojang.serialization.DataResult.success(java.util.Collections.unmodifiableMap(copy));
        },java.util.function.Function.identity());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.Map<String,java.util.List<Integer>>>> FOUP_NEXT_STEPS =
        DATA_COMPONENTS.registerComponentType("foup_next_steps", builder -> builder.persistent(NEXT_STEPS_CODEC).networkSynchronized(ByteBufCodecs.fromCodec(NEXT_STEPS_CODEC)));

    private ModDataComponents() {
    }
}