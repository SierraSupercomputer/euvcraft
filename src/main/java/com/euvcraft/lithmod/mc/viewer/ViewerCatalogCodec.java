package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.google.gson.*;
import java.util.*;

/** Only descriptive, public strings/numbers cross the wire; never serialize live ItemStacks. */
public final class ViewerCatalogCodec {
    public static final int MAX_PAGES=1024,MAX_CHARS=200000;
    private static final Gson GSON=new Gson();
    public static String encode(List<ViewerPage> pages){
        validate(pages);String json=GSON.toJson(pages);if(json.length()>MAX_CHARS)throw new IllegalArgumentException("Viewer catalog too large");return json;
    }
    public static List<ViewerPage> decode(String json){
        if(json.length()>MAX_CHARS)throw new IllegalArgumentException("Viewer catalog too large");
        var root=JsonParser.parseString(json);if(!root.isJsonArray()||root.getAsJsonArray().size()>MAX_PAGES)throw new IllegalArgumentException("Invalid viewer catalog");
        var pages=new ArrayList<ViewerPage>();for(var element:root.getAsJsonArray())pages.add(GSON.fromJson(element,ViewerPage.class));validate(pages);return List.copyOf(pages);
    }
    private static void validate(List<ViewerPage> pages){
        if(pages.size()>MAX_PAGES)throw new IllegalArgumentException("Too many viewer pages");var ids=new HashSet<String>();for(var page:pages)if(page==null||!ViewerMachines.CONTROLLERS.containsKey(page.machine())||!ids.add(page.id()))throw new IllegalArgumentException("Invalid/duplicate viewer page");
    }
    private ViewerCatalogCodec(){}
}
