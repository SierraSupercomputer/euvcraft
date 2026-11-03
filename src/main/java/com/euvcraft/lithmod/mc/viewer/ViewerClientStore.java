package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.core.viewer.ViewerPage;
import java.util.*;

/** Client data boundary has no JEI references, so networking works with JEI absent. */
public final class ViewerClientStore {
    private static List<ViewerPage> pages=List.of();
    private static Runnable changed=()->{};
    public static List<ViewerPage> pages(){return pages;}
    public static void accept(String json){try{replace(ViewerCatalogCodec.decode(json));}catch(RuntimeException invalid){clear();com.euvcraft.lithmod.mc.EUVCRAFTMod.LOGGER.warn("Discarded invalid recipe viewer catalog",invalid);}}
    public static void clear(){replace(List.of());}
    private static void replace(List<ViewerPage> next){if(!pages.equals(next)){pages=List.copyOf(next);changed.run();}}
    public static void listen(Runnable listener){changed=Objects.requireNonNull(listener);}
    private ViewerClientStore(){}
}
