package ru.pasha.adaptationwheel.server;

import java.lang.reflect.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.entity.player.Player;

/** First Aid commits limb damage through its own event instead of LivingDamageEvent. */
public final class FirstAidCompatibility {
    private static boolean warned;
    private FirstAidCompatibility() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void partDamage(PlayerEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!event.getClass().getName().equals("ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent"))return;
        try {
            Object before=event.getClass().getMethod("getBeforeDamage").invoke(event);
            Object after=event.getClass().getMethod("getAfterDamage").invoke(event);
            if(health(before)>health(after))
                AdaptationEvents.acceptedDamage(player,(DamageSource)event.getClass().getMethod("getSource").invoke(event),health(before)-health(after));
        }catch(ReflectiveOperationException failure){report(failure);}
    }
    private static float health(Object model)throws ReflectiveOperationException {
        float total=0;
        for(Object part:(Iterable<?>)model)total+=part.getClass().getField("currentHealth").getFloat(part);
        return total;
    }
    public static void healAll(ServerPlayer player) {
        if(!ModList.get().isLoaded("firstaid"))return;
        try {
            Class<?> utils=Class.forName("ichttt.mods.firstaid.common.util.CommonUtils");
            Object model=utils.getMethod("getDamageModel",Player.class).invoke(null,player);
            if(model==null)return;
            for(Object part:(Iterable<?>)model) {
                var maximum=part.getClass().getMethod("getMaxHealth").invoke(part);
                part.getClass().getMethod("heal",float.class,Player.class,boolean.class).invoke(part,((Number)maximum).floatValue(),player,false);
            }
            model.getClass().getMethod("scheduleResync").invoke(model);
        }catch(ReflectiveOperationException failure){report(failure);}
    }
    private static void report(Exception failure) {
        if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().error("First Aid 1.1 integration could not access its damage model",failure);}
    }
}
