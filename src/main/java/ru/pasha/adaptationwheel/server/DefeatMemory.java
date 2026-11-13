package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.net.MetaNetwork;

/** Twenty seconds of bounded combat events; no video, auto-dodge, health changes or enemy scanning. */
public final class DefeatMemory {
    private record Attack(String owner,String key,long tick){}
    private static final class Recent {final Deque<Attack> attacks=new ArrayDeque<>();final Deque<Vec3> path=new ArrayDeque<>();long warned=-10000,trailSent=-10000;}
    private static final Map<ServerPlayer,Recent> LIVE=new WeakHashMap<>();
    private DefeatMemory(){}
    private static CompoundTag data(ServerPlayer p){var all=p.getPersistentData();if(!all.contains("adaptationwheel_meta"))all.put("adaptationwheel_meta",new CompoundTag());return all.getCompound("adaptationwheel_meta");}
    public static String attackKey(ServerPlayer p,DamageSource source){
        var precise=ModAttackCompatibility.exposure(p,source);if(precise!=null)return precise.key();
        return source.typeHolder().unwrapKey().map(k->"damage:"+k.location()).orElse("damage:minecraft:generic");
    }
    public static void contact(ServerPlayer p,DamageSource source){
        if(!AdaptationEvents.active(p)||!ru.pasha.adaptationwheel.MetaConfig.enabled(MetaMechanic.DEFEAT))return;
        source=ModAttackCompatibility.impactSource(p,source);
        if(!(source.getEntity() instanceof LivingEntity attacker)||attacker==p)return;
        String owner=ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType()).toString(),key=attackKey(p,source);
        long now=p.serverLevel().getGameTime();Recent r=LIVE.computeIfAbsent(p,x->new Recent());
        if(!r.attacks.isEmpty()&&r.attacks.getLast().tick==now&&r.attacks.getLast().key.equals(key)&&r.attacks.getLast().owner.equals(owner))return;
        while(!r.attacks.isEmpty()&&(now-r.attacks.getFirst().tick>400||r.attacks.size()>=24))r.attacks.removeFirst();
        r.attacks.addLast(new Attack(owner,key,now));
        if(!MetaServer.full(p,MetaMechanic.DEFEAT)||now-r.warned<160)return;
        List<Attack> history=r.attacks.stream().filter(a->a.owner.equals(owner)).toList();
        ListTag memories=data(p).getList("Defeats",Tag.TAG_COMPOUND);
        for(int n=0;n<memories.size();n++){
            CompoundTag saved=memories.getCompound(n);if(!owner.equals(saved.getString("Owner")))continue;
            ListTag pattern=saved.getList("Pattern",Tag.TAG_COMPOUND);int prefix=pattern.size()-1;
            if(prefix<2||history.size()<prefix)continue;boolean same=true;
            for(int i=0;i<prefix;i++){
                Attack hit=history.get(history.size()-prefix+i);if(!hit.key.equals(pattern.getCompound(i).getString("Key"))){same=false;break;}
                if(i>0){long observed=hit.tick-history.get(history.size()-prefix+i-1).tick;int expected=pattern.getCompound(i).getInt("Gap");if(expected>0&&(observed<expected*.35||observed>expected*2.5+4)){same=false;break;}}
            }
            if(same){r.warned=now;MetaNetwork.warning(p,saved.getString("Dimension"),pattern.getCompound(prefix).getString("Key"),readPath(saved),true);return;}
        }
    }
    public static void tick(ServerPlayer p){
        Recent r=LIVE.computeIfAbsent(p,x->new Recent());
        if(p.tickCount%20==0){if(r.path.size()>=20)r.path.removeFirst();r.path.addLast(p.position());}
        if(!MetaServer.active(p,MetaMechanic.DEFEAT)||AdaptationEvents.state(p).stage(MetaMechanic.DEFEAT.key)<1||p.serverLevel().getGameTime()-r.trailSent<200)return;
        ListTag list=data(p).getList("Defeats",Tag.TAG_COMPOUND);if(list.isEmpty())return;CompoundTag last=list.getCompound(0);
        List<Vec3> path=readPath(last);if(path.isEmpty()||!p.level().dimension().location().toString().equals(last.getString("Dimension"))||p.position().distanceToSqr(path.get(path.size()-1))>128*128)return;
        r.trailSent=p.serverLevel().getGameTime();MetaNetwork.warning(p,last.getString("Dimension"),"",path,false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!AdaptationEvents.equipped(p)||!ru.pasha.adaptationwheel.MetaConfig.enabled(MetaMechanic.DEFEAT))return;
        Recent r=LIVE.get(p);if(r==null||r.attacks.isEmpty())return;
        long now=p.serverLevel().getGameTime();Attack finalHit=r.attacks.getLast();
        List<Attack> attacks=r.attacks.stream().filter(a->a.owner.equals(finalHit.owner)&&now-a.tick<=400).toList();
        if(attacks.isEmpty())return;int start=Math.max(0,attacks.size()-4);
        CompoundTag memory=new CompoundTag();memory.putString("Owner",finalHit.owner);memory.putString("Dimension",p.level().dimension().location().toString());
        ListTag pattern=new ListTag();for(int i=start;i<attacks.size();i++){Attack a=attacks.get(i);CompoundTag row=new CompoundTag();row.putString("Key",a.key);row.putInt("Gap",i==start?0:(int)Math.max(1,Math.min(200,a.tick-attacks.get(i-1).tick)));pattern.add(row);}memory.put("Pattern",pattern);
        ListTag path=new ListTag();for(Vec3 v:r.path){CompoundTag point=new CompoundTag();point.putDouble("X",v.x);point.putDouble("Y",v.y);point.putDouble("Z",v.z);path.add(point);}memory.put("Path",path);
        ListTag old=data(p).getList("Defeats",Tag.TAG_COMPOUND),saved=new ListTag();saved.add(memory);for(int i=0;i<Math.min(old.size(),2);i++)saved.add(old.getCompound(i).copy());
        data(p).put("Defeats",saved);data(p).putBoolean("PendingDefeat",true);LIVE.remove(p);
    }
    private static List<Vec3> readPath(CompoundTag saved){
        List<Vec3> points=new ArrayList<>();ListTag list=saved.getList("Path",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(20,list.size());i++){CompoundTag p=list.getCompound(i);double x=p.getDouble("X"),y=p.getDouble("Y"),z=p.getDouble("Z");if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30_000_000&&Math.abs(z)<=30_000_000&&Math.abs(y)<32768)points.add(new Vec3(x,y,z));}
        return List.copyOf(points);
    }
    public static void forget(ServerPlayer p){LIVE.remove(p);}public static void clear(){LIVE.clear();}
}
