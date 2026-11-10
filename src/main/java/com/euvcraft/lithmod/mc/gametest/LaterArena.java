package com.euvcraft.lithmod.mc.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;

/** Keep large new fixtures in their already-forced chunks, above baseline fixtures and each other. */
final class LaterArena extends T7RuntimeFixtures.Arena {
    private static final BitSet OCCUPIED = new BitSet();
    private final int slot;
    LaterArena(GameTestHelper h){this(h,Direction.NORTH);}
    LaterArena(GameTestHelper h,Direction facing){
        super(h,facing);
        slot=OCCUPIED.nextClearBit(0);
        if(slot>=24)throw new IllegalStateException("Later-test isolation levels exhausted");
        OCCUPIED.set(slot);
    }
    @Override BlockPos anchor(){return super.anchor().above(32+11*slot);}
    @Override public void close(){
        try{super.close();}finally{OCCUPIED.clear(slot);}
    }
}
