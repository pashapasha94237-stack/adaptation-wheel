package ru.pasha.adaptationwheel.integration;

import java.lang.reflect.*;
import java.util.*;
import ru.pasha.adaptationwheel.core.SwordSettings;

/** Optional Configured bridge. No client or Configured classes are linked on a dedicated server. */
public final class ConfiguredMobSelectionAccess {
    public record Session(Object parent,List<String> initial,Object config,Object holder,Class<?> valueApi,
                          Object multiplierHolder,Map<String,Double> initialMultipliers,double defaultMultiplier) {
        public void stage(List<String> selected) {
            stage(selected,initialMultipliers);
        }
        public void stage(List<String> selected,Map<String,Double> multipliers) {
            try {
                List<String> copy=List.copyOf(selected);
                var validated=new SwordSettings.Values(10,2,defaultMultiplier,copy,multipliers);
                List<String> rules=SwordSettings.multiplierRules(validated.mobMultipliers());
                if(!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(holder,copy))throw new IllegalArgumentException("Invalid mob selection");
                if(!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(multiplierHolder,rules))throw new IllegalArgumentException("Invalid per-mob multipliers");
                Method setter=valueApi.getMethod("set",Object.class),getter=valueApi.getMethod("get");
                Object oldTargets=getter.invoke(holder),oldRules=getter.invoke(multiplierHolder);
                try {
                    setter.invoke(holder,new ArrayList<>(copy));setter.invoke(multiplierHolder,new ArrayList<>(rules));
                }catch(ReflectiveOperationException failure) {
                    try{setter.invoke(holder,oldTargets);setter.invoke(multiplierHolder,oldRules);}catch(ReflectiveOperationException rollback){failure.addSuppressed(rollback);}
                    throw failure;
                }
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("Unable to stage Configured mob selection",failure);}
        }
    }
    private ConfiguredMobSelectionAccess() {}
    public static Optional<Session> read(Object editor) throws ReflectiveOperationException {
        ClassLoader loader=editor.getClass().getClassLoader();
        Class<?> configApi=Class.forName("com.mrcrayfish.configured.api.IModConfig",false,loader);
        Class<?> valueApi=Class.forName("com.mrcrayfish.configured.api.IConfigValue",false,loader);
        Object config=editor.getClass().getMethod("getActiveConfig").invoke(editor);
        if(!"adaptationwheel".equals(configApi.getMethod("getModId").invoke(config))
                || (Boolean)configApi.getMethod("isReadOnly").invoke(config))return Optional.empty();
        Field holderField=editor.getClass().getDeclaredField("holder");holderField.setAccessible(true);
        Object holder=holderField.get(editor);
        String name=(String)valueApi.getMethod("getName").invoke(holder);
        String key=(String)valueApi.getMethod("getTranslationKey").invoke(holder);
        if(!("specialMobs".equals(name) && "config.adaptationwheel.sword_special_mobs".equals(key))
                && !("mobMultipliers".equals(name) && "config.adaptationwheel.sword_mob_multipliers".equals(key)))return Optional.empty();
        Field parentField=editor.getClass().getDeclaredField("parent");parentField.setAccessible(true);Object parent=parentField.get(editor);
        // ForgeConfig.getRoot() creates fresh value holders: use the exact parent's cached tree so Save sees both edits.
        Field folderField=findField(parent.getClass(),"folderEntry");folderField.setAccessible(true);Object folder=folderField.get(parent);
        Class<?> entryApi=Class.forName("com.mrcrayfish.configured.api.IConfigEntry",false,loader);
        Object targets=findValue(folder,entryApi,valueApi,"config.adaptationwheel.sword_special_mobs");
        Object overrides=findValue(folder,entryApi,valueApi,"config.adaptationwheel.sword_mob_multipliers");
        Object defaultValue=findValue(folder,entryApi,valueApi,"config.adaptationwheel.sword_cursed_multiplier");
        if(targets==null || overrides==null || defaultValue==null)throw new IllegalStateException("Configured parent is missing sword value holders");
        Object value=valueApi.getMethod("get").invoke(targets);
        if(!(value instanceof List<?> ids) || ids.stream().anyMatch(id->!(id instanceof String)))return Optional.empty();
        List<String> rules=((List<?>)valueApi.getMethod("get").invoke(overrides)).stream().map(String.class::cast).toList();
        double fallback=((Number)valueApi.getMethod("get").invoke(defaultValue)).doubleValue();
        return Optional.of(new Session(parent,ids.stream().map(String.class::cast).toList(),config,targets,valueApi,overrides,SwordSettings.parseMultipliers(rules),fallback));
    }
    private static Field findField(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{return current.getDeclaredField(name);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static Object findValue(Object entry,Class<?> entryApi,Class<?> valueApi,String key) throws ReflectiveOperationException {
        if((Boolean)entryApi.getMethod("isLeaf").invoke(entry)) {
            Object value=entryApi.getMethod("getValue").invoke(entry);
            return key.equals(valueApi.getMethod("getTranslationKey").invoke(value))?value:null;
        }
        for(Object child:(List<?>)entryApi.getMethod("getChildren").invoke(entry)) {
            Object found=findValue(child,entryApi,valueApi,key);if(found!=null)return found;
        }
        return null;
    }
}
