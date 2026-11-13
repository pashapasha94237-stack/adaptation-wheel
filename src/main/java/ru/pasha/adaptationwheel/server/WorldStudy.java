package ru.pasha.adaptationwheel.server;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.LearningConfig;
import ru.pasha.adaptationwheel.core.*;

/** Incremental inspection of loaded game definitions. Studying never earns adaptation stages. */
public final class WorldStudy {
    private static final long PLAYER_BUDGET=1_000_000L,GLOBAL_BUDGET=2_000_000L;
    private static final AdaptationProfile[] PROFILES=AdaptationProfile.values();
    private static final AtomicInteger GENERATION=new AtomicInteger(1);
    private static final Map<ServerPlayer,Cursor> CURSORS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Budget> BUDGETS=new WeakHashMap<>();
    private static final class Budget {int tick=-1;long spentNanos;}
    private static final class Cursor {
        final int generation,total;
        final Iterator<Holder.Reference<DamageType>> damage;
        final Iterator<MobEffect> effects;
        int inspected;
        Cursor(ServerPlayer p){
            generation=GENERATION.get();var registry=p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            damage=registry.holders().iterator();effects=ForgeRegistries.MOB_EFFECTS.getValues().iterator();
            total=registry.size()+ForgeRegistries.MOB_EFFECTS.getValues().size();
        }
        boolean finished(){return inspected>=total||!damage.hasNext()&&!effects.hasNext();}
    }
    private WorldStudy(){}
    public static int inspected(ServerPlayer p){var c=CURSORS.get(p);return c==null||c.generation!=GENERATION.get()?0:c.inspected;}
    public static int total(ServerPlayer p){var c=CURSORS.get(p);return c==null||c.generation!=GENERATION.get()?p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).size()+ForgeRegistries.MOB_EFFECTS.getValues().size():c.total;}
    public static boolean complete(ServerPlayer p){var c=CURSORS.get(p);return c!=null&&c.generation==GENERATION.get()&&c.finished();}
    public static int registryRevision(ServerPlayer p){return GENERATION.get();}
    public static void invalidate(ServerPlayer p){CURSORS.remove(p);}

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||p.tickCount%20!=0
                ||!AdaptationEvents.active(p)||LearningController.mode(p)!=LearningMode.RESEARCH||!LearningController.acceptWorldFacts(p))return;
        var budget=BUDGETS.computeIfAbsent(p.server,ignored->new Budget());int tick=p.server.getTickCount();
        if(budget.tick!=tick){budget.tick=tick;budget.spentNanos=0;}
        if(budget.spentNanos>=GLOBAL_BUDGET)return;
        long start=System.nanoTime();
        try{
            var cursor=CURSORS.get(p);
            if(cursor==null||cursor.generation!=GENERATION.get()){
                cursor=new Cursor(p);CURSORS.put(p,cursor);
                LearningController.worldRevision(p,cursor.generation);
            }
            int steps=LearningConfig.SPEC.isLoaded()?LearningConfig.STUDY_PER_SECOND.get():8;
            for(int n=0;n<steps&&!cursor.finished();n++){
                long elapsed=System.nanoTime()-start;
                if(elapsed>=PLAYER_BUDGET||budget.spentNanos+elapsed>=GLOBAL_BUDGET)break;
                if(cursor.damage.hasNext())damage(p,cursor.damage.next());else if(cursor.effects.hasNext())effect(p,cursor.effects.next());
                cursor.inspected++;
            }
            // After the initial study, native exposure metadata needs only an occasional refresh.
            // Read at most two already-loaded blocks; do not include other mods' tick work in our budget.
            long elapsed=System.nanoTime()-start;
            if((!cursor.finished()||p.tickCount%100==0)&&elapsed<PLAYER_BUDGET&&budget.spentNanos+elapsed<GLOBAL_BUDGET)environment(p);
        }finally{budget.spentNanos+=Math.max(0,System.nanoTime()-start);}
    }
    private static void learn(ServerPlayer p,String key,AdaptationProfile profile,int confidence,String source){
        LearningController.learnFact(p,new ResearchMemory.Fact(key,profile,confidence,source));
    }
    private static void damage(ServerPlayer p,Holder<DamageType> type){
        if(DamageCategories.excluded(type))return;
        String key=type.unwrapKey().map(k->"damage:"+k.location()).orElse("");if(key.isEmpty())return;
        for(var profile:PROFILES)if(type.is(DamageCategories.profileTag(profile))){learn(p,key,profile,94,"world:datapack/category");return;}
        AdaptationProfile profile=null;
        if(type.is(DamageTypeTags.IS_LIGHTNING))profile=AdaptationProfile.ELECTRIC;
        else if(type.is(DamageTypeTags.IS_EXPLOSION))profile=AdaptationProfile.EXPLOSION;
        else if(type.is(DamageTypeTags.IS_FIRE))profile=AdaptationProfile.FIRE;
        else if(type.is(DamageTypeTags.IS_FREEZING))profile=AdaptationProfile.COLD;
        else if(type.is(DamageTypeTags.IS_DROWNING))profile=AdaptationProfile.SUFFOCATION;
        else if(type.is(DamageTypeTags.IS_FALL))profile=AdaptationProfile.PHYSICAL;
        else profile=DamageCategories.vanillaProfile(type);
        if(profile!=null){learn(p,key,profile,92,"world:registry/vanilla-category");return;}
        if(type.is(DamageTypes.MOB_ATTACK)||type.is(DamageTypes.MOB_ATTACK_NO_AGGRO)||type.is(DamageTypes.PLAYER_ATTACK)){
            learn(p,key,AdaptationProfile.PHYSICAL,92,"world:registry/melee");return;
        }
        // The author's category tag is metadata. It is kept weaker than native observations.
        var tagged=AdaptationAnalysis.strongest(DamageCategories.tagProfiles(type));
        if(tagged!=null){learn(p,key,tagged,78,"world:datapack/mod-category");return;}
        if(type.is(DamageTypeTags.IS_PROJECTILE))learn(p,key,AdaptationProfile.PROJECTILE,88,"world:registry/projectile");
        // Unmarked IDs and names alone are not factual training labels.
    }
    private static void effect(ServerPlayer p,MobEffect effect){
        if(!DamageCategories.isHarmful(effect))return;
        var id=ForgeRegistries.MOB_EFFECTS.getKey(effect);if(id==null)return;
        AdaptationProfile profile=effect==MobEffects.POISON?AdaptationProfile.POISON:
                effect==MobEffects.WITHER||effect==MobEffects.HARM?AdaptationProfile.MAGIC:
                effect==MobEffects.HUNGER?AdaptationProfile.HUNGER:
                effect==MobEffects.BLINDNESS||effect==MobEffects.DARKNESS?AdaptationProfile.DARKNESS:AdaptationProfile.EFFECT;
        if(profile==AdaptationProfile.EFFECT){
            // "Harmful" is all the registry says about a mod's effect: it must not outrank the effect's name or a prepared fact.
            var known=LearningController.memory(p).fact("effect:"+id);
            if(DamageCategories.effectProfile(effect)!=AdaptationProfile.EFFECT||known!=null&&known.source().startsWith("seed:"))return;
        }
        learn(p,"effect:"+id,profile,84,"world:registry/harmful-effect-properties");
    }
    private static void environment(ServerPlayer p){
        if(p.getTicksFrozen()>0)learn(p,"damage:minecraft:freeze",AdaptationProfile.COLD,90,"world:observed/freezing");
        if(p.isUnderWater())learn(p,"damage:minecraft:drown",AdaptationProfile.SUFFOCATION,90,"world:observed/underwater");
        if(p.isOnFire())learn(p,"damage:minecraft:on_fire",AdaptationProfile.FIRE,90,"world:observed/burning");
        for(BlockPos pos:List.of(p.blockPosition(),p.blockPosition().below())){
            if(!p.serverLevel().hasChunkAt(pos))continue;
            var state=p.serverLevel().getBlockState(pos);
            if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE))learn(p,"damage:minecraft:in_fire",AdaptationProfile.FIRE,90,"world:observed/fire-block");
            if(p.serverLevel().getFluidState(pos).is(Fluids.LAVA))learn(p,"damage:minecraft:lava",AdaptationProfile.FIRE,90,"world:observed/lava");
        }
    }
    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event){
        if(event.getUpdateCause()==TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)GENERATION.incrementAndGet();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)CURSORS.remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){CURSORS.clear();BUDGETS.clear();GENERATION.incrementAndGet();}
}
