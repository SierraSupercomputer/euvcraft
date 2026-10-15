package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.Tier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Codec for Tier records, used for JSON data loading.
 */
public final class TierCodec {
    private TierCodec() {}

    public static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(Tier::id),
        Codec.INT.fieldOf("index").forGetter(Tier::index),
        Codec.STRING.fieldOf("display_name").forGetter(Tier::displayName),
        Codec.STRING.fieldOf("light_source").forGetter(Tier::lightSource),
        Codec.INT.fieldOf("resolution_nm").forGetter(Tier::resolutionNm),
        Codec.INT.fieldOf("overlay_nm").forGetter(Tier::overlayNm),
        Codec.INT.fieldOf("throughput_wph").forGetter(Tier::throughputWph),
        Codec.INT.fieldOf("layers").forGetter(Tier::layers),
        Codec.INT.fieldOf("layer_ticks").forGetter(Tier::layerTicks),
        Codec.DOUBLE.fieldOf("default_yield_percent").forGetter(Tier::defaultYieldPercent),
        Codec.INT.fieldOf("target_speed_mhz").forGetter(Tier::targetSpeedMhz),
        Codec.INT.optionalFieldOf("comfortable_cores").forGetter(t -> Optional.ofNullable(t.comfortableCores())),
        Codec.BOOL.optionalFieldOf("optional", false).forGetter(Tier::optional)
    ).apply(instance, (id, index, displayName, lightSource, resolutionNm, overlayNm, throughputWph, layers, layerTicks, defaultYieldPercent, targetSpeedMhz, comfortableCores, optional) -> 
        new Tier(id, index, displayName, lightSource, resolutionNm, overlayNm, throughputWph, layers, layerTicks, defaultYieldPercent, targetSpeedMhz, comfortableCores.orElse(null), optional)
    ));

    public static final Codec<List<Tier>> LIST_CODEC = CODEC.listOf();
}
