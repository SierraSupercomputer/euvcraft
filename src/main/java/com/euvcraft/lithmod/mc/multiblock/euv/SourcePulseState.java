package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.t7.SourcePulseCalibration;
import com.euvcraft.lithmod.core.t7.SourcePulseCalibration.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.UUID;

/** Saved source-owned calibration; no client-supplied targets, scores or payments. */
public final class SourcePulseState {
    private final EuvLightSourceControllerBlockEntity owner;
    private UUID installation = UUID.randomUUID();
    private String hardware = "";
    private long seed;
    private Controls controls = new Controls(50,50,50), tested;
    private Measurement measurement;
    private boolean accepted;
    public SourcePulseState(EuvLightSourceControllerBlockEntity owner) { this.owner=owner; }
    public Spec spec() {
        return new Spec(owner.param("pulse_control_min",0),owner.param("pulse_control_max",100),
                owner.param("pulse_timing_tolerance",18),owner.param("pulse_amplitude_tolerance",20),
                owner.param("pulse_min_quality",60),owner.param("pulse_min_multiplier_permille",850),owner.param("pulse_max_multiplier_permille",1000));
    }
    public boolean validSpec() { try { spec(); return true; } catch(IllegalArgumentException error) { return false; } }
    public UUID installation() { return installation; }
    public Controls controls() { return controls; }
    public Measurement measurement() { return measurement; }
    public boolean locked() { return owner.calibrationLocked(); }
    public void refresh() {
        if(!owner.isFormed() || !validSpec())return;
        var level=owner.getLevel();if(level==null)return;
        String key="";
        for(var pos:owner.members())if(level.getBlockEntity(pos) instanceof EuvLightSourceMemberBlockEntity member
                && member.structureKind()==BlockKind.PULSE_MODULE)key=installation+":"+member.installation()+":"+spec();
        if(key.isEmpty())return;
        if(!hardware.equals(key)) {
            hardware=key;seed=UUID.randomUUID().getLeastSignificantBits();
            int center=(spec().min()+spec().max())/2;controls=new Controls(center,center,center);
            tested=null;measurement=null;accepted=false;owner.setChanged();
        }
    }
    private Target target() { return SourcePulseCalibration.target(seed,spec()); }
    public boolean calibrated() {
        return owner.isFormed() && validSpec() && !hardware.isEmpty() && accepted && tested!=null && tested.equals(controls)
                && measurement!=null && measurement.quality()>=spec().minimumQuality();
    }
    public int multiplier() { return calibrated()?SourcePulseCalibration.multiplierPermille(spec().minimumQuality()==100?100:Math.clamp((measurement.quality()-spec().minimumQuality())*100/(100-spec().minimumQuality()),0,100),spec()):0; }
    /** 0 ready, 1 structure, 2 busy, 3 config, 4 vacuum, 5 FE, 6 hydrogen, 7 tin. */
    public int trialIssue() {
        if(!owner.isFormed())return 1;if(locked())return 2;if(!validSpec())return 3;
        if(!owner.vacuumState().canProcess(owner.vacuumSpec()))return 4;
        if(!owner.validTrialCosts())return 3;
        if(owner.buffer().getAmountAsLong()<owner.trialFe())return 5;
        if(owner.hydrogenTank.amount()<owner.trialHydrogen())return 6;
        if(owner.availableTinCredit()<owner.trialTin())return 7;
        return 0;
    }
    public boolean adjust(int axis,int delta) {
        refresh();if(!owner.isFormed()||locked()||!validSpec()||axis<0||axis>2)return false;
        int fine=Math.clamp(owner.param("pulse_fine_step",1),1,100),coarse=Math.clamp(owner.param("pulse_coarse_step",5),1,100);
        if(Math.abs(delta)!=fine&&Math.abs(delta)!=coarse)return false;
        var next=SourcePulseCalibration.clamp(new Controls(controls.phase()+(axis==0?delta:0),controls.delay()+(axis==1?delta:0),controls.amplitude()+(axis==2?delta:0)),spec());
        if(next.equals(controls))return false;controls=next;accepted=false;owner.setChanged();return true;
    }
    public boolean test() {
        refresh();if(trialIssue()!=0)return false;
        var result=SourcePulseCalibration.measure(target(),controls,spec());
        if(!owner.payTrial())return false;
        tested=controls;measurement=result;accepted=false;owner.setChanged();return true;
    }
    public boolean accept() {
        refresh();if(!owner.isFormed()||locked()||!validSpec()||tested==null||!tested.equals(controls)
                ||measurement==null||measurement.quality()<spec().minimumQuality())return false;
        accepted=true;owner.setChanged();return true;
    }
    public int value(int i) {
        return switch(i) {
            case 0 -> owner.isFormed()?1:0; case 1 -> controls.phase(); case 2 -> controls.delay(); case 3 -> controls.amplitude();
            case 4 -> measurement==null?-1:measurement.quality(); case 5 -> calibrated()?1:0;
            case 6 -> tested!=null&&tested.equals(controls)?1:0; case 7 -> multiplier(); case 8 -> locked()?1:0; case 9 -> trialIssue();
            case 10 -> measurement==null?0:measurement.responsePermille();
            case 11 -> measurement==null?0:Integer.signum(measurement.phaseError());
            case 12 -> measurement==null?0:Integer.signum(measurement.delayError());
            case 13 -> measurement==null?0:Integer.signum(measurement.amplitudeError());
            case 14 -> validSpec()?spec().min():0; case 15 -> validSpec()?spec().max():100;
            case 16 -> tested==null?50:tested.phase(); case 17 -> tested==null?50:tested.delay(); case 18 -> tested==null?50:tested.amplitude();
            case 19 -> validSpec()?spec().minimumQuality():101;
            case 20 -> Math.clamp(owner.param("pulse_fine_step",1),1,100);case 21 -> Math.clamp(owner.param("pulse_coarse_step",5),1,100);
            default -> 0;
        };
    }
    public void save(ValueOutput out) {
        out.putString("pulseInstallation",installation.toString());out.putString("pulseHardware",hardware);out.putLong("pulseSeed",seed);
        out.putInt("pulsePhase",controls.phase());out.putInt("pulseDelay",controls.delay());out.putInt("pulseAmplitude",controls.amplitude());
        out.putBoolean("pulseAccepted",accepted);out.putBoolean("pulseTested",tested!=null);
        if(tested!=null){out.putInt("pulseTestPhase",tested.phase());out.putInt("pulseTestDelay",tested.delay());out.putInt("pulseTestAmplitude",tested.amplitude());}
    }
    public void load(ValueInput in) {
        accepted=false;tested=null;measurement=null;hardware="";
        try {
            installation=UUID.fromString(in.getString("pulseInstallation").orElse(""));hardware=in.getString("pulseHardware").orElse("");seed=in.getLongOr("pulseSeed",0);
            controls=new Controls(in.getIntOr("pulsePhase",50),in.getIntOr("pulseDelay",50),in.getIntOr("pulseAmplitude",50));
            SourcePulseCalibration.measure(target(),controls,spec());
            if(in.getBooleanOr("pulseTested",false)){
                tested=new Controls(in.getIntOr("pulseTestPhase",-1),in.getIntOr("pulseTestDelay",-1),in.getIntOr("pulseTestAmplitude",-1));
                measurement=SourcePulseCalibration.measure(target(),tested,spec());
                accepted=in.getBooleanOr("pulseAccepted",false)&&tested.equals(controls)&&measurement.quality()>=spec().minimumQuality();
            }
        }catch(IllegalArgumentException error){installation=UUID.randomUUID();hardware="";accepted=false;tested=null;measurement=null;controls=new Controls(50,50,50);}
    }
}
