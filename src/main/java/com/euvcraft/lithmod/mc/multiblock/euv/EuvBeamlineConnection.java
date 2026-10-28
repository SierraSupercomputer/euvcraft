package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/** Short, straight, loaded beamline from an exposure module to a source shell face. */
final class EuvBeamlineConnection {
    private EuvBeamlineConnection(){}
    static @Nullable EuvLightSourceControllerBlockEntity source(EuvScannerControllerBlockEntity scanner){
        var level=scanner.getLevel();if(level==null)return null;
        int limit=scanner.definition().map(d->Math.clamp(d.paramOrDefault("beamline_max_length",16),1,64)).orElse(16);
        EuvLightSourceControllerBlockEntity found=null;
        for(var pos:scanner.members()){
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity port)||port.structureKind()!=BlockKind.EXPOSURE_MODULE)continue;
            for(var direction:Direction.values())for(int distance=1;distance<=limit+1;distance++){
                var next=pos.relative(direction,distance);if(!level.hasChunkAt(next))break;
                if(level.getBlockState(next).is(EuvMachines.BEAM_LINE.get()))continue;
                if(distance>1&&level.getBlockEntity(next) instanceof EuvLightSourceMemberBlockEntity endpoint&&endpoint.structureKind()==BlockKind.CASING){
                    var source=endpoint.lightSource();
                    if(source!=null&&source.isFormed()&&source.members().contains(next)){if(found!=null&&found!=source)return null;found=source;}
                }
                break;
            }
        }
        return found;
    }
}
