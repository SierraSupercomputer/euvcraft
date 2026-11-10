package com.euvcraft.lithmod.core.routing;

import com.euvcraft.lithmod.core.*;
import java.util.*;

/** Pure client-safe process advice. Callers provide the authoritative support matrix; no world lookup. */
public final class FabNextSteps {
    public static Map<String,List<Integer>> options(FoupPublicView lot, FabProgress progress,
            boolean contaminated, GenerationMatrix matrix, TierRegistry tiers) {
        return options(lot,progress,contaminated,matrix,tiers,Set.of(4,5,6,7,8));
    }
    public static Map<String,List<Integer>> options(FoupPublicView lot, FabProgress progress,
            boolean contaminated, GenerationMatrix matrix, TierRegistry tiers, Set<Integer> linearTiers) {
        Objects.requireNonNull(lot);Objects.requireNonNull(progress);Objects.requireNonNull(matrix);Objects.requireNonNull(tiers);
        var result=new LinkedHashMap<String,List<Integer>>();
        if(lot.isEmpty()){result.put("wafer_slicer",List.of());return Collections.unmodifiableMap(result);}
        if(contaminated)return Map.of();
        var grouped=new LinkedHashMap<String,List<Integer>>();
        for(var tier:tiers.all()){
            int index=tier.index();if(index<1||index>8)continue;
            if(progress.tier()!=0&&progress.tier()!=index)continue;
            if(lot.grade().generation()<matrix.requiredGeneration(SupportLines.SUBSTRATE,index))continue;
            int prep=matrix.requiredGeneration(SupportLines.DEPOSITION_ETCH,index);
            String next=switch(progress.stage()){
                case UNPROCESSED -> index<=3&&prep==1?"process_cell_controller":index>=4&&prep>=2&&prep<=3?(prep==3?"g3_deposition_etcher":"deposition_etcher"):null;
                case DEPOSITED -> index<4||lot.preparationGeneration()<prep?null:index==8?"t8_planarizer":!linearTiers.contains(index)?(index==7?"euv_scanner_controller":"lithography_scanner"):index==4?"lithography_scanner":"coating_track";
                case PLANARIZED -> "coating_track";
                case COATED -> lot.preparationGeneration()<prep?null:index==8?"high_na_scanner_controller":index==7?"euv_scanner_controller":"lithography_scanner";
                case EXPOSING -> index<=3?"process_cell_controller":index==8?"high_na_scanner_controller":index>=7?"euv_scanner_controller":"lithography_scanner";
                case EXPOSED -> index<=3?"test_package":!linearTiers.contains(index)?"advanced_test_package":index>=7?"g3_etch_cell":"etch_cell";
                case ETCHED -> index==4?"advanced_test_package":index==8?"t8_implanter":"wet_cleaner";
                case IMPLANTED -> "wet_cleaner";
                case CLEANED -> "advanced_test_package";
            };
            if(next!=null)grouped.computeIfAbsent(next,k->new ArrayList<>()).add(index);
        }
        grouped.forEach((k,v)->result.put(k,List.copyOf(v)));
        return Collections.unmodifiableMap(result);
    }

    private FabNextSteps(){}
}
