package com.euvcraft.lithmod.mc.multiblock.g3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid="lithmod",value=Dist.CLIENT)
public final class G3ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(G3Machines.RETICLE_WRITER_MENU.get(),G3ReticleWriterScreen::new);e.register(G3Machines.DEPOSITION_ETCHER_MENU.get(),G3DepositionEtcherScreen::new);e.register(G3Machines.OPERATING_PANEL_MENU.get(),G3OperatingPanelScreen::new);e.register(G3Machines.PLANARIZER_CABINET_MENU.get(),PlanarizerCabinetScreen::new);}
}

