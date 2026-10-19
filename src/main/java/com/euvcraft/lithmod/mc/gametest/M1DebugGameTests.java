package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabDebugCommand;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.UUID;
import java.util.function.Consumer;

/** Exercises both configuration states and actual dispatch; no positive-path skips. */
public final class M1DebugGameTests {
    private static final String DEBUG_ENERGY = "m1_gate_debug_energy";
    private static final String COMMAND = "debug fab energy";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);
    static { FUNCTIONS.register(DEBUG_ENERGY, () -> M1DebugGameTests::debugEnergy); }
    private M1DebugGameTests() { }
    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((Consumer<RegisterGameTestsEvent>)M1DebugGameTests::registerTests);
    }
    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.parse("lithmod:m1_debug"),new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data=new TestData<>(env,Level.OVERWORLD,
                Identifier.withDefaultNamespace("empty"),300,0,true,Rotation.NONE,false,1,1,false,0);
        register(event,data,DEBUG_ENERGY);
    }
    private static void register(RegisterGameTestsEvent event, TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id=Identifier.fromNamespaceAndPath("lithmod",name);
        ResourceKey<Consumer<GameTestHelper>> function=ResourceKey.create(Registries.TEST_FUNCTION,id);
        event.registerTest(id,new FunctionGameTestInstance(function,data));
    }

    private static void debugEnergy(GameTestHelper h) {
        ServerLevel level=h.getLevel();
        boolean original=FabConfig.DEBUG_COMMANDS.get();
        var realDispatcher=level.getServer().getCommands().getDispatcher();
        var realRootBefore=realDispatcher.getRoot().getChild("debug");
        var realFabBefore=realRootBefore==null?null:realRootBefore.getChild("fab");
        BlockPos target=new BlockPos(2,1,4), other=new BlockPos(5,1,4), cellPos=new BlockPos(2,1,8);
        ServerPlayer player=null;
        try {
            setDebug(false);
            h.assertFalse(hasEnergy(dispatcher(level)),"Disabled registration has no fab energy branch");
            setDebug(true);
            var dispatcher=dispatcher(level);
            h.assertTrue(hasEnergy(dispatcher),"Enabled registration includes fab energy");
            player=connect(level);
            var targetMachine=placeMachine(h,target);
            var otherMachine=placeMachine(h,other);
            targetMachine.setStoredEnergy(31);
            otherMachine.setStoredEnergy(47);
            BlockPos absoluteTarget=h.absolutePos(target);
            player.setPos(absoluteTarget.getX()+0.5,absoluteTarget.getY(),absoluteTarget.getZ()-2.5);
            look(player,absoluteTarget);
            var admins=LevelBasedPermissionSet.forLevel(PermissionLevel.ADMINS);
            var admin=sourceFor(level,player,admins);
            var noPermission=sourceFor(level,player,PermissionSet.NO_PERMISSIONS);
            var gamemaster=sourceFor(level,player,LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS));
            h.assertValueEqual(0,execute(dispatcher,noPermission),"Level zero cannot execute");
            h.assertValueEqual(31,targetMachine.buffer().getAmountAsInt(),"Level zero changes no energy");
            h.assertValueEqual(0,execute(dispatcher,gamemaster),"Level two cannot execute");
            h.assertValueEqual(31,targetMachine.buffer().getAmountAsInt(),"Level two changes no energy");
            h.assertTrue(execute(dispatcher,admin)>0,"Level three executes actual targeted command");
            h.assertValueEqual(targetMachine.buffer().getCapacityAsInt(),targetMachine.buffer().getAmountAsInt(),"Target fills exactly to real capacity");
            h.assertValueEqual(47,otherMachine.buffer().getAmountAsInt(),"Untargeted machine is unchanged");

            targetMachine.setStoredEnergy(23);
            player.lookAt(EntityAnchorArgument.Anchor.EYES,player.getEyePosition().add(0,20,0));
            h.assertValueEqual(0,execute(dispatcher,admin),"Ray miss fails safely");
            h.assertValueEqual(23,targetMachine.buffer().getAmountAsInt(),"Ray miss changes no machine");
            BlockPos stone=target.offset(0,0,-1);
            h.setBlock(stone,Blocks.STONE);
            look(player,h.absolutePos(stone));
            h.assertValueEqual(0,execute(dispatcher,admin),"Non-machine target fails safely");
            h.assertValueEqual(23,targetMachine.buffer().getAmountAsInt(),"Non-machine target changes no machine");
            h.setBlock(stone,Blocks.AIR);
            var console=new CommandSourceStack(CommandSource.NULL,Vec3.atCenterOf(absoluteTarget),new Vec2(0,0),
                    level,admins,Component.literal("m1-console"),level.getServer());
            h.assertTrue(console.getPlayer()==null,"Console control has no player");
            h.assertValueEqual(0,execute(dispatcher,console),"No-player source fails safely");
            h.assertValueEqual(23,targetMachine.buffer().getAmountAsInt(),"Console failure changes no machine");

            buildCell(h,cellPos);
            ProcessCellControllerBlockEntity cell=(ProcessCellControllerBlockEntity)level.getBlockEntity(h.absolutePos(cellPos));
            cell.revalidate(); cell.serverTick();
            h.assertTrue(cell.isFormed(),"Controller fill requires a genuinely formed cell");
            h.assertTrue(cell.buffer().getCapacityAsInt()>19,"Controller has usable real capacity");
            cell.setStoredEnergy(19);
            BlockPos absoluteCell=h.absolutePos(cellPos);
            player.setPos(absoluteCell.getX()+0.5,absoluteCell.getY(),absoluteCell.getZ()-2.5);
            look(player,absoluteCell);
            h.assertTrue(execute(dispatcher,sourceFor(level,player,admins))>0,"Level three fills the looked-at formed controller");
            h.assertValueEqual(cell.buffer().getCapacityAsInt(),cell.buffer().getAmountAsInt(),"Controller fills to real capacity");
            h.assertValueEqual(23,targetMachine.buffer().getAmountAsInt(),"Controller fill does not refill previous target");
            h.assertValueEqual(47,otherMachine.buffer().getAmountAsInt(),"Controller fill leaves other machine unchanged");

            setDebug(false);
            cell.setStoredEnergy(13);
            var disabledSource=sourceFor(level,player,admins);
            h.assertTrue(dispatcher.parse(COMMAND,disabledSource).getContext().getCommand()!=null,
                    "Previously registered handler remains reachable for runtime disable control");
            h.assertValueEqual(0,execute(dispatcher,disabledSource),"Runtime disable blocks an already registered handler");
            h.assertValueEqual(13,cell.buffer().getAmountAsInt(),"Disabled handler changes no energy");
            h.assertFalse(hasEnergy(dispatcher(level)),"Fresh disabled dispatcher remains unregistered");
            h.assertTrue(realRootBefore!=null,"Actual vanilla debug root exists");
            h.assertFalse(realRootBefore.canUse(noPermission),"Actual merged root denies level zero");
            h.assertFalse(realRootBefore.canUse(gamemaster),"Actual merged root denies level two");
            h.assertTrue(realRootBefore.canUse(admin),"Actual merged root permits level three");
        } finally {
            try {
                clear(h,target); clear(h,other); clear(h,target.offset(0,0,-1));
                for(int x=0;x<2;x++) for(int y=0;y<2;y++) for(int z=0;z<3;z++) clear(h,cellPos.offset(x,y,z));
                if(player!=null) { player.closeContainer();level.getServer().getPlayerList().remove(player); }
            } finally {
                setDebug(original);
            }
        }
        var realRootAfter=realDispatcher.getRoot().getChild("debug");
        h.assertTrue(realRootAfter==realRootBefore && (realRootAfter==null?null:realRootAfter.getChild("fab"))==realFabBefore,
                "Isolated registration never mutates shared server commands");
        EUVCRAFTMod.LOGGER.info("[M1-DEBUG] disabled/enabled dispatch, levels0/2/3, target/controller, misses and runtime-disable verified");
        h.succeed();
    }

    private static void setDebug(boolean enabled) {
        FabConfig.DEBUG_COMMANDS.set(enabled);EUVCRAFTMod.config().onConfigReload();
    }
    private static CommandDispatcher<CommandSourceStack> dispatcher(ServerLevel level) {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        FabDebugCommand.register(new RegisterCommandsEvent(dispatcher,Commands.CommandSelection.ALL,
                CommandBuildContext.simple(level.registryAccess(),level.enabledFeatures())));
        return dispatcher;
    }
    private static boolean hasEnergy(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=dispatcher.getRoot().getChild("debug");
        var fab=root==null?null:root.getChild("fab");
        return fab!=null && fab.getChild("energy")!=null;
    }
    private static int execute(CommandDispatcher<CommandSourceStack> dispatcher,CommandSourceStack source) {
        try{return dispatcher.execute(COMMAND,source);}catch(CommandSyntaxException expected){return 0;}
    }
    private static CommandSourceStack sourceFor(ServerLevel level,ServerPlayer player,PermissionSet permissions) {
        return new CommandSourceStack(player.createCommandSourceStack().source,player.position(),player.getRotationVector(),
                level,permissions,level.getServer(),player);
    }
    private static void look(ServerPlayer player,BlockPos absolute) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(absolute));
    }
    private static FabMachineBlockEntity placeMachine(GameTestHelper h,BlockPos relative) {
        clear(h,relative);h.setBlock(relative,SiliconMachines.CRYSTAL_PULLER.get());
        var machine=(FabMachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(relative));
        h.assertTrue(machine!=null,"Real machine exists");machine.serverTick();
        h.assertTrue(machine.buffer().getCapacityAsInt()>47,"Real machine has positive capacity");return machine;
    }
    private static void buildCell(GameTestHelper h,BlockPos base) {
        for(int x=0;x<2;x++) for(int y=0;y<2;y++) for(int z=0;z<3;z++) {
            BlockPos pos=base.offset(x,y,z);clear(h,pos);
            if(x==0 && y==0 && z==0) h.setBlock(pos,ModMultiblocks.PROCESS_CELL_CONTROLLER.get().defaultBlockState().setValue(ProcessCellControllerBlock.FACING,Direction.NORTH));
            else if(x==1 && y==0 && z==2) h.setBlock(pos,ModMultiblocks.PROCESS_CELL_POWER_PORT.get());
            else h.setBlock(pos,ModMultiblocks.PROCESS_CELL_CASING.get());
        }
    }
    private static void clear(GameTestHelper h,BlockPos relative) {
        var be=h.getLevel().getBlockEntity(h.absolutePos(relative));
        if(be instanceof net.minecraft.world.Container inventory) inventory.clearContent();h.setBlock(relative,Blocks.AIR);
    }
    private static ServerPlayer connect(ServerLevel level) {
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"m1-debug-test"),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation()){};
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.CREATIVE);player.gameMode.setLevel(level);return player;
    }
}
