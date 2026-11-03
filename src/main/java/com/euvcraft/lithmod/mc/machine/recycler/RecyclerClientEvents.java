package com.euvcraft.lithmod.mc.machine.recycler;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=EUVCRAFTMod.MODID,value=Dist.CLIENT)
public final class RecyclerClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(RecyclerMachines.MENU.get(),Screen::new);}
    private static final class Screen extends SiliconMachineScreen<ScrapRecyclerMenu>{private Screen(ScrapRecyclerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}}
    private RecyclerClientEvents(){}
}
