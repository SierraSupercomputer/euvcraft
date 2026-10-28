package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T7ReticleProvenanceTest {
    @Test void reflectiveReticlesPreserveGenerationAcrossEveryCodec() {
        for(String family:java.util.List.of("compute","memory","nand")){
            var mask=new MaskData(family,7,5,3);
            assertEquals(mask,MaskDataCodec.CODEC.parse(JsonOps.INSTANCE,MaskDataCodec.CODEC.encodeStart(JsonOps.INSTANCE,mask).getOrThrow()).getOrThrow());
            assertEquals(mask,MaskDataCodec.CODEC.parse(NbtOps.INSTANCE,MaskDataCodec.CODEC.encodeStart(NbtOps.INSTANCE,mask).getOrThrow()).getOrThrow());
            var buffer=Unpooled.buffer();try{MaskDataCodec.STREAM_CODEC.encode(buffer,mask);assertEquals(mask,MaskDataCodec.STREAM_CODEC.decode(buffer));}finally{buffer.release();}
            assertEquals(3,mask.consumeUse().shopGeneration());assertEquals(4,mask.consumeUse().usesRemaining());
        }
    }
}
