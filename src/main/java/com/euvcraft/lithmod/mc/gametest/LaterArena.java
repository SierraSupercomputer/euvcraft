package com.euvcraft.lithmod.mc.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;

/** Keep large new fixtures in their already-forced chunks, above baseline fixtures and each other. */
final class LaterArena extends T7RuntimeFixtures.Arena {
    private static final Map<GameTestHelper,Integer> LEVELS=new WeakHashMap<>();
    private static int next;
    private final int offset;
    LaterArena(GameTestHelper h){this(h,Direction.NORTH);}
    LaterArena(GameTestHelper h,Direction facing){super(h,facing);offset=LEVELS.computeIfAbsent(h,key->{if(next>=24)throw new IllegalStateException("Later-test isolation levels exhausted");return 32+11*next++;});}
    @Override BlockPos anchor(){return super.anchor().above(offset);}
}
