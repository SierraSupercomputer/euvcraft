package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.*;
import net.minecraft.resources.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;
public final class CleanroomItems {
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(EUVCRAFTMod.MODID);
 private static final ArmorMaterial GOWN=new ArmorMaterial(5,Map.of(),0,ArmorMaterials.LEATHER.equipSound(),0,0,ArmorMaterials.LEATHER.repairIngredient(),ResourceKey.create(EquipmentAssets.ROOT_ID,Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID,"cleanroom_gown")));
 public static final DeferredItem<Item> MEDIUM=ITEMS.registerSimpleItem("cleanroom_filter_medium");
 public static final DeferredItem<Item> HOOD=ITEMS.registerItem("cleanroom_hood",Item::new,p->p.humanoidArmor(GOWN,ArmorType.HELMET));
 public static final DeferredItem<Item> SUIT=ITEMS.registerItem("cleanroom_suit",Item::new,p->p.humanoidArmor(GOWN,ArmorType.CHESTPLATE));
 public static final DeferredItem<Item> TROUSERS=ITEMS.registerItem("cleanroom_trousers",Item::new,p->p.humanoidArmor(GOWN,ArmorType.LEGGINGS));
 public static final DeferredItem<Item> BOOTS=ITEMS.registerItem("cleanroom_boots",Item::new,p->p.humanoidArmor(GOWN,ArmorType.BOOTS));
 public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS=List.of(
  ITEMS.registerSimpleBlockItem("cleanroom_floor",CleanroomBlocks.FLOOR),ITEMS.registerSimpleBlockItem("cleanroom_wall",CleanroomBlocks.WALL),ITEMS.registerSimpleBlockItem("cleanroom_window",CleanroomBlocks.WINDOW),ITEMS.registerSimpleBlockItem("cleanroom_door",CleanroomBlocks.DOOR),ITEMS.registerSimpleBlockItem("cleanroom_controller",CleanroomBlocks.CONTROLLER),ITEMS.registerSimpleBlockItem("cleanroom_filter",CleanroomBlocks.FILTER),ITEMS.registerSimpleBlockItem("cleanroom_power_port",CleanroomBlocks.POWER_PORT),ITEMS.registerSimpleBlockItem("cleanroom_fluid_port",CleanroomBlocks.FLUID_PORT),ITEMS.registerSimpleBlockItem("cleanroom_item_port",CleanroomBlocks.ITEM_PORT));
 public static List<Item> all(){var out=new ArrayList<Item>();BLOCK_ITEMS.forEach(i->out.add(i.get()));for(var i:List.of(MEDIUM,HOOD,SUIT,TROUSERS,BOOTS))out.add(i.get());return List.copyOf(out);}
 private CleanroomItems(){}
}
