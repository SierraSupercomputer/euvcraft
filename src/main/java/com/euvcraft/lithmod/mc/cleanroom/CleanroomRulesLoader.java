package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.core.cleanroom.CleanroomRules;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.JsonResourceDataLoader;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
public final class CleanroomRulesLoader extends JsonResourceDataLoader<Map<String,Integer>,CleanroomRules>{
 private static CleanroomRulesLoader instance;
 public CleanroomRulesLoader(){super("cleanroom",Codec.unboundedMap(Codec.STRING,Codec.INT),(entries,source)->{if(entries.size()!=1||!entries.containsKey(Identifier.fromNamespaceAndPath("lithmod","default")))throw new IllegalArgumentException("Expected cleanroom/default.json only");return decode(entries.values().iterator().next());},EUVCRAFTMod.LOGGER);instance=this;}
 public static CleanroomRules rules(){return instance!=null&&instance.current!=null?instance.current:CleanroomRules.defaults();}
 public static void register(AddServerReloadListenersEvent e){e.addListener(Identifier.fromNamespaceAndPath("lithmod","cleanroom_rules"),new CleanroomRulesLoader());}
 public static CleanroomRules decode(Map<String,Integer> m){return new CleanroomRules(v(m,"max_span"),v(m,"max_volume"),v(m,"reference_volume"),v(m,"controller_fe_per_tick"),v(m,"filter_fe_per_tick"),v(m,"filter_media_ticks"),v(m,"baseline_leak"),v(m,"ungowned_leak"),v(m,"gowned_leak"),v(m,"door_leak"),v(m,"power_loss_leak"),v(m,"breach_leak"),v(m,"filter_strength"),v(m,"outside_yield_permille"),v(m,"dirty_yield_permille"),v(m,"clean_ppm"),v(m,"dirty_ppm"));}
 private static int v(Map<String,Integer> m,String key){var n=m.get(key);if(n==null)throw new IllegalArgumentException("Missing cleanroom balance "+key);return n;}
}
