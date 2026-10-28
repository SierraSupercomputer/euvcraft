package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.network.FoupNetworkContext;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContaminatedFoupCodecTest {
    private FoupContents bad() {
        return FoupContents.of(UUID.randomUUID(),25,List.of(new Wafer(0,.42f,7)),WaferSubstrate.PURE).contaminated();
    }
    @Test void diskKeepsExactPrivateFlagsAndRolls() {
        var lot=bad();var encoded=FoupContentsCodec.CODEC.encodeStart(NbtOps.INSTANCE,lot).getOrThrow();
        assertEquals(lot,FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow());
    }
    @Test void survivalReceivesOnlyAggregateContaminationStatus() {
        var buffer=Unpooled.buffer();
        try { FoupContentsCodec.STREAM_CODEC.encode(buffer,bad());var view=FoupContentsCodec.STREAM_CODEC.decode(buffer);
            assertTrue(FoupContentsCodec.isClientView(view)); assertTrue(view.isContaminated());
            assertEquals(Wafer.NO_ROLLS,view.wafers().getFirst().cumulativeRoll());
            assertEquals(Wafer.CONTAMINATED,view.wafers().getFirst().flags());
            assertNull(FoupContentsCodec.unstartedServerLot(view));
        } finally { buffer.release(); }
    }
    @Test void creativePreservesTheWholeContaminatedLotExactly() {
        var buffer=Unpooled.buffer();var lot=bad();FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try { FoupContentsCodec.STREAM_CODEC.encode(buffer,lot);assertEquals(lot,FoupContentsCodec.STREAM_CODEC.decode(buffer)); }
        finally {buffer.release();FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);}
    }
}
