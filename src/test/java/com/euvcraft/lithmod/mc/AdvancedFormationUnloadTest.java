package com.euvcraft.lithmod.mc;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdvancedFormationUnloadTest {
 @Test void unloadingNeverResolvesTheOwnerOrWorldAndIsIdempotent(){
  var owner=new AdvancedOwner(){
   public FabMachineBlockEntity machine(){throw new AssertionError("Chunk removal must not resolve the owner/world");}
   public AdvancedFormation formation(){throw new AssertionError("No revalidation during removal");}
   public boolean large(){throw new AssertionError("No structure scanning during removal");}
  };
  var formation=new AdvancedFormation(owner);
  assertDoesNotThrow(formation::unload);assertDoesNotThrow(formation::unload);
  assertFalse(formation.formed());assertTrue(formation.members().isEmpty());assertEquals(0,formation.data().get(0));
 }
}
