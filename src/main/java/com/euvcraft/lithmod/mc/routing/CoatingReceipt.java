package com.euvcraft.lithmod.mc.routing;

import com.euvcraft.lithmod.core.routing.LinearBudget;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** Paid, public process budget. No wafer identities, rolls or yield factors. */
public record CoatingReceipt(int tier,int layers,int passes,int resistPerLayer,int scannerTicksPerLayer,
        int coatTicks,int cleanDryTicks,int cleanWaterMb,int energyPerTick,int rinseTicks,int rinseEnergyPerTick){
    public CoatingReceipt {
        if(tier<5||tier>7||layers<1||layers>1024||passes<1||passes>8||resistPerLayer<0||resistPerLayer>64||scannerTicksPerLayer<1||scannerTicksPerLayer>72000||coatTicks<0||coatTicks>72000||cleanDryTicks<0||cleanDryTicks>72000||cleanWaterMb<0||energyPerTick<1||rinseTicks<0||rinseTicks>72000||rinseEnergyPerTick<1)throw new IllegalArgumentException("Invalid coating budget");
    }
    public CoatingReceipt(int tier,int layers,int passes,int resist,LinearBudget budget,int energy,int rinseTicks,int rinseEnergy){this(tier,layers,passes,resist,budget.scannerTicksPerLayer(),budget.coatTicks(),budget.cleanDryTicks(),budget.cleanWaterMb(),energy,rinseTicks,rinseEnergy);}
    public int totalResist(){return Math.multiplyExact(layers,resistPerLayer);}
    public int totalCleanTicks(){return Math.addExact(rinseTicks,cleanDryTicks);}
    public static final Codec<CoatingReceipt> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.intRange(5,7).fieldOf("tier").forGetter(CoatingReceipt::tier),Codec.intRange(1,1024).fieldOf("layers").forGetter(CoatingReceipt::layers),Codec.intRange(1,8).fieldOf("passes").forGetter(CoatingReceipt::passes),Codec.intRange(0,64).fieldOf("resist_per_layer").forGetter(CoatingReceipt::resistPerLayer),Codec.intRange(1,72000).fieldOf("scanner_ticks_per_layer").forGetter(CoatingReceipt::scannerTicksPerLayer),Codec.intRange(0,72000).fieldOf("coat_ticks").forGetter(CoatingReceipt::coatTicks),Codec.intRange(0,72000).fieldOf("clean_dry_ticks").forGetter(CoatingReceipt::cleanDryTicks),Codec.intRange(0,Integer.MAX_VALUE).fieldOf("clean_water_mb").forGetter(CoatingReceipt::cleanWaterMb),Codec.intRange(1,Integer.MAX_VALUE).fieldOf("energy_per_tick").forGetter(CoatingReceipt::energyPerTick),Codec.intRange(0,72000).fieldOf("rinse_ticks").forGetter(CoatingReceipt::rinseTicks),Codec.intRange(1,Integer.MAX_VALUE).fieldOf("rinse_energy_per_tick").forGetter(CoatingReceipt::rinseEnergyPerTick)
    ).apply(i,CoatingReceipt::new));
    public static final StreamCodec<ByteBuf,CoatingReceipt> STREAM_CODEC=ByteBufCodecs.fromCodec(CODEC);
}
