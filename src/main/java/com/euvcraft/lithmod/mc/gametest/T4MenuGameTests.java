package com.euvcraft.lithmod.mc.gametest;
import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;

final class T4MenuGameTests {
 static void run(GameTestHelper h){
  var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"t4-menu"),false);
  var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
  for(var kind:java.util.List.of(Kind.DEPOSITION,Kind.PACKAGING))try(var c=build(h,kind)){
   player.setPos(c.anchor().getX()+.5,c.anchor().getY()+1,c.anchor().getZ()+.5);
   var cabinet=c.machine().createMenu(1,player.getInventory(),player);
   eq(h,36,cabinet.slots.size(),"Lower cabinet exposes only player slots");
   var panel=new AdvancedOperatingPanelMenu(2,player.getInventory(),c.machine(),c.anchor());player.containerMenu=panel;
   int machineSlots=kind==Kind.DEPOSITION?3:8;
   eq(h,36+machineSlots,panel.slots.size(),"Upper panel exposes exact operational slots");check(h,panel.stillValid(player),"Actual owned upper panel is valid");
   var wire=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());wire.writeBlockPos(c.anchor().above());wire.writeBoolean(kind==Kind.DEPOSITION);
   var client=new AdvancedOperatingPanelMenu(2,player.getInventory(),wire);wire.release();
   eq(h,panel.slots.size(),client.slots.size(),"Both endpoints have identical slot topology");
   check(h,panel.getSlot(0).mayPlace(fresh(1,WaferSubstrate.PURE)),"Upper panel accepts FOUP");
   check(h,!panel.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),"FOUP slot rejects arbitrary items");
   check(h,panel.getSlot(1).mayPlace(new ItemStack(kind==Kind.DEPOSITION?ModItems.SILICON.get():ModItems.CHIP_PACKAGE.get())),"Correct reagent input");
   for(int n=2;n<machineSlots;n++)check(h,!panel.getSlot(n).mayPlace(new ItemStack(Items.DIRT)),"Output insertion refused");
   if(kind==Kind.PACKAGING){
    var machine=(AdvancedTestPackageControllerBlockEntity)c.machine();
    check(h,panel.clickMenuButton(player,1)&&machine.highBinning(),"Open owned server panel can toggle high binning");
    player.containerMenu=player.inventoryMenu;check(h,!panel.clickMenuButton(player,1)&&machine.highBinning(),"Closed panel cannot mutate mode");player.containerMenu=panel;
    machine.setSlot(0,complete(3,DieTypes.LOGIC,0));machine.setSlot(1,new ItemStack(ModItems.CHIP_PACKAGE.get(),64));replenish(machine);machine.serverTick();
    check(h,machine.isQueueActive()&&!panel.clickMenuButton(player,1)&&machine.highBinning(),"Active queue locks mode through actual menu RPC");
   }
   c.level().setBlock(c.anchor().above(),Blocks.AIR.defaultBlockState(),3);
   check(h,!panel.stillValid(player)&&!panel.clickMenuButton(player,1),"Removed panel invalidates retained menu");
  }
  player.containerMenu=player.inventoryMenu;h.succeed();
 }
 private T4MenuGameTests(){}
}
