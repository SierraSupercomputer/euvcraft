package com.euvcraft.lithmod.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import com.euvcraft.lithmod.mc.multiblock.g3.G3Machines;
import com.euvcraft.lithmod.mc.multiblock.euv.EuvMachines;
import com.euvcraft.lithmod.mc.multiblock.hydrogen.HydrogenMachines;
import com.euvcraft.lithmod.mc.multiblock.hydrogen.HydrogenFluids;
import com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumMachines;

/** Registers the T7 families and their physical block items in one stable order. */
public final class T7Content {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(EUVCRAFTMod.MODID);
    private static final List<DeferredItem<? extends Item>> BLOCK_ITEMS=new ArrayList<>();
    private T7Content() {}
    public static void register(IEventBus bus) {
        G3Machines.register(bus); EuvMachines.register(bus);
        HydrogenFluids.register(bus); HydrogenMachines.register(bus); VacuumMachines.register(bus);
        for (var blocks : List.of(G3Machines.BLOCKS,EuvMachines.BLOCKS,HydrogenMachines.BLOCKS,VacuumMachines.BLOCKS)) {
            for (var block : blocks.getEntries()) {
                BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block.getId().getPath(),block::get));
            }
        }
        ITEMS.register(bus);
    }
    public static List<Item> blockItems() { return BLOCK_ITEMS.stream().map(entry->(Item)entry.get()).toList(); }
}
