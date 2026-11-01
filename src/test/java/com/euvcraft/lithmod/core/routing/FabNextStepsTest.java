package com.euvcraft.lithmod.core.routing;
import static org.junit.jupiter.api.Assertions.*;
import com.euvcraft.lithmod.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FabNextStepsTest {
    private static TierRegistry tiers(){var list=new ArrayList<Tier>();for(int n=1;n<=8;n++)list.add(new Tier("T"+n,n,"Tier "+n,"src",100,10,10,n,100,90,100,n>=6?4:null,n==6));return TierRegistry.build(list,"routing test");}
    private static GenerationMatrix matrix(boolean changed){return GenerationMatrix.build(tiers(),Map.of("substrate",changed?List.of(1,1,2,3,2,3,3,3):List.of(1,1,2,2,2,3,3,3),"mask_shop",List.of(1,1,2,2,2,2,3,3),"deposition_etch",List.of(1,1,1,2,2,2,3,3),"utilities",List.of(1,1,1,1,2,2,3,3),"test_packaging",List.of(1,1,1,2,2,2,2,3)),"routing test");}
    private static Map<String,List<Integer>> next(WaferSubstrate grade,int preparation,FabProgress progress){return FabNextSteps.options(new FoupPublicView(25,1,0,grade,preparation),progress,false,matrix(false),tiers());}

    @Test void freshChoicesAreNotAssignedATier(){
        var pure=next(WaferSubstrate.PURE,1,FabProgress.initial());assertEquals(List.of(1,2,3),pure.get("process_cell_controller"));assertEquals(List.of(4,5),pure.get("deposition_etcher"));
        var electronic=next(WaferSubstrate.ELECTRONIC_GRADE,1,FabProgress.initial());assertEquals(List.of(4,5,6),electronic.get("deposition_etcher"));assertEquals(List.of(7),electronic.get("g3_deposition_etcher"));assertTrue(electronic.values().stream().flatMap(List::stream).noneMatch(t->t==8));
    }
    @Test void depositedWafersStillHaveOptionsUntilCoatingOrExposure(){
        var progress=FabProgress.initial().deposited();var pure=next(WaferSubstrate.PURE,2,progress);assertEquals(List.of(4),pure.get("lithography_scanner"));assertEquals(List.of(5),pure.get("coating_track"));
        assertEquals(Map.of("euv_scanner_controller",List.of(7)),next(WaferSubstrate.ELECTRONIC_GRADE,3,progress.coated(7)));
    }
    @Test void downstreamAdviceRequiresEtchAndCleaningBeforePackaging(){
        var exposed4=FabProgress.initial().deposited().exposed(4);assertEquals(Map.of("etch_cell",List.of(4)),next(WaferSubstrate.PURE,2,exposed4));assertEquals(Map.of("advanced_test_package",List.of(4)),next(WaferSubstrate.PURE,2,exposed4.etched()));
        var exposed7=FabProgress.initial().deposited().coated(7).exposed(7);assertEquals(Map.of("g3_etch_cell",List.of(7)),next(WaferSubstrate.ELECTRONIC_GRADE,3,exposed7));assertEquals(Map.of("wet_cleaner",List.of(7)),next(WaferSubstrate.ELECTRONIC_GRADE,3,exposed7.etched()));assertEquals(Map.of("advanced_test_package",List.of(7)),next(WaferSubstrate.ELECTRONIC_GRADE,3,exposed7.etched().cleaned()));
    }
    @Test void actualMatrixChangesAndDisposalAreRespected(){
        var lot=new FoupPublicView(25,1,0,WaferSubstrate.PURE,1);assertEquals(List.of(5),FabNextSteps.options(lot,FabProgress.initial(),false,matrix(true),tiers()).get("deposition_etcher"));
        assertTrue(FabNextSteps.options(lot,FabProgress.initial(),true,matrix(false),tiers()).isEmpty());assertEquals(Map.of("wafer_slicer",List.of()),FabNextSteps.options(FoupPublicView.empty(25),FabProgress.initial(),false,matrix(false),tiers()));
    }
}
