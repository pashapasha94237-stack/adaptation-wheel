package ru.pasha.adaptationwheel.server;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Dedicated servers also record received terrain only; never generate distant chunks. */
public final class LandscapeServerPolicy {
    private LandscapeServerPolicy(){}
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event){
        try{
            Object configs=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
            if(configs==null)return;
            Object gen=configs.getClass().getMethod("worldGenerator").invoke(configs);
            Object enabled=gen.getClass().getMethod("enableDistantWorldGeneration").invoke(gen);
            enabled.getClass().getMethod("setValue",Object.class,String.class).invoke(enabled,false,"Adaptation Wheel");
        }catch(ClassNotFoundException absent){}catch(ReflectiveOperationException failure){LogUtils.getLogger().error("Unable to disable distant terrain generation before server start",failure);}
    }
}
