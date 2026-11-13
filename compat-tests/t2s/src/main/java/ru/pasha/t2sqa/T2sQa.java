package ru.pasha.t2sqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

@Mod("t2sqa")
public final class T2sQa {
    private boolean requested,testing;private volatile boolean done,serverFinished;private volatile Throwable failure;private int ticks,clientWait;
    private static final String BASE="ru.pasha.adaptationwheel.server.";
    public T2sQa(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(++ticks>2400)throw new IllegalStateException("Integration test timed out");
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(done){if(failure!=null)throw new IllegalStateException("T2S integration failed",failure);mc.stop();return;}
        if(serverFinished){
            if(failure!=null)throw new IllegalStateException("T2S integration failed",failure);
            if(++clientWait<20)return;
            try{
                Object capability=Class.forName("net.mcreator.trytosurvivetwo.network.TrytosurvivetwoModVariables").getField("PLAYER_VARIABLES_CAPABILITY").get(null);
                @SuppressWarnings("unchecked") Object data=mc.player.getCapability((Capability<Object>)capability).orElseThrow(()->new AssertionError("Client capability absent"));
                number(data,"T2SFluLevel",3);number(data,"T2SFluCountdown",200);
                call("ru.pasha.adaptationwheel.client.T2SClientCompatibility","tick",new Class<?>[]{});
                check(num(data,"T2SFluLevel")==0&&num(data,"T2SFluCountdown")==0,"Client indicators must respect server immunity");
                ItemStack helmet=mc.player.getItemBySlot(EquipmentSlot.HEAD);mc.player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
                number(data,"T2SFluLevel",3);call("ru.pasha.adaptationwheel.client.T2SClientCompatibility","tick",new Class<?>[]{});
                check(num(data,"T2SFluLevel")==3,"Client mirror must stop on unequip");mc.player.setItemSlot(EquipmentSlot.HEAD,helmet);
                LogUtils.getLogger().info("T2S_WHEEL_QA CLIENT PASS: server immunity clears local disease indicators; unequip stops client protection");done=true;
            }catch(Exception e){throw new IllegalStateException(e);}return;
        }
        if(!requested&&mc.screen!=null){
            requested=true;
            GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("T2S-Wheel-Integration-"+System.currentTimeMillis(),
                new LevelSettings("T2S wheel compatibility tests",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(4215,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(!testing&&mc.player!=null&&mc.getSingleplayerServer()!=null){
            testing=true;var server=mc.getSingleplayerServer();UUID id=mc.player.getUUID();
            server.execute(()->{try{run(server.getPlayerList().getPlayer(id));}catch(Throwable e){failure=e;LogUtils.getLogger().error("T2S integration failed",e);}finally{serverFinished=true;}});
        }
    }
    private static Object call(String cls,String name,Class<?>[] types,Object...args)throws Exception{
        try{return Class.forName(cls).getMethod(name,types).invoke(null,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}
    }
    private static Object access(Object object,String method)throws Exception {Method m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);return m.invoke(object);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double num(Object vars,String field)throws Exception{return vars.getClass().getField(field).getDouble(vars);}
    private static void number(Object vars,String field,double value)throws Exception{vars.getClass().getField(field).setDouble(vars,value);}
    private static void reset(Object vars)throws Exception{Object defaults=vars.getClass().getConstructor().newInstance();for(Field f:vars.getClass().getFields())f.set(vars,f.get(defaults));}
    private static void setBad(Object vars,Object condition)throws Exception{
        for(Object metric:(List<?>)access(condition,"metrics")){
            String field=(String)access(metric,"field");double healthy=(double)access(metric,"healthy"),threshold=(double)access(metric,"threshold");boolean low=(boolean)access(metric,"low");
            number(vars,field,healthy+(low?-1:1)*(threshold+50));
        }
        for(Object flag:(List<?>)access(condition,"flags"))vars.getClass().getField((String)flag).setBoolean(vars,true);
    }
    private static boolean healthy(Object vars,Object condition)throws Exception{
        for(Object metric:(List<?>)access(condition,"metrics")){
            double value=num(vars,(String)access(metric,"field")),ref=(double)access(metric,"healthy");boolean low=(boolean)access(metric,"low");
            if(low?value<ref:value>ref)return false;
        }
        for(Object flag:(List<?>)access(condition,"flags"))if(vars.getClass().getField((String)flag).getBoolean(vars))return false;
        return true;
    }
    private static void phase(ServerPlayer p,TickEvent.Phase phase)throws Exception{call(BASE+"T2SCompatibility",phase==TickEvent.Phase.START?"start":"end",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(phase,p));}
    private static Object state(ServerPlayer p)throws Exception{return call(BASE+"AdaptationEvents","state",new Class<?>[]{ServerPlayer.class},p);}
    private static void wheelTick(ServerPlayer p)throws Exception{call(BASE+"AdaptationEvents","onTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static int stage(Object state,String key)throws Exception{return (int)state.getClass().getMethod("stage",String.class).invoke(state,key);}
    private static void advance(Object state,String key,int target)throws Exception{
        state.getClass().getMethod("hit",String.class,boolean.class).invoke(state,key,false);
        for(int i=0;i<60000&&stage(state,key)<target;i++)state.getClass().getMethod("tick").invoke(state);
        check(stage(state,key)==target,"Cannot advance "+key);
    }
    @SuppressWarnings("unchecked") private static void run(ServerPlayer p)throws Exception{
        check((boolean)call(BASE+"T2SCompatibility","available",new Class<?>[]{}),"T2S schema must load");
        Object capability=Class.forName("net.mcreator.trytosurvivetwo.network.TrytosurvivetwoModVariables").getField("PLAYER_VARIABLES_CAPABILITY").get(null);
        Object vars=p.getCapability((Capability<Object>)capability).orElseThrow(()->new AssertionError("Missing T2S capability"));
        List<?> conditions=(List<?>)call(BASE+"T2SCompatibility","conditions",new Class<?>[]{});
        check(conditions.size()==26,"Full T2S catalogue expected");
        var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","wheel"));check(item!=null,"Wheel registered");
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(item));Object state=state(p);
        Map<String,Object> byId=new HashMap<>();
        for(Object c:conditions){
            String key=(String)access(c,"key"),id=(String)access(c,"id");byId.put(id,c);
            reset(vars);p.removeAllEffects();state.getClass().getMethod("reset").invoke(state);p.tickCount+=40;
            call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);
            setBad(vars,c);phase(p,TickEvent.Phase.START);wheelTick(p);phase(p,TickEvent.Phase.END);
            check(((List<?>)state.getClass().getMethod("snapshots").invoke(state)).stream().anyMatch(s->{try{return access(s,"key").equals(key);}catch(Exception e){throw new RuntimeException(e);}}),"Missing exposure: "+id);
            advance(state,key,4);setBad(vars,c);phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);
            check(healthy(vars,c),"Full immunity failed: "+id);
            state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key,false);
            setBad(vars,c);phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);check(!healthy(vars,c),"Disabled condition was still protected: "+id);
            state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key,true);
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);check(!healthy(vars,c),"Unequipped wheel protected: "+id);
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(item));phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);check(healthy(vars,c),"Re-equipped wheel failed: "+id);
        }
        LogUtils.getLogger().info("T2S_WHEEL_QA CONDITIONS PASS: 26 sources discovered; all clear at 100%, toggles and unequip respected");
        reset(vars);state.getClass().getMethod("reset").invoke(state);p.tickCount+=40;
        String enteritis=(String)access(byId.get("enteritis"),"key");advance(state,enteritis,1);
        phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);
        number(vars,"T2SEnteritisProgression",100);phase(p,TickEvent.Phase.START);number(vars,"T2SEnteritisProgression",200);phase(p,TickEvent.Phase.END);
        check(Math.abs(num(vars,"T2SEnteritisProgression")-180)<.0001,"Stage one must reduce new progression by 20%: actual="+num(vars,"T2SEnteritisProgression"));
        phase(p,TickEvent.Phase.START);phase(p,TickEvent.Phase.END);
        check(Math.abs(num(vars,"T2SEnteritisProgression")-180)<.0001,"No repeated shrinking of unchanged disease progression");
        for(String[] test:new String[][]{{"enteritis","T2SEnteritisProceduresProcedure"},{"encephalitis","T2SEncephalitisProceduresProcedure"},{"hypothermia","T2SHypothermiaProceduresProcedure"},{"stomach_ulcer","T2SStomachUclerProceduresProcedure"},{"heatstroke","T2SStrokeProceduresProcedure"},{"brain_damage","T2SBrainDamageProceduresProcedure"},{"exhaustion","T2SExhaustionProceduresProcedure"}}){
            Object c=byId.get(test[0]);advance(state,(String)access(c,"key"),4);reset(vars);setBad(vars,c);
            Class<?> procedure=Class.forName("net.mcreator.trytosurvivetwo.procedures."+test[1]);
            Method execute=Arrays.stream(procedure.getMethods()).filter(m->m.getName().equals("execute")).findFirst().orElseThrow();
            Object[] args=new Object[execute.getParameterCount()];Class<?>[] types=execute.getParameterTypes();
            for(int i=0;i<types.length;i++)args[i]=Entity.class.isAssignableFrom(types[i])?p:types[i]==double.class?0d:p.serverLevel();
            execute.invoke(null,args);check(healthy(vars,c),"Coremod did not guard actual procedure: "+test[1]);
        }
        LogUtils.getLogger().info("T2S_WHEEL_QA PROCEDURES PASS: all seven actual disease procedures guarded; partial progression 100 -> 180 verified");
        reset(vars);p.removeAllEffects();state.getClass().getMethod("reset").invoke(state);p.tickCount+=40;
        MobEffect flu=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("trytosurvivetwo","t_2_s_flu_disease_effect"));check(flu!=null,"Flu effect exists");
        p.addEffect(new MobEffectInstance(flu,400,0));wheelTick(p);
        for(int i=0;i<30;i++)p.addEffect(new MobEffectInstance(flu,400,0));wheelTick(p);
        Object snapshot=((List<?>)state.getClass().getMethod("snapshots").invoke(state)).stream().filter(s->{try{return access(s,"key").equals("effect:trytosurvivetwo:t_2_s_flu_disease_effect");}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        check((int)access(snapshot,"hits")==0,"Refreshing the same icon must not accelerate adaptation");
        advance(state,"effect:trytosurvivetwo:t_2_s_flu_disease_effect",4);p.removeEffect(flu);
        check(!p.addEffect(new MobEffectInstance(flu,400,1)),"Adapted disease effect must be rejected");
        for(String good:new String[]{"t_2_s_cleanse","t_2_s_energy_shot","t_2_s_tranquility","t_2_s_pleasant_heat","t_2_s_immunized_effect"}){
            MobEffect effect=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("trytosurvivetwo",good));check(effect!=null,"Positive effect exists: "+good);
            check(p.addEffect(new MobEffectInstance(effect,400,0)),"Positive effect was blocked: "+good);
            check(stage(state,"effect:trytosurvivetwo:"+good)==0,"Positive effect should not adapt: "+good);
        }
        List<?> catalogue=(List<?>)call(BASE+"AdaptationCatalogue","entries",new Class<?>[]{ServerPlayer.class},p);
        for(Object c:conditions){String key=(String)access(c,"key");check(catalogue.stream().anyMatch(e->{try{return access(e,"key").equals(key);}catch(Exception ex){throw new RuntimeException(ex);}}),"Catalogue missing "+key);}
        LogUtils.getLogger().info("T2S_WHEEL_QA PASS: internal diseases, actual guarded procedures, partial protection, all 26 catalogue entries, icon refresh, beneficial effects, toggle and equipment checks passed");
        call("ru.pasha.adaptationwheel.net.WheelNetwork","sync",new Class<?>[]{ServerPlayer.class},p);
    }
}
