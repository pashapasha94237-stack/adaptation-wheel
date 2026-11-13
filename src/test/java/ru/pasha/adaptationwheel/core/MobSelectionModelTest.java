package ru.pasha.adaptationwheel.core;

import java.util.*;

public final class MobSelectionModelTest {
    public static void run() {
        List<String> initial=new ArrayList<>(List.of("minecraft:wither","missingmod:old_spirit"));
        var model=new MobSelectionModel(List.of(
            new MobSelectionModel.Entry("minecraft:wither","Иссушитель",true),
            new MobSelectionModel.Entry("minecraft:creeper","Крипер",true),
            new MobSelectionModel.Entry("minecraft:zombie","Зомби",true),
            new MobSelectionModel.Entry("examplemod:spirit","Древний дух",true)),initial);
        check(model.filtered("ИССУШ",false).get(0).id().equals("minecraft:wither"),"Localized names can be searched without entity IDs");
        check(model.filtered("examplemod",false).get(0).name().equals("Древний дух"),"Mod creatures can also be found by their namespace");
        check(model.filtered("",true).size()==2,"Selected-only view includes unavailable IDs from old configs");
        model.toggle("minecraft:creeper");model.toggle("minecraft:wither");
        check(model.selection().equals(List.of("missingmod:old_spirit","minecraft:creeper")),"Checkboxes stage exact additions and removals while preserving unseen IDs");
        check(initial.equals(List.of("minecraft:wither","missingmod:old_spirit")),"Cancelling the draft leaves the caller's original configuration unchanged");
        model.toggle("unknown:injected");check(model.selection().size()==2,"Unknown rows cannot inject identifiers");
        check(model.filtered("old_spirit",false).stream().anyMatch(e->!e.available()),"Unavailable configured entries remain visible and removable");
        boolean immutable=false;try{model.selection().clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"Returning a selection cannot expose mutable internal draft state");
        model.clear();check(model.selection().isEmpty() && model.filtered("",true).isEmpty(),"Clear yields an explicit empty selection without reintroducing defaults");
        var perMob=new MobSelectionModel(List.of(new MobSelectionModel.Entry("minecraft:wither","Иссушитель",true),
            new MobSelectionModel.Entry("minecraft:warden","Варден",true)),List.of("minecraft:wither","minecraft:warden"),Map.of("minecraft:wither",4.0));
        perMob.setMultiplierInput("minecraft:warden","6");
        check(perMob.multipliers().equals(Map.of("minecraft:wither",4.0,"minecraft:warden",6.0)),"Each selected mob has its own independent multiplier");
        perMob.setMultiplierInput("minecraft:warden","6,25");check(perMob.multipliers().get("minecraft:warden")==6.25,"Comma decimals are accepted in the localized editor");
        perMob.setMultiplierInput("minecraft:warden","NaN");check(!perMob.valid(),"Nonfinite multipliers disable confirmation");
        perMob.setMultiplierInput("minecraft:warden","1001");check(!perMob.valid(),"Values above the maximum cannot be saved");
        perMob.setMultiplierInput("minecraft:warden","");check(perMob.valid() && !perMob.multipliers().containsKey("minecraft:warden"),"Blank restores inheritance rather than silently storing a stale global value");
        perMob.setMultiplierInput("minecraft:warden","0");check(perMob.multipliers().get("minecraft:warden")==0,"Zero is a valid individual multiplier");
        perMob.toggle("minecraft:warden");check(!perMob.selected("minecraft:warden") && perMob.multipliers().get("minecraft:warden")==0,"Deselecting retains the multiplier draft without selecting its target again");
        check(!SwordSettings.validMultiplierRule("minecraft:wither=NaN") && !SwordSettings.validMultiplierRule("minecraft:warden=-1"),"Config rejects invalid rules too");
        check(SwordSettings.parseMultipliers(List.of("minecraft:wither=4","minecraft:warden=6.25")).get("minecraft:warden")==6.25,"Decimal rules decode without rounding");
        System.out.println("MobSelectionModelTest: localized search, selection, cancellation, unavailable entries, individual multipliers, inheritance and validation passed.");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
