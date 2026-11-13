package ru.pasha.adaptationwheel.net;

import java.util.Set;
import ru.pasha.adaptationwheel.core.StageDurations;

/** Bounded, server-authoritative explanation; it never grants protection on the client. */
public record AnalysisInfo(String kind,int confidence,String reason,int observations,StageDurations recommendedTimings,ru.pasha.adaptationwheel.core.CombatKnowledge.Summary insight,String originMod,String originWithout) {
    /** originMod: the mod whose compatibility table decided the verdict ("" = none); originWithout: the profile name the analyzer would pick without it ("" = unknown or the same). */
    public AnalysisInfo(String kind,int confidence,String reason,int observations,StageDurations recommendedTimings,ru.pasha.adaptationwheel.core.CombatKnowledge.Summary insight){this(kind,confidence,reason,observations,recommendedTimings,insight,"","");}
    public AnalysisInfo(String kind,int confidence,String reason,int observations,StageDurations recommendedTimings){this(kind,confidence,reason,observations,recommendedTimings,ru.pasha.adaptationwheel.core.CombatKnowledge.Summary.EMPTY);}
    private static final Set<String> KINDS=Set.of("","mob","projectile","damage","effect","condition","environment");
    public static final AnalysisInfo NONE=new AnalysisInfo("",0,"",0,null);
    public AnalysisInfo {
        if(kind==null || !KINDS.contains(kind))throw new IllegalArgumentException("Invalid analysis source kind");
        if(confidence<0 || confidence>100)throw new IllegalArgumentException("Invalid analysis confidence");
        if(reason==null || reason.length()>128)throw new IllegalArgumentException("Invalid analysis reason");
        if(observations<0)throw new IllegalArgumentException("Invalid analysis observation count");
        if(!kind.isEmpty() && recommendedTimings==null)throw new IllegalArgumentException("Missing analysis timings");
        if(insight==null)insight=ru.pasha.adaptationwheel.core.CombatKnowledge.Summary.EMPTY;
        if(originMod==null)originMod="";
        if(originWithout==null)originWithout="";
        if(originMod.length()>64||originWithout.length()>32)throw new IllegalArgumentException("Invalid analysis origin");
    }
    public boolean present(){return !kind.isEmpty();}
    public boolean fromTable(){return !originMod.isEmpty();}
    public AnalysisInfo withOrigin(String mod,String without){return new AnalysisInfo(kind,confidence,reason,observations,recommendedTimings,insight,mod,without);}
}
