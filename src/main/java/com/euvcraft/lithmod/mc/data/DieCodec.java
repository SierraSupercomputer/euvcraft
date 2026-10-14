package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRule;
import com.mojang.serialization.Codec;
import java.util.List;

public final class DieCodec {
    private DieCodec() {}
    public static final Codec<DieRule> CODEC = DieRuleCodec.CODEC;
    public static final Codec<List<DieRule>> LIST_CODEC = DieRuleCodec.LIST_CODEC;
}
