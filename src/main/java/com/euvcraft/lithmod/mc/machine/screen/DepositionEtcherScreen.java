package com.euvcraft.lithmod.mc.machine.screen;
import com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class DepositionEtcherScreen extends AdvancedMachineScreen<DepositionEtcherMenu>{
 public DepositionEtcherScreen(DepositionEtcherMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
 @Override protected boolean formed(){return menu.formed();}
}
