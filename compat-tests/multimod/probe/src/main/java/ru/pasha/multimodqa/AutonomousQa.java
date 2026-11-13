package ru.pasha.multimodqa;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Unknown QA-mod setters exercise the native sinks; no production health hook is called directly. */
final class AutonomousQa {
    private static EntityDataAccessor<Float> HEALTH;
    private static final String GUARD=BASE+"AutonomousHealthGuard";
    private AutonomousQa(){}
    interface Writer {void write(ServerPlayer p,float target);}
    // These are deliberately separate, previously unconfigured real mod call sites.
    static void directAlpha(ServerPlayer p,float target){p.setHealth(target);}
    static void directBeta(ServerPlayer p,float target){p.setHealth(target);}
    static void trackedAlpha(ServerPlayer p,float target){p.getEntityData().set(HEALTH,target);}
    private static List<String> keys(ServerPlayer p)throws Exception{
        var result=new ArrayList<String>();for(Object row:(List<?>)getter(state(p),"snapshots")){
            String key=getter(row,"key").toString();if(key.startsWith("attack:multimodqa:health_write/"))result.add(key);
        }return result;
    }
    private static String newKey(ServerPlayer p,Set<String> previous)throws Exception{
        var added=new ArrayList<>(keys(p));added.removeAll(previous);check(added.size()==1,"Unknown direct writer must create exactly one attributed ability, got "+added);return added.get(0);
    }
    private static float writeLoss(ServerPlayer p,Writer writer,float raw)throws Exception{
        float before=health(p);writer.write(p,p.getHealth()-raw);return before-health(p);
    }
    private static void heal(ServerPlayer p)throws Exception{
        p.setHealth(p.getMaxHealth());call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);
    }
    private static void cleanBroad(ServerPlayer p)throws Exception{
        if(!ModList.get().isLoaded("firstaid"))return;
        for(Object row:(List<?>)getter(state(p),"snapshots")){
            String key=getter(row,"key").toString();check(!key.startsWith("damage:")&&!key.startsWith("mob:"),"First Aid generated duplicate broad damage adaptation for one direct setter: "+key);
        }
    }
    private static void enable(ServerPlayer p,String key)throws Exception{state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,true);}
    static void run(ServerPlayer p)throws Exception{
        reset(p);HEALTH=(EntityDataAccessor<Float>)ObfuscationReflectionHelper.findField(LivingEntity.class,"f_20961_").get(null);
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,p.server);
        try{
            source(p,"setHealth",AutonomousQa::directAlpha,true);
            source(p,"tracked DATA_HEALTH",AutonomousQa::trackedAlpha,false);
            configured(p);ordinaryDamage(p);AutonomousEffectQa.run(p);
            if(!ModList.get().isLoaded("firstaid"))nestedAndFailure(p);
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_HEALTH PASS: unknown native setters actually protected; variant firstaid={}",ModList.get().isLoaded("firstaid"));
        }finally{call(CORE+"AdaptationTimings","defaults",new Class<?>[]{});reset(p);}
    }
    private static void source(ServerPlayer p,String label,Writer writer,boolean distinct)throws Exception{
        reset(p);float baseline=writeLoss(p,writer,4);check(baseline>0,label+" baseline did not remove actual HP/limb health");wheelTick(p);
        String key=newKey(p,Set.of());check(stage(p,key)==0,label+" got a free adaptation stage");cleanBroad(p);
        Object first=required(p,key);int frozen=duration(first);double work=number(first,"work");
        check(number(first,"hits")==0&&Math.abs(number(first,"speed")-1)<1E-8,label+" first write manufactured a repeat-hit bonus");
        check(writeLoss(p,writer,4)>0,label+" repeated stage-zero write did not remove real HP");
        check(duration(required(p,key))==frozen&&number(required(p,key),"work")==work,label+" write subtracted timer work before progression");
        wheelTick(p);Object second=required(p,key);
        check(number(second,"hits")==1&&Math.abs(number(second,"speed")-1.3)<1E-8&&Math.abs(number(second,"work")-work-1.3)<1E-8,label+" delegated setter callbacks duplicated/lost the exact x1.30 acceleration");
        cleanBroad(p);learn(p,key,1);heal(p);
        float partial=writeLoss(p,writer,4);check(Math.abs(partial-baseline*.8F)<Math.max(.025F,baseline*.005F),label+" actual stage-one HP loss is not exactly 20% smaller: "+baseline+" -> "+partial);
        learn(p,key,4);heal(p);float hp=health(p),visible=p.getHealth();
        writer.write(p,0);check(p.isAlive()&&p.getHealth()==visible&&health(p)==hp,label+" full adaptation failed to block a zero-health request BEFORE death/body mutation");wheelTick(p);
        if(distinct){
            Set<String> known=new HashSet<>(keys(p));check(writeLoss(p,AutonomousQa::directBeta,4)>0,"Learning one writer incorrectly protected a different unknown method");wheelTick(p);
            String other=newKey(p,known);check(!other.equals(key)&&stage(p,other)==0,"Different writer methods collapsed to one learned immunity");
        }
        Zombie mob=new Zombie(p.serverLevel());mob.setNoAi(true);mob.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(mob);
        try{check(hit(p,p.damageSources().mobAttack(mob),4)>0,label+" full custom-write immunity blocked unrelated ordinary melee");}finally{mob.discard();}
        // Raw tracked data has no vanilla clamp. A value above max HP also makes
        // First Aid's native scaleMaxHealth baseline differ from its body model.
        // Exercise a valid healing request on both real paths instead.
        wheelTick(p);double observations=number(required(p,key),"analysisObservations");hp=health(p);writer.write(p,p.getMaxHealth());
        check(health(p)>=hp&&p.isAlive()&&p.getHealth()<=p.getMaxHealth(),label+" learned immunity blocked valid native healing or the fixture exceeded max HP");wheelTick(p);
        check(number(required(p,key),"analysisObservations")==observations,label+" healing counted as a hostile exposure");
        controlDisable(p,key);check(writeLoss(p,writer,4)>0,label+" disabled protection still blocked actual HP/limb loss");enable(p,key);
        ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        try{check(writeLoss(p,writer,4)>0,label+" protection remained active without wearing the wheel");}finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
        LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_SOURCE PASS: {} actual damage {} -> stage1 {} -> zero-health request blocked at stage4; x1.30, isolation, healing, disable/unequip",label,baseline,partial);
    }
    private static void configured(ServerPlayer p)throws Exception{
        reset(p);check(writeLoss(p,AutonomousQa::directAlpha,4)>0,"Config fixture baseline failed");wheelTick(p);String key=newKey(p,Set.of());
        Class<?> protections=Class.forName(CORE+"StageProtections");Object custom=protections.getConstructor(int.class,int.class,int.class,int.class).newInstance(30,40,70,100);
        call(CORE+"AdaptationTimings","replace",new Class<?>[]{Map.class,Map.class,int.class,protections},Map.of(),Map.of(key,List.of(5,0,0,0,0)),50,custom);
        try{
            reset(p);float baseline=writeLoss(p,AutonomousQa::directAlpha,4);wheelTick(p);Object first=required(p,key);
            check(duration(first)==100,"Unknown writer ignored the Configured five-second source override");double work=number(first,"work");
            check(writeLoss(p,AutonomousQa::directAlpha,4)>0,"Configured acceleration control did not exercise real HP loss");wheelTick(p);Object second=required(p,key);
            check(Math.abs(number(second,"speed")-1.5)<1E-8&&Math.abs(number(second,"work")-work-1.5)<1E-8&&duration(second)==100,"Unknown writer ignored configured +50% acceleration or changed the frozen clock");
            learn(p,key,1);heal(p);float partial=writeLoss(p,AutonomousQa::directAlpha,4);
            check(Math.abs(partial-baseline*.7F)<Math.max(.025F,baseline*.005F),"Unknown writer ignored configured 30% first-stage protection: "+baseline+" -> "+partial);
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_CONFIG PASS: actual unknown writer honors 5s source time, +50% hit speed and 30% first-stage real HP protection");
        }finally{call(CORE+"AdaptationTimings","defaults",new Class<?>[]{});reset(p);}
    }
    private static void ordinaryDamage(ServerPlayer p)throws Exception{
        reset(p);Zombie mob=new Zombie(p.serverLevel());mob.setNoAi(true);mob.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(mob);
        try{
            String key="mob:minecraft:zombie";float baseline=hit(p,p.damageSources().mobAttack(mob),4);check(baseline>0,"Ordinary native melee baseline failed");wheelTick(p);
            Object first=required(p,key);double work=number(first,"work");check(hit(p,p.damageSources().mobAttack(mob),4)>0,"Repeated ordinary native melee failed");wheelTick(p);Object second=required(p,key);
            check(number(second,"hits")==1&&Math.abs(number(second,"speed")-1.3)<1E-8&&Math.abs(number(second,"work")-work-1.3)<1E-8,"Autonomous guard duplicated normal damage's x1.30 receipt");
            learn(p,key,1);heal(p);float partial=hit(p,p.damageSources().mobAttack(mob),4);
            check(Math.abs(partial-baseline*.8F)<.025F,"Autonomous guard double-mitigated normal native damage: "+baseline+" -> "+partial);
            wheelTick(p);check(keys(p).isEmpty(),"Native hurt's own HP commit was falsely discovered as an unknown direct writer");
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_NATIVE_REGRESSION PASS: actual ordinary melee retains one receipt, x1.30 and exactly20% first-stage reduction without health_write adaptation");
        }finally{mob.discard();reset(p);}
    }
    /** A real server-player subclass triggers nested native tracked-data callbacks and throws inside the sink. */
    private static final class NestedPlayer extends ServerPlayer {
        private static final EntityDataAccessor<Boolean> NESTED=SynchedEntityData.defineId(NestedPlayer.class,EntityDataSerializers.BOOLEAN);
        boolean armed,throwOnce,beforeNested,afterNested;
        NestedPlayer(ServerPlayer real){super(real.server,real.serverLevel(),new GameProfile(UUID.randomUUID(),"AutoHealthQA"));}
        @Override protected void defineSynchedData(){super.defineSynchedData();getEntityData().define(NESTED,false);}
        @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor){
            super.onSyncedDataUpdated(accessor);
            if(armed&&accessor==HEALTH){
                beforeNested=writing(this);getEntityData().set(NESTED,!getEntityData().get(NESTED));afterNested=writing(this);
                if(throwOnce){throwOnce=false;throw new IllegalStateException("Intentional native health callback failure");}
            }
        }
    }
    private static boolean writing(ServerPlayer p){try{return (boolean)call(GUARD,"isWriting",new Class<?>[]{ServerPlayer.class},p);}catch(Exception e){throw new RuntimeException(e);}}
    private static int queued(ServerPlayer p)throws Exception{
        var field=Class.forName(BASE+"AdaptationEvents").getDeclaredField("RECEIVED_DAMAGE");field.setAccessible(true);
        Object value=((Map<?,?>)field.get(null)).get(p);return value==null?0:((List<?>)value).size();
    }
    private static void nestedAndFailure(ServerPlayer real)throws Exception{
        NestedPlayer p=new NestedPlayer(real);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);p.setItemSlot(EquipmentSlot.HEAD,real.getItemBySlot(EquipmentSlot.HEAD).copy());p.armed=true;
        try{
            float before=p.getHealth();trackedAlpha(p,before-4);
            check(p.getHealth()==before-4&&p.beforeNested&&p.afterNested&&!writing(p)&&queued(p)==1,"Nested same-data nonhealth callback lost/popped the outer real health scope or duplicated exposure");
            p.throwOnce=true;before=p.getHealth();boolean thrown=false;
            try{trackedAlpha(p,before-4);}catch(IllegalStateException expected){thrown=expected.getMessage().contains("Intentional native health callback failure");}
            check(thrown&&p.getHealth()==before-4&&!writing(p)&&queued(p)==2,"An exception after the real HP commit lost its measured damage receipt or leaked a health scope");
            before=p.getHealth();trackedAlpha(p,before-4);
            check(p.getHealth()==before-4&&p.beforeNested&&p.afterNested&&!writing(p)&&queued(p)==3,"A native tracked write after exception lost protection scope or receipt");
            SynchedEntityData missing=new SynchedEntityData(p);before=p.getHealth();boolean rejected=false;
            try{missing.set(HEALTH,before-4);}catch(RuntimeException expected){rejected=true;}
            check(rejected&&p.getHealth()==before&&!writing(p)&&queued(p)==3,"A native failure before the HP commit fabricated an exposure or leaked its scope");
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_NESTED_EXCEPTION PASS: real same-SynchedEntityData nested callback retains outer marker; real callback exception cleans it; subsequent real write creates exactly one receipt");
        }finally{call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);p.discard();}
    }
    static boolean clientTick(){return true;}
}
