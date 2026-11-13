package ru.pasha.jjcqa;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

@Mod("jjcqa")
public final class JjcQa {
    private static final String BASE="ru.pasha.adaptationwheel.server.",PROC="net.mcreator.jujutsucraft.procedures.";
    private boolean requested,testing;private volatile boolean finished;private volatile Throwable error;
    private int ticks,pageTicks,page;private Screen screen;
    private static final String[] SCREENS={"SelectProfessionScreen","SelectTechniqueScreen","SelectTechnique2Screen"};
    public JjcQa(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(++ticks>3600)throw new IllegalStateException("JJC QA timed out");
        if(mc.getOverlay()!=null)return;GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try {
            if(error!=null)throw new IllegalStateException("Server integration failed",error);
            if(!requested&&mc.screen!=null){
                verifyLanguage();requested=true;GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("JJC-QA-"+System.currentTimeMillis(),
                    new LevelSettings("Jujutsu Craft integration tests",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7781,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(!testing&&mc.player!=null&&mc.getSingleplayerServer()!=null){
                testing=true;var server=mc.getSingleplayerServer();UUID uuid=mc.player.getUUID();
                server.execute(()->{try{run(server.getPlayerList().getPlayer(uuid));}catch(Throwable e){error=e;LogUtils.getLogger().error("JJC_QA FAIL",e);}finally{finished=true;}});return;
            }
            if(!finished||mc.player==null)return;
            if(page>=SCREENS.length){
                mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;
                LogUtils.getLogger().info("JJC_WHEEL_QA PASS: Russian resources, dynamic text, 3 GUI screens, 259 catalogue entries, actual coremod target guards, effects, damage, toggles and projectile attribution");mc.stop();return;
            }
            if(screen==null){
                Class<?> cls=Class.forName("net.mcreator.jujutsucraft.client.gui."+SCREENS[page]);var constructor=cls.getConstructors()[0];
                Object menu=constructor.getParameterTypes()[0].getConstructor(int.class,Inventory.class,FriendlyByteBuf.class).newInstance(0,mc.player.getInventory(),null);
                mc.player.containerMenu=(AbstractContainerMenu)menu;screen=(Screen)constructor.newInstance(menu,mc.player.getInventory(),Component.empty());mc.setScreen(screen);
            }
            if(++pageTicks==8)Screenshot.grab(mc.gameDirectory,SCREENS[page]+"-ru.png",mc.getMainRenderTarget(),message->{});
            if(pageTicks>=12){mc.setScreen(null);screen=null;pageTicks=0;page++;}
        }catch(Exception e){throw new IllegalStateException("JJC QA failed",e);}
    }
    private static Object call(String cls,String name,Class<?>[] types,Object...args)throws Exception{
        try{return Class.forName(cls).getMethod(name,types).invoke(null,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}
    }
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    private static Object state(ServerPlayer p)throws Exception{return call(BASE+"AdaptationEvents","state",new Class<?>[]{ServerPlayer.class},p);}
    private static void reset(ServerPlayer p)throws Exception{p.removeAllEffects();p.getPersistentData().putDouble("skill",0);state(p).getClass().getMethod("reset").invoke(state(p));call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);p.tickCount+=60;}
    private static String key(int id){return "condition:jujutsucraft:technique/"+id;}
    private static void wheelTick(ServerPlayer p)throws Exception{call(BASE+"AdaptationEvents","onTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static int stage(Object state,String key)throws Exception{return (int)state.getClass().getMethod("stage",String.class).invoke(state,key);}
    private static void advance(Object state,String key,int target)throws Exception{
        state.getClass().getMethod("hit",String.class,boolean.class).invoke(state,key,false);
        for(int n=0;n<60000&&stage(state,key)<target;n++)state.getClass().getMethod("tick").invoke(state);
        check(stage(state,key)==target,"Stage failed: "+key);
    }
    private static MobEffect effect(String id){return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft",id));}
    private static void verifyLanguage()throws Exception{
        try(var jar=new java.util.zip.ZipFile(System.getProperty("t2sqa.mod"))){
            var entry=jar.getEntry("assets/jujutsucraft/lang/ru_ru.json");check(entry!=null,"RU resource missing");
            JsonObject ru=JsonParser.parseReader(new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:ru.entrySet())check(Language.getInstance().has(e.getKey()),"Missing resource key "+e.getKey());
            check(Component.translatable("jujutsu.technique.unlimited_void").getString().equals("Беспредельная пустота"),"Russian locale not active");
            Component gain=(Component)call("ru.pasha.jjclocalization.KnownTexts","component",new Class<?>[]{String.class},"Gained 17 Reputation.");
            check(gain.getString().equals("Получено репутации: 17."),"Dynamic reputation translation: "+gain.getString());
            Component main=(Component)call("ru.pasha.jjclocalization.KnownTexts","component",new Class<?>[]{String.class},"Main Hand: Sword of Extermination");
            check(main.getString().contains("Основная рука")&&main.getString().contains("Меч истребления"),"Six Eyes translation: "+main.getString());
            LogUtils.getLogger().info("JJC_QA LANGUAGE PASS: {} language keys and dynamic templates",ru.size());
        }
    }
    private static boolean target(ServerPlayer p,Entity caster,boolean domain)throws Exception{
        return (boolean)call(PROC+(domain?"LogicAttackDomainProcedure":"LogicAttackProcedure"),"execute",new Class<?>[]{LevelAccessor.class,Entity.class,Entity.class},p.level(),caster,p);
    }
    private static void speech(Entity caster,ServerPlayer p)throws Exception{call(PROC+"EffectCursedSpeechDontMoveProcedure","execute",new Class<?>[]{Entity.class,Entity.class},caster,p);}
    @SuppressWarnings("unchecked") private static void run(ServerPlayer p)throws Exception{
        check((boolean)call(BASE+"JujutsuCompatibility","available",new Class<?>[]{}),"Jujutsu mod undetected");
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","wheel"))));
        p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;
        Object state=state(p);
        Entity caster=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("jujutsucraft","gojo_satoru")).create(p.level());
        check(caster!=null,"Gojo not registered");caster.getPersistentData().putDouble("skill",206);
        caster.getPersistentData().putBoolean("CursedSpirit",true);p.getPersistentData().putBoolean("CursedSpirit",false);p.getPersistentData().putBoolean("JujutsuSorcerer",false);
        caster.moveTo(p.getX()+1,p.getY(),p.getZ());p.serverLevel().addFreshEntity(caster);
        List<?> catalogue=(List<?>)call(BASE+"AdaptationCatalogue","entries",new Class<?>[]{ServerPlayer.class},p);
        long count=catalogue.stream().filter(e->{try{return e.getClass().getMethod("key").invoke(e).toString().startsWith("condition:jujutsucraft:technique/");}catch(Exception ex){throw new RuntimeException(ex);}}).count();
        check(count==259,"Technique catalogue count "+count);
        for(Object entry:catalogue){
            String candidate=entry.getClass().getMethod("key").invoke(entry).toString();
            if(!candidate.startsWith("condition:jujutsucraft:technique/"))continue;
            caster.getPersistentData().putDouble("skill",Integer.parseInt(candidate.substring(candidate.lastIndexOf('/')+1)));
            check(call(BASE+"JujutsuCompatibility","technique",new Class<?>[]{Entity.class},caster).equals(candidate),"Misidentified technique: "+candidate);
        }
        caster.getPersistentData().putDouble("skill",99998);
        check(call(BASE+"JujutsuCompatibility","technique",new Class<?>[]{Entity.class},caster).equals(key(99998)),"New technique was not discovered automatically");
        caster.getPersistentData().putDouble("skill",206);
        var holder=p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("jujutsucraft","damage_curse")));
        DamageSource source=new DamageSource(holder,caster);
        call(BASE+"AdaptationEvents","onDamage",new Class<?>[]{LivingDamageEvent.class},new LivingDamageEvent(p,source,10));wheelTick(p);
        check(((Map<?,?>)state.getClass().getMethod("pending").invoke(state)).containsKey(key(206)),"Damage did not start Blue adaptation");
        call(BASE+"AdaptationEvents","onContact",new Class<?>[]{LivingAttackEvent.class},new LivingAttackEvent(p,source,10));
        call(BASE+"AdaptationEvents","onDamage",new Class<?>[]{LivingDamageEvent.class},new LivingDamageEvent(p,source,10));wheelTick(p);
        Object rate=((List<?>)state.getClass().getMethod("snapshots").invoke(state)).stream().filter(s->{try{return s.getClass().getMethod("key").invoke(s).equals(key(206));}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        check((int)rate.getClass().getMethod("hits").invoke(rate)==1,"A contact and its damage counted twice");
        check(Math.abs((double)rate.getClass().getMethod("work").invoke(rate)-1.3)<0.00001,"Actual server clock did not advance by x1.30");
        advance(state,key(206),1);LivingHurtEvent partial=new LivingHurtEvent(p,source,10);
        call(BASE+"AdaptationEvents","onHurt",new Class<?>[]{LivingHurtEvent.class},partial);check(Math.abs(partial.getAmount()-8)<0.001,"Stage 1 mitigation "+partial.getAmount());
        advance(state,key(206),4);LivingAttackEvent full=new LivingAttackEvent(p,source,10);call(BASE+"AdaptationEvents","onAttack",new Class<?>[]{LivingAttackEvent.class},full);check(full.isCanceled(),"Technique immunity failed");
        check(!target(p,caster,false),"Actual LogicAttack coremod not applied");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key(206),false);check(target(p,caster,false),"Per-technique toggle ignored");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key(206),true);
        ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(target(p,caster,false),"Unequipped wheel protected");p.setItemSlot(EquipmentSlot.HEAD,wheel);
        reset(p);caster.getPersistentData().putDouble("skill",308);advance(state,key(308),4);p.getPersistentData().putDouble("skill",0);
        speech(caster,p);check(p.getPersistentData().getDouble("skill")==0&&!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Cursed speech still incapacitated immune player");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key(308),false);
        speech(caster,p);check(p.getPersistentData().getDouble("skill")==-1000&&p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Disabled speech immunity still protected");
        reset(p);caster.getPersistentData().putDouble("skill",220);
        p.addEffect(new MobEffectInstance(effect("neutralization"),1000,0));
        Object capability=Class.forName("net.mcreator.jujutsucraft.network.JujutsucraftModVariables").getField("PLAYER_VARIABLES_CAPABILITY").get(null);
        Object vars=p.getCapability((net.minecraftforge.common.capabilities.Capability<Object>)capability).orElseThrow(()->new AssertionError("Missing capability"));vars.getClass().getField("PlayerCursePowerFormer").setDouble(vars,200);
        check(target(p,caster,true),"Original domain eligibility should permit test target");wheelTick(p);check(stage(state,key(220))==0,"Unexpected domain stage");
        for(int n=0;n<30;n++){p.tickCount++;target(p,caster,true);wheelTick(p);}
        Object snapshot=((List<?>)state.getClass().getMethod("snapshots").invoke(state)).stream().filter(s->{try{return s.getClass().getMethod("key").invoke(s).equals(key(220));}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        int hits=(int)snapshot.getClass().getMethod("hits").invoke(snapshot);check(hits==0,"Continuous domain accelerated from refreshes: "+hits);
        advance(state,key(220),4);p.removeEffect(effect("brain_damage"));
        call(PROC+"UnlimitedVoidActiveProcedure","execute",new Class<?>[]{LevelAccessor.class,Entity.class},p.level(),caster);
        check(!p.hasEffect(effect("brain_damage"))&&p.getPersistentData().getDouble("skill")!=-999,"Unlimited Void bypassed full adaptation");
        reset(p);int harmful=0,services=0,positive=0;
        for(MobEffect e:ForgeRegistries.MOB_EFFECTS){ResourceLocation id=ForgeRegistries.MOB_EFFECTS.getKey(e);if(!id.getNamespace().equals("jujutsucraft"))continue;
            boolean real=(boolean)call(BASE+"DamageCategories","isHarmful",new Class<?>[]{MobEffect.class},e);
            if(real){harmful++;String k="effect:"+id;advance(state,k,4);MobEffectEvent.Applicable a=new MobEffectEvent.Applicable(p,new MobEffectInstance(e,100,0));call(BASE+"NegativeEffects","applicable",new Class<?>[]{MobEffectEvent.Applicable.class},a);check(a.getResult()==Event.Result.DENY,"Harmful immunity failed: "+id);}
            else if(e.getCategory()==MobEffectCategory.BENEFICIAL)positive++;else if(e.getCategory()==MobEffectCategory.HARMFUL)services++;
        }
        check(harmful>=7&&services>=5&&positive>=15,"Effect policy incomplete: "+harmful+"/"+services+"/"+positive);
        check(!((boolean)call(BASE+"DamageCategories","isHarmful",new Class<?>[]{MobEffect.class},effect("sukuna_effect"))),"Sukuna power mistaken for disease");
        reset(p);String freeze="condition:jujutsucraft:projection_freeze";advance(state,freeze,4);p.getPersistentData().putDouble("select",0);
        check(!p.addEffect(new MobEffectInstance(effect("projection_sorcery"),100,0)),"Projection freeze immunity failed");
        p.getPersistentData().putDouble("select",1);MobEffectEvent.Applicable own=new MobEffectEvent.Applicable(p,new MobEffectInstance(effect("projection_sorcery"),100,0));
        call(BASE+"JujutsuCompatibility","projection",new Class<?>[]{MobEffectEvent.Applicable.class},own);check(own.getResult()!=Event.Result.DENY,"Own projection technique blocked");p.getPersistentData().putDouble("select",0);
        reset(p);caster.getPersistentData().putDouble("skill",207);
        Entity projectile=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("jujutsucraft","projectile_slash")).create(p.level());
        call(PROC+"SetRangedAmmoProcedure","execute",new Class<?>[]{Entity.class,Entity.class},caster,projectile);
        caster.getPersistentData().putDouble("skill",215);
        String remembered=(String)call(BASE+"JujutsuCompatibility","technique",new Class<?>[]{Entity.class},projectile);check(remembered.equals(key(207)),"Projectile changed technique after caster switched: "+remembered);
        reset(p);String species="mob:jujutsucraft:gojo_satoru";state.getClass().getMethod("hit",String.class,boolean.class).invoke(state,species,true);advance(state,species,4);
        caster.getPersistentData().putDouble("skill",206);check(!target(p,caster,false),"Boss species immunity failed");wheelTick(p);
        Object existence=((List<?>)state.getClass().getMethod("snapshots").invoke(state)).stream().filter(s->{try{return s.getClass().getMethod("key").invoke(s).equals(species);}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        check((int)existence.getClass().getMethod("hits").invoke(existence)==1,"Blocked boss attack did not accelerate existence adaptation");
        infinity(p,(LivingEntity)caster);
        worldSlash(p,(LivingEntity)caster);
        caster.discard();fixedSlash(p);
        caster.discard();p.setGameMode(GameType.CREATIVE);
        LogUtils.getLogger().info("JJC_QA SERVER PASS: 259 techniques, {} harmful effects, {} service exclusions, {} positive effects; real speech/domain/projectile coremods verified",harmful,services,positive);
    }

    private static void fixedSlash(ServerPlayer p)throws Exception{
        reset(p);p.server.setDifficulty(Difficulty.NORMAL,true);var state=state(p);String key="mob:minecraft:wither";
        state.getClass().getMethod("hit",String.class,boolean.class).invoke(state,key,true);advance(state,key,5);
        var sword=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","extermination_sword")));
        p.setItemInHand(InteractionHand.MAIN_HAND,sword);p.setYRot(0);p.setXRot(0);
        var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double oldBase=attribute.getBaseValue();attribute.setBaseValue(400);
        Object map=call("net.mcreator.jujutsucraft.network.JujutsucraftModVariables$MapVariables","get",new Class<?>[]{LevelAccessor.class},p.level());
        map.getClass().getField("config_doVanillaMobSpawning").setBoolean(map,true);
        var targets=new ArrayList<LivingEntity>();
        try {
            int distance=3;
            for(var type:List.of(EntityType.COW,EntityType.ZOMBIE,EntityType.VEX,EntityType.WITHER)){
                LivingEntity target=type.create(p.level());target.moveTo(p.getX(),p.getY()+(type==EntityType.VEX?1:0),p.getZ()+distance);distance+=0;
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(30);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
                ItemStack armor=new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE);
                armor.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION,4);
                target.setItemSlot(EquipmentSlot.CHEST,armor);
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,600,4));
                target.setAbsorptionAmount(200);
                target.setHealth(1000);if(target instanceof Mob mob)mob.setNoAi(true);
                check(p.serverLevel().addFreshEntity(target),"Test target could not join world: "+type);targets.add(target);
            }
            p.getCooldowns().removeCooldown(sword.getItem());
            call(BASE+"WorldSlash","cast",new Class<?>[]{ServerPlayer.class,ItemStack.class,InteractionHand.class},p,sword,InteractionHand.MAIN_HAND);
            for(var target:targets)check(Math.abs(target.getHealth()-850)<0.01,"Fixed 150 HP failed for "+target.getType()+": "+target.getHealth());
            for(int n=0;n<1199;n++)p.getCooldowns().tick();
            check(p.getCooldowns().isOnCooldown(sword.getItem()),"Cooldown expired before 60 seconds");
            p.getCooldowns().tick();check(!p.getCooldowns().isOnCooldown(sword.getItem()),"Cooldown did not expire at 1200 ticks");
        }finally{attribute.setBaseValue(oldBase);targets.forEach(Entity::discard);}
        LogUtils.getLogger().info("JJC_FIXED_SLASH_QA PASS: 150 HP through armor 30, toughness 20, Protection IV, Resistance V, absorption 200 on cow, zombie, vex and learned wither; exact 1200-tick cooldown");
    }
    private static void worldSlash(ServerPlayer p,LivingEntity gojo)throws Exception{
        reset(p);Object state=state(p);String boss="mob:minecraft:wither";
        var sword=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","extermination_sword")));
        p.setItemInHand(InteractionHand.MAIN_HAND,sword);p.stopUsingItem();p.setShiftKeyDown(false);
        var boundary=net.minecraft.world.entity.EntityType.COW.create(p.level());
        boundary.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        check((boolean)call(BASE+"DamageCategories","boss",new Class<?>[]{LivingEntity.class},boundary),"Exactly 100 HP must count as boss");
        check(!(boolean)call(BASE+"WorldSlash","unlocked",new Class<?>[]{ServerPlayer.class},p),"Slash unlocked before boss learning");
        state.getClass().getMethod("hit",String.class,boolean.class).invoke(state,boss,true);advance(state,boss,4);
        check(!(boolean)call(BASE+"WorldSlash","unlocked",new Class<?>[]{ServerPlayer.class},p),"Stage 4 incorrectly unlocks existence reward");
        advance(state,boss,5);
        check((boolean)call(BASE+"WorldSlash","unlocked",new Class<?>[]{ServerPlayer.class},p),"Boss stage 5 did not unlock slash");
        check(stage(state,key(205))==0,"Test accidentally learned Infinity");
        gojo.moveTo(p.getX(),p.getY(),p.getZ()+6);gojo.setHealth(gojo.getMaxHealth());gojo.removeAllEffects();
        gojo.addEffect(new MobEffectInstance(effect("infinity_effect"),1005,3));p.setYRot(0);p.setXRot(0);
        probeDamage(p,gojo,false,"Boss learning alone must not bypass Infinity on ordinary attacks");
        p.setShiftKeyDown(true);sword.use(p.level(),p,InteractionHand.MAIN_HAND);p.setShiftKeyDown(false);
        check((boolean)call(BASE+"WorldSlash","selected",new Class<?>[]{ItemStack.class},sword),"Shift-use did not select slash");
        var cow=net.minecraft.world.entity.EntityType.COW.create(p.level());cow.moveTo(p.getX()+4,p.getY(),p.getZ()+6);p.serverLevel().addFreshEntity(cow);
        gojo.invulnerableTime=0;float health=gojo.getHealth();int durability=sword.getDamageValue();
        sword.use(p.level(),p,InteractionHand.MAIN_HAND);
        check(gojo.getHealth()<health,"World Slash did not inflict real health damage through actual Infinity");
        check(gojo.hasEffect(effect("infinity_effect")),"Slash removed Infinity globally");
        check(cow.getHealth()==cow.getMaxHealth(),"Slash hit an off-axis target");
        check(sword.getDamageValue()==durability+3,"Slash durability cost is not 3");
        health=gojo.getHealth();gojo.invulnerableTime=0;sword.use(p.level(),p,InteractionHand.MAIN_HAND);
        check(gojo.getHealth()==health&&sword.getDamageValue()==durability+3,"Cooldown failed to prevent second cast");
        probeDamage(p,gojo,false,"Slash bypass leaked after its damage callback");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,boss,false);
        check(!(boolean)call(BASE+"WorldSlash","unlocked",new Class<?>[]{ServerPlayer.class},p),"Disabled boss adaptation still grants slash");
        p.setShiftKeyDown(true);sword.use(p.level(),p,InteractionHand.MAIN_HAND);p.setShiftKeyDown(false);
        check(!(boolean)call(BASE+"WorldSlash","selected",new Class<?>[]{ItemStack.class},sword),"Cannot return to parry after losing unlock");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,boss,true);
        ItemStack helmet=p.getItemBySlot(EquipmentSlot.HEAD);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(!(boolean)call(BASE+"WorldSlash","unlocked",new Class<?>[]{ServerPlayer.class},p),"Unequipped wheel grants slash");p.setItemSlot(EquipmentSlot.HEAD,helmet);
        call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);
        Object restored=call(BASE+"AdaptationEvents","loadState",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);
        check(stage(restored,boss)==5,"Slash prerequisite did not persist");
        cow.discard();p.getCooldowns().removeCooldown(sword.getItem());
        LogUtils.getLogger().info("JJC_WORLD_SLASH_QA PASS: boss >=100 HP; stage 5 unlock; Shift-use mode; real ranged Gojo damage without Infinity learning; effect retained; bypass scoped; cooldown, durability, targeting, toggles and persistence");
    }
    private static Object snapshot(Object state,String key)throws Exception{
        for(Object s:(List<?>)state.getClass().getMethod("snapshots").invoke(state))
            if(s.getClass().getMethod("key").invoke(s).equals(key))return s;
        throw new AssertionError("No snapshot: "+key);
    }
    private static void field(ServerPlayer p,LivingEntity gojo)throws Exception{
        gojo.removeEffect(effect("infinity_effect"));gojo.addEffect(new MobEffectInstance(effect("infinity_effect"),1005,3));
        call(PROC+"InfinityActiveTickProcedure","execute",new Class<?>[]{LevelAccessor.class,Entity.class},p.level(),gojo);
    }
    private static net.minecraft.world.entity.projectile.Arrow arrow(ServerPlayer owner,LivingEntity gojo){
        var arrow=new net.minecraft.world.entity.projectile.Arrow(owner.serverLevel(),owner);
        arrow.setPos(gojo.getX()-1,gojo.getY()+gojo.getBbHeight()*0.5-arrow.getBbHeight()*0.5,gojo.getZ());
        arrow.setDeltaMovement(1,0,0);owner.serverLevel().addFreshEntity(arrow);return arrow;
    }
    private static void probeDamage(ServerPlayer p,LivingEntity gojo,boolean allowed,String description){
        gojo.invulnerableTime=0;float before=gojo.getHealth();
        gojo.hurt(p.damageSources().playerAttack(p),8);
        check(allowed?gojo.getHealth()<before:gojo.getHealth()==before,description+": "+before+" -> "+gojo.getHealth());
        check(gojo.hasEffect(effect("infinity_effect")),"Infinity was removed globally: "+description);
    }
    private static void infinity(ServerPlayer p,LivingEntity gojo)throws Exception{
        reset(p);Object state=state(p);String key=key(205);
        gojo.removeAllEffects();gojo.setHealth(gojo.getMaxHealth());gojo.getPersistentData().putDouble("skill",0);
        if(gojo instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);
        gojo.addEffect(new MobEffectInstance(effect("infinity_effect"),1005,3));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        float health=gojo.getHealth();p.attack(gojo);wheelTick(p);
        check(gojo.getHealth()==health,"Unadapted fist bypassed Infinity");
        check(((Map<?,?>)state.getClass().getMethod("pending").invoke(state)).containsKey(key),"Blocked swing did not begin Infinity adaptation");
        Object first=snapshot(state,key);check((int)first.getClass().getMethod("hits").invoke(first)==0,"First swing counted more than once");
        p.tickCount++;p.attack(gojo);wheelTick(p);Object second=snapshot(state,key);
        check((int)second.getClass().getMethod("hits").invoke(second)==1,"Repeated blocked swing did not accelerate exactly once");
        check(Math.abs((double)second.getClass().getMethod("work").invoke(second)-1.3)<0.00001,"Infinity clock did not advance at x1.30");
        advance(state,key,3);probeDamage(p,gojo,false,"Stage 3 must remain blocked");
        var held=arrow(p,gojo);field(p,gojo);check(held.getDeltaMovement().lengthSqr()<0.001,"Original Infinity field no longer stops unadapted arrows");held.discard();
        advance(state,key,4);probeDamage(p,gojo,true,"Full adaptation must inflict real damage");
        check(gojo.getEffect(effect("infinity_effect")).getAmplifier()==3,"Infinity strength changed globally");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);gojo.invulnerableTime=0;health=gojo.getHealth();p.attack(gojo);
        check(gojo.getHealth()<health,"Actual adapted fist did not damage Gojo");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));gojo.invulnerableTime=0;health=gojo.getHealth();p.attack(gojo);
        check(gojo.getHealth()<health,"Actual adapted sword did not damage Gojo");
        var shot=arrow(p,gojo);field(p,gojo);check(shot.getDeltaMovement().x>0.9&&!shot.getPersistentData().getBoolean("Stop"),"Adapted arrow was still stopped by actual field");shot.discard();
        var rival=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(p.serverLevel());
        LivingAttackEvent rivalHit=new LivingAttackEvent(gojo,rival.damageSources().playerAttack(rival),8);
        call(PROC+"WhenEntityAttacked1Procedure","onEntityAttacked",new Class<?>[]{LivingAttackEvent.class},rivalHit);
        check(rivalHit.isCanceled(),"Another player inherited personal Infinity bypass");
        var rivalShot=arrow(rival,gojo);field(p,gojo);check(rivalShot.getDeltaMovement().lengthSqr()<0.001,"Another player's arrow bypassed field");rivalShot.discard();
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key,false);probeDamage(p,gojo,false,"Disabled Infinity entry must not bypass");
        state.getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state,key,true);
        state.getClass().getMethod("setEnabled",boolean.class).invoke(state,false);probeDamage(p,gojo,false,"Disabled wheel must not bypass");state.getClass().getMethod("setEnabled",boolean.class).invoke(state,true);
        ItemStack helmet=p.getItemBySlot(EquipmentSlot.HEAD);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);probeDamage(p,gojo,false,"Unequipped wheel must not bypass");p.setItemSlot(EquipmentSlot.HEAD,helmet);probeDamage(p,gojo,true,"Re-equipped wheel must restore bypass");
        call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);
        Object restored=call(BASE+"AdaptationEvents","loadState",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);check(stage(restored,key)==4,"Infinity adaptation did not persist");
        reset(p);p.tickCount++;var discovery=arrow(p,gojo);field(p,gojo);wheelTick(p);
        check(((Map<?,?>)state.getClass().getMethod("pending").invoke(state)).containsKey(key),"Blocked arrow did not start adaptation");
        for(int n=0;n<3;n++){p.tickCount++;field(p,gojo);wheelTick(p);}
        Object refresh=snapshot(state,key);check((int)refresh.getClass().getMethod("hits").invoke(refresh)==0,"Same suspended arrow produced fake acceleration");discovery.discard();
        gojo.removeEffect(effect("infinity_effect"));reset(p);p.tickCount++;p.attack(gojo);wheelTick(p);
        check(!((Map<?,?>)state.getClass().getMethod("pending").invoke(state)).containsKey(key),"Inactive Infinity started false adaptation");
        LogUtils.getLogger().info("JJC_INFINITY_QA PASS: real Gojo health decreases after stage 4 with fist and sword; stages 0-3 blocked; personal arrows bypass actual field; rival arrows remain blocked; learning, x1.30, no refresh farming, toggles, unequip and persistence verified");
    }
}




