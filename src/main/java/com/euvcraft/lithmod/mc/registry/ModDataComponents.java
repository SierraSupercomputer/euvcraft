package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.core.FoupContents;
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

    private ModDataComponents() {
    }
}