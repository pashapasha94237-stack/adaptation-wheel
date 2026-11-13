package ru.pasha.adaptationwheel.client;

import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.server.T2SCompatibility;

/** Keeps T2S's client-side calculations and indicators consistent with server-granted immunity. */
public final class T2SClientCompatibility {
    private T2SClientCompatibility(){}
    public static void tick(){
        var player=Minecraft.getInstance().player;
        if(player==null||!player.getItemBySlot(EquipmentSlot.HEAD).is(AdaptationWheel.WHEEL.get()))return;
        var state=ClientWheelState.get(player.getId());
        if(state==null||!state.enabled()||!state.equipped())return;
        var immune=new HashSet<String>();
        for(var entry:state.entries())if(entry.enabled()&&entry.reduction()==100)immune.add(entry.key());
        T2SCompatibility.mirrorClient(player,immune);
    }
}
