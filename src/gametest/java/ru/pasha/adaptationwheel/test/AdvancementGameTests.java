package ru.pasha.adaptationwheel.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.*;

/**
 * The wheel's advancement tab on real server players (Forge makes award a no-op for a FakePlayer): the tree as loaded,
 * the data nodes through the vanilla inventory listener, the code nodes through their live hooks, /adaptation learn,
 * the menu request and the login catch-up, which lands in the first (reset) packet and so shows no toasts.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancementGameTests {
    private static final String Z="mob:minecraft:zombie",POISON="effect:minecraft:poison",FALL="damage:minecraft:fall";
    private static final ResourceLocation ROOT=id("root"),SWORD=id("sword"),RECIPE=id("recipes/combat/wheel"),SWORD_RECIPE=id("recipes/combat/extermination_sword"),
        WORN=WheelAdvancements.WORN,FIRST=WheelAdvancements.FIRST,FOUR=WheelAdvancements.FOUR,EXISTENCE=WheelAdvancements.EXISTENCE,
        MENU=WheelAdvancements.MENU,SLASH=WheelAdvancements.WORLD_SLASH;
    private static final List<ResourceLocation> VISIBLE=List.of(ROOT,WORN,FIRST,FOUR,EXISTENCE,MENU,SWORD,SLASH);

    @GameTest(template="empty",timeoutTicks=60)
    public static void advancementTreeLoads(GameTestHelper h) {
        var manager=h.getLevel().getServer().getAdvancements();
        Map<ResourceLocation,ResourceLocation> parents=Map.of(WORN,ROOT,FIRST,WORN,FOUR,FIRST,EXISTENCE,FOUR,MENU,ROOT,SWORD,ROOT,SLASH,EXISTENCE);
        Map<ResourceLocation,FrameType> frames=Map.of(ROOT,FrameType.TASK,WORN,FrameType.TASK,FIRST,FrameType.TASK,FOUR,FrameType.GOAL,
            EXISTENCE,FrameType.CHALLENGE,MENU,FrameType.TASK,SWORD,FrameType.TASK,SLASH,FrameType.GOAL);
        for(var id:VISIBLE){
            // A JSON error only logs "Parsing error loading custom advancement" and drops the node with its children.
            var a=manager.getAdvancement(id);
            check(h,a!=null&&a.getDisplay()!=null,"Advancement did not load: "+id);
            var d=a.getDisplay();var parent=a.getParent()==null?null:a.getParent().getId();
            check(h,Objects.equals(parent,parents.get(id)),id+" hangs under "+parent);
            check(h,d.getFrame()==frames.get(id),id+" has the frame "+d.getFrame());
            // Vanilla's Russian chat line about an advancement fixes the player's gender; the toast headings do not.
            check(h,!d.isHidden()&&!d.shouldAnnounceChat()&&d.shouldShowToast(),id+" is hidden, announced in chat or has no toast");
            check(h,d.getTitle().getContents() instanceof TranslatableContents title&&title.getKey().equals(key(id,"title"))
                &&d.getDescription().getContents() instanceof TranslatableContents text&&text.getKey().equals(key(id,"description")),id+" does not use its own translation keys");
        }
        var root=manager.getAdvancement(ROOT).getDisplay();
        check(h,new ResourceLocation("textures/gui/advancements/backgrounds/end.png").equals(root.getBackground())&&root.getIcon().is(AdaptationWheel.WHEEL.get()),
            "The tab has the wrong background or icon: "+root.getBackground());
        var menu=(TranslatableContents)manager.getAdvancement(MENU).getDisplay().getDescription().getContents();
        check(h,menu.getArgs().length==1&&menu.getArgs()[0] instanceof Component bound&&bound.getContents() instanceof KeybindContents keybind
            &&keybind.getName().equals("key.adaptationwheel.menu"),"The menu description does not show the bound key");
        for(var id:List.of(RECIPE,SWORD_RECIPE)){
            var a=manager.getAdvancement(id);
            check(h,a!=null&&a.getDisplay()==null&&a.getParent()!=null&&a.getParent().getId().equals(new ResourceLocation("recipes/root")),"Recipe advancement missing or visible: "+id);
        }
        int shown=0,all=0;
        for(var a:manager.getAllAdvancements())if(a.getId().getNamespace().equals(AdaptationWheel.MOD_ID)){all++;if(a.getDisplay()!=null)shown++;}
        check(h,shown==8&&all<=10,"The namespace has "+shown+" visible advancements and "+all+" in all");
        var en=lang("en_us");var ru=lang("ru_ru");
        for(var id:VISIBLE)for(var part:List.of("title","description"))
            check(h,en.has(key(id,part))&&ru.has(key(id,part)),"A translation is missing: "+key(id,part));
        check(h,advancementKeys(en).equals(advancementKeys(ru)),"en_us and ru_ru translate different advancement keys");
        for(var file:List.of(en,ru)){
            String text=file.get(key(MENU,"description")).getAsString();
            check(h,text.contains("%s")&&text.indexOf("%s")==text.lastIndexOf("%s"),"The menu description needs exactly one %s for the key: "+text);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void rootOnlyWhenTheWheelIsCarried(GameTestHelper h) {
        var p=player(h,"AdvCarrier",null,60,true);
        p.getInventory().add(new ItemStack(Items.STICK));p.tick();
        check(h,!done(p,ROOT),"A stick completed the root");
        p.getInventory().add(new ItemStack(AdaptationWheel.WHEEL.get()));p.tick();
        check(h,done(p,ROOT),"A wheel in the inventory did not complete the root");
        check(h,!done(p,SWORD),"The sword advancement came without a sword");
        p.getInventory().add(new ItemStack(AdaptationWheel.EXTERMINATION_SWORD.get()));p.tick();
        check(h,done(p,SWORD),"A Sword of Extermination in the inventory did not complete its advancement");
        // An armour slot of the inventory menu: a wheel that only ever sat on the head counts too.
        var head=player(h,"AdvHeadOnly",null,60,true);
        head.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));head.tick();
        check(h,head.getInventory().items.stream().noneMatch(s -> s.is(AdaptationWheel.WHEEL.get()))&&done(head,ROOT),"A wheel on the head did not complete the root");
        check(h,!done(head,SWORD),"The sword advancement came without a sword");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void wornOnlyOnTheHead(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(1,2,1));
        for(boolean enabled:new boolean[]{true,false}){
            // The full player tick sees equipment changes (LivingEntity.detectEquipmentUpdates); no gravity, no fall.
            var p=player(h,"AdvWearer",null,60,false);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setNoGravity(true);
            AdaptationEvents.state(p).setEnabled(enabled);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AdaptationWheel.WHEEL.get()));p.doTick();p.doTick();
            check(h,!done(p,WORN),"A wheel in the hand counted as worn");
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.GOLDEN_HELMET));p.doTick();
            check(h,!done(p,WORN),"A golden helmet counted as the wheel");
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));p.doTick();
            check(h,done(p,WORN),"The wheel on the head did not give its advancement (wheel switched "+(enabled?"on":"off")+")");
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.doTick();
            check(h,done(p,WORN),"Taking the wheel off took the advancement back");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void stagesAwardByNormalPlayNotEarly(GameTestHelper h) {
        var p=wearer(h,"AdvPlay");var state=AdaptationEvents.state(p);
        state.hit(Z,false);
        int toFirst=advanceTo(h,p,Z,1);
        check(h,done(p,FIRST)&&!done(p,FOUR),"Stage I did not give exactly the first advancement");
        advanceTo(h,p,Z,3);
        check(h,!done(p,FOUR),"Stage III already counted as stage IV");
        advanceTo(h,p,Z,4);
        check(h,done(p,FOUR)&&!done(p,EXISTENCE),"Stage IV did not give its advancement, or gave existence");
        for(int i=0;i<200;i++)tick(p);
        check(h,state.stage(Z)==4&&!done(p,EXISTENCE),"An ordinary adaptation went past IV or reached existence");
        // Utility adaptations count too.
        var meta=player(h,"AdvMeta",null,60,false);
        check(h,run(h,op(meta),"adaptation learn @s 1 "+MetaMechanic.RIPTIDE.key)==1&&done(meta,FIRST)&&!done(meta,FOUR),"Stage I of a utility adaptation did not count");
        var bare=player(h,"AdvBare",null,60,false);AdaptationEvents.state(bare).hit(Z,false);
        for(int i=0;i<toFirst+100;i++)tick(bare);
        check(h,AdaptationEvents.state(bare).stage(Z)==0&&!done(bare,FIRST),"An adaptation completed without the wheel on the head");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void existenceOnlyFromBossStageFive(GameTestHelper h) {
        var p=wearer(h,"AdvBoss");AdaptationEvents.state(p).hit(Z,true);
        advanceTo(h,p,Z,4);
        check(h,done(p,FOUR)&&!done(p,EXISTENCE),"Stage IV of a boss already counted as existence");
        advanceTo(h,p,Z,5);
        check(h,done(p,EXISTENCE)&&done(p,FOUR)&&done(p,FIRST),"Stage V of a boss did not give existence");
        var plain=wearer(h,"AdvNotBoss");var state=AdaptationEvents.state(plain);
        state.setStage(Z,4,null);state.hit(Z,false);
        for(int i=0;i<400;i++)tick(plain);
        check(h,state.stage(Z)==4&&!done(plain,EXISTENCE),"An ordinary adaptation at IV reached existence");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void learnCountsCumulativelyAndNeverRevokes(GameTestHelper h) {
        var a=player(h,"AdvLearnA",null,60,false);var b=player(h,"AdvLearnB",null,60,false);var c=player(h,"AdvLearnC",null,60,false);
        online(h,List.of(a,b,c),() -> {
            var op=op(a);
            check(h,run(h,op,"adaptation learn AdvLearnA 1 "+Z)==1&&done(a,FIRST)&&!done(a,FOUR),"learn 1 did not give exactly the first advancement");
            check(h,run(h,op,"adaptation learn AdvLearnA 4 "+POISON)==1&&done(a,FOUR),"learn 4 did not give stage IV");
            check(h,run(h,op,"adaptation learn AdvLearnC 4 "+MetaMechanic.RIPTIDE.key)==1&&done(c,FIRST)&&done(c,FOUR),"learn 4 on a fresh player did not give both stage advancements");
            check(h,!done(b,FIRST)&&!done(b,FOUR),"learn for others reached a third player");
            // Advancements are history: nothing takes them back.
            check(h,run(h,op,"adaptation learn AdvLearnA 1 "+POISON)==1&&run(h,op,"adaptation forget AdvLearnA "+Z)==1
                &&run(h,op,"adaptation reset AdvLearnA")==1&&run(h,op,"adaptation off AdvLearnA")==1,"lowering, forget, reset or off failed");
            check(h,AdaptationEvents.state(a).snapshots().isEmpty()&&!AdaptationEvents.state(a).enabled()&&done(a,FIRST)&&done(a,FOUR),"A command took an advancement back");
            for(var p:List.of(a,b,c))check(h,!done(p,EXISTENCE),"learn gave existence");
            check(h,!done(b,FIRST)&&!done(b,FOUR),"learn for others reached a third player");
        });
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void menuOpenedOnceThroughCatalogueRequest(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"AdvMenu",sent,60,false);
        check(h,!done(p,MENU),"The menu advancement came before the menu");
        // No wheel needed, as for the menu itself.
        WheelNetwork.catalogueRequested(p);p.tick();
        check(h,done(p,MENU)&&updates(sent).size()==1,"Opening the menu sent "+updates(sent).size()+" advancement updates, not one");
        sent.clear();
        for(int i=0;i<50;i++)WheelNetwork.catalogueRequested(p);
        p.tick();
        check(h,done(p,MENU)&&updates(sent).isEmpty(),"Opening the menu again sent advancement updates");
        var other=player(h,"AdvMenuPacket",null,60,false);
        boolean handled=handleCatalogueRequest(other);
        com.mojang.logging.LogUtils.getLogger().info("ADVANCEMENT_MENU_PACKET: "+(handled?"CatalogueRequest.handle ran":"skipped, the module system refused the handler's context"));
        if(handled)check(h,done(other,MENU),"The CatalogueRequest handler did not award the menu advancement");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void worldSlashOnlyFromARealCast(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=slasher(h,"AdvSlash",sent);var sword=p.getMainHandItem();
        WorldSlash.cast(p,sword,InteractionHand.MAIN_HAND);
        check(h,overlay(sent,"message.adaptationwheel.world_slash_locked")&&!done(p,SLASH),"A locked World Slash counted, or did not say it is locked");
        AdaptationEvents.state(p).hit(Z,true);advanceTo(h,p,Z,5);
        check(h,WorldSlash.unlocked(p)&&!done(p,SLASH),"Stage V of a boss did not unlock World Slash, or already gave its advancement");
        WorldSlash.cast(p,sword,InteractionHand.MAIN_HAND);
        check(h,done(p,SLASH)&&p.getCooldowns().isOnCooldown(sword.getItem()),"A real World Slash did not give its advancement");
        int wear=sword.getDamageValue();
        WorldSlash.cast(p,sword,InteractionHand.MAIN_HAND);
        check(h,sword.getDamageValue()==wear&&done(p,SLASH),"A World Slash on cooldown still cut");
        var cutter=slasher(h,"AdvCutOnly",null);seed(cutter,entry(Z,5,true));
        check(h,WorldSlash.unlocked(cutter),"A saved stage V did not unlock World Slash");
        WorldSlash.cut(cutter,h.getLevel(),WorldSlash.plane(cutter));
        check(h,!done(cutter,SLASH),"WorldSlash.cut without a cast gave the advancement");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void loginCatchUpIsSilentAndIdempotent(GameTestHelper h) {
        // Never ticked: the login comes before the first advancement packet, as in PlayerList.placeNewPlayer.
        var sent=new ArrayList<Packet<?>>();var p=player(h,"AdvVeteran",sent,0,false);
        // Not in stage order, as in a real save (entries keep the order they started in): the highest is neither first nor last.
        seed(p,entry(FALL,4,false),entry(Z,5,true),entry(POISON,1,false));
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        login(p);
        check(h,done(p,FIRST)&&done(p,FOUR)&&done(p,EXISTENCE)&&done(p,WORN),"The login did not credit what the saved wheel proves");
        check(h,!done(p,MENU)&&!done(p,SLASH),"The login credited something nothing records");
        p.tick();
        var first=updates(sent);
        check(h,first.size()==1&&first.get(0).shouldReset()&&first.get(0).getProgress().containsKey(EXISTENCE),"The credit did not arrive in the reset packet, the one that shows no toasts");
        sent.clear();login(p);p.tick();
        check(h,updates(sent).isEmpty(),"A second login sent advancement updates");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void loginCatchUpReadsOldSavesOnly(GameTestHelper h) {
        var old=player(h,"AdvOldSave",null,0,false);
        var legacy=new CompoundTag();legacy.putInt("Version",1);
        var completed=new ListTag();completed.add(StringTag.valueOf(Z));legacy.put("Completed",completed);
        old.getPersistentData().put(AdaptationWheel.MOD_ID,legacy);login(old);
        check(h,done(old,FIRST)&&done(old,FOUR)&&!done(old,EXISTENCE),"A version 1 save's completed adaptation did not count as stage IV");
        var started=player(h,"AdvStageZero",null,0,false);seed(started,entry(Z,0,true),entry(POISON,0,false));
        started.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));login(started);
        check(h,!done(started,FIRST)&&!done(started,FOUR)&&!done(started,EXISTENCE),"Stage 0 entries were credited");
        check(h,done(started,WORN),"The wheel on the head was not credited at login next to stage 0 entries");
        // Worn before 4.32 with nothing adapted yet: the worn wheel alone, in the reset packet.
        var sent=new ArrayList<Packet<?>>();var fresh=player(h,"AdvFreshWearer",sent,0,false);
        fresh.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));login(fresh);
        check(h,done(fresh,WORN)&&!done(fresh,FIRST),"An empty save with the wheel on the head did not credit exactly the worn wheel");
        fresh.tick();var first=updates(sent);
        check(h,first.size()==1&&first.get(0).shouldReset()&&first.get(0).getProgress().containsKey(WORN),"The worn wheel did not arrive in the reset packet");
        var carried=player(h,"AdvCarried",null,0,false);carried.getInventory().add(new ItemStack(AdaptationWheel.WHEEL.get()));login(carried);
        check(h,!done(carried,WORN)&&!done(carried,FIRST),"A wheel in the inventory counted as worn at login");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void loginCatchUpIgnoresTheSwitches(GameTestHelper h) {
        // The whole wheel switched off: the saved stages and the wheel on the head still count.
        var master=player(h,"AdvMasterOff",null,0,false);
        seed(master,false,entry(POISON,1,false),entry(Z,5,true),entry(FALL,4,false));
        master.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));login(master);
        check(h,!AdaptationEvents.state(master).enabled(),"The saved global switch did not load as off");
        check(h,done(master,FIRST)&&done(master,FOUR)&&done(master,EXISTENCE)&&done(master,WORN),"The login skipped a wheel switched off as a whole");
        // Only the adaptations at IV and V are switched off in the J menu.
        var menu=player(h,"AdvEntriesOff",null,0,false);
        seed(menu,true,entry(POISON,1,false),entry(Z,5,true,false),entry(FALL,4,false,false));login(menu);
        var state=AdaptationEvents.state(menu);
        check(h,state.enabled()&&!state.snapshot(Z).enabled()&&!state.snapshot(FALL).enabled()&&state.snapshot(POISON).enabled(),"The saved per-adaptation switches did not load");
        check(h,done(menu,FIRST)&&done(menu,FOUR)&&done(menu,EXISTENCE),"The login skipped adaptations switched off in the menu");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void wheelRecipeUnlocksFromGoldPearlOrRecipe(GameTestHelper h) {
        var recipe=id("wheel");
        var gold=player(h,"AdvGold",null,60,true);
        gold.getInventory().add(new ItemStack(Items.DIRT));gold.tick();
        check(h,!done(gold,RECIPE)&&!gold.getRecipeBook().contains(recipe),"Dirt unlocked the wheel recipe");
        gold.getInventory().add(new ItemStack(Items.GOLD_INGOT));gold.tick();
        check(h,done(gold,RECIPE)&&gold.getRecipeBook().contains(recipe),"A gold ingot did not unlock the wheel recipe");
        var pearl=player(h,"AdvPearl",null,60,true);
        pearl.getInventory().add(new ItemStack(Items.ENDER_PEARL));pearl.tick();
        check(h,done(pearl,RECIPE)&&pearl.getRecipeBook().contains(recipe),"An ender pearl did not unlock the wheel recipe");
        var given=player(h,"AdvGiven",null,60,false);
        given.awardRecipesByKey(new ResourceLocation[]{recipe});
        check(h,done(given,RECIPE),"A recipe given another way did not complete its advancement");
        var wheel=h.getLevel().getServer().getRecipeManager().byKey(recipe);
        check(h,wheel.isPresent()&&wheel.get() instanceof CraftingRecipe crafting&&crafting.category()==CraftingBookCategory.EQUIPMENT,"The wheel recipe is not filed under equipment");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void missingOrFakeIsHarmless(GameTestHelper h) {
        var p=player(h,"AdvMissing",null,60,false);
        check(h,!WheelAdvancements.award(p,id("nope")),"A missing advancement was awarded");
        var fake=new Fake(h.getLevel());var state=AdaptationEvents.state(fake);state.hit(Z,false);
        for(int guard=20000;state.stage(Z)<4&&guard>0;guard--)tick(fake);
        check(h,state.stage(Z)==4,"The fake player did not reach stage IV");
        for(var id:List.of(FIRST,FOUR))check(h,!fake.getAdvancements().getOrStartProgress(advancement(h,id)).hasProgress(),"A FakePlayer got progress on "+id);
        h.succeed();
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(AdaptationWheel.MOD_ID,path); }
    private static String key(ResourceLocation id,String part) { return "advancements.adaptationwheel."+id.getPath()+"."+part; }
    private static Advancement advancement(GameTestHelper h,ResourceLocation id) {
        var a=h.getLevel().getServer().getAdvancements().getAdvancement(id);
        if(a==null)throw new IllegalStateException("Advancement did not load: "+id);
        return a;
    }
    private static boolean done(ServerPlayer p,ResourceLocation id) {
        var a=p.server.getAdvancements().getAdvancement(id);
        if(a==null)throw new IllegalStateException("Advancement did not load: "+id);
        return p.getAdvancements().getOrStartProgress(a).isDone();
    }
    /** Ticks the wheel until the key reaches the stage; after every tick the stage advancements must match the highest stage held. */
    private static int advanceTo(GameTestHelper h,ServerPlayer p,String key,int stage) {
        int ticks=0;
        while(AdaptationEvents.state(p).stage(key)<stage){
            if(++ticks>20000)h.fail("Stage never completed: "+key);
            tick(p);
            int top=0;for(var s:AdaptationEvents.state(p).snapshots())top=Math.max(top,s.stage());
            if(done(p,FIRST)!=(top>=1)||done(p,FOUR)!=(top>=4)||done(p,EXISTENCE)!=(top>=5))
                h.fail("At stage "+top+" the stage advancements are "+done(p,FIRST)+"/"+done(p,FOUR)+"/"+done(p,EXISTENCE));
        }
        return ticks;
    }
    private static void tick(ServerPlayer p) { p.tickCount++; MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p)); }
    /** The wheel's own login listener: posting the event would also run Forge's tier sync, which needs a real channel. */
    private static void login(ServerPlayer p) { AdaptationEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); }
    private static CompoundTag entry(String key,int stage,boolean boss) { return entry(key,stage,boss,true); }
    /** {@code enabled} is the adaptation's own J-menu switch. */
    private static CompoundTag entry(String key,int stage,boolean boss,boolean enabled) {
        var e=new CompoundTag();e.putString("Key",key);e.putBoolean("Enabled",enabled);e.putBoolean("Boss",boss);e.putInt("Stage",stage);return e;
    }
    private static void seed(ServerPlayer p,CompoundTag... entries) { seed(p,true,entries); }
    /** A saved wheel (Version 5), {@code enabled} its global switch. The state loads lazily, so this must come before anything asks for it. */
    private static void seed(ServerPlayer p,boolean enabled,CompoundTag... entries) {
        var tag=new CompoundTag();tag.putInt("Version",5);tag.putBoolean("Enabled",enabled);
        var list=new ListTag();list.addAll(Arrays.asList(entries));tag.put("Entries",list);
        p.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
    }
    private static List<ClientboundUpdateAdvancementsPacket> updates(List<Packet<?>> sent) {
        var found=new ArrayList<ClientboundUpdateAdvancementsPacket>();
        for(var packet:sent)if(packet instanceof ClientboundUpdateAdvancementsPacket update)found.add(update);
        return found;
    }
    private static boolean overlay(List<Packet<?>> sent,String key) {
        for(var packet:sent)if(packet instanceof ClientboundSystemChatPacket chat&&chat.overlay()
            &&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().equals(key))return true;
        return false;
    }
    private static JsonObject lang(String name) {
        try(var in=AdvancementGameTests.class.getResourceAsStream("/assets/adaptationwheel/lang/"+name+".json")) {
            if(in==null)throw new IllegalStateException("No "+name+".json on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static Set<String> advancementKeys(JsonObject lang) {
        var keys=new TreeSet<String>();for(var k:lang.keySet())if(k.startsWith("advancements."))keys.add(k);return keys;
    }
    /** The packet's own handler, as the network runs it, when the module system lets a test build its context; false if refused. */
    private static boolean handleCatalogueRequest(ServerPlayer p) {
        Object request;java.lang.reflect.Method handle;NetworkEvent.Context context;
        try {
            var type=Class.forName("ru.pasha.adaptationwheel.net.WheelNetwork$CatalogueRequest");
            var make=type.getDeclaredConstructor();make.setAccessible(true);request=make.newInstance();
            handle=type.getDeclaredMethod("handle",Supplier.class);handle.setAccessible(true);
            var build=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,BiConsumer.class);build.setAccessible(true);
            BiConsumer<ResourceLocation,FriendlyByteBuf> sink=(id,buffer) -> {};
            context=(NetworkEvent.Context)build.newInstance(p.connection.connection,NetworkDirection.PLAY_TO_SERVER,sink);
        } catch(ReflectiveOperationException|RuntimeException refused) { return false; }
        try { handle.invoke(request,(Supplier<NetworkEvent.Context>)() -> context); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("The CatalogueRequest handler failed",e); }
        return true;
    }
    private static int run(GameTestHelper h,CommandSourceStack source,String command) {
        return h.getLevel().getServer().getCommands().performPrefixedCommand(source,command);
    }
    private static CommandSourceStack op(ServerPlayer p) { return p.createCommandSourceStack().withPermission(2); }
    /** Forge hands out a read-only view of the player list; the list behind it is the one ArrayList field. */
    @SuppressWarnings("unchecked")
    private static void online(GameTestHelper h,List<ServerPlayer> players,Runnable body) {
        List<ServerPlayer> list=null;var playerList=h.getLevel().getServer().getPlayerList();
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(List.class.isAssignableFrom(field.getType()))try{
            field.setAccessible(true);if(field.get(playerList) instanceof ArrayList<?> found)list=(List<ServerPlayer>)found;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(list==null)throw new IllegalStateException("No player list to join");
        list.addAll(players);
        try{body.run();}finally{list.removeAll(players);}
    }
    private static void check(GameTestHelper h,boolean value,String message) { if(!value)h.fail(message); }
    private static ServerPlayer wearer(GameTestHelper h,String name) {
        var p=player(h,name,null,60,false);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));return p;
    }
    /** Wheel on, sword in hand, high above this test's structure and looking straight up: the plane meets only sky. */
    private static ServerPlayer slasher(GameTestHelper h,String name,List<Packet<?>> sent) {
        var p=player(h,name,sent,60,false);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        var base=h.absolutePos(new BlockPos(1,2,1));
        p.setPos(base.getX()+.5,base.getY()+120,base.getZ()+.5);p.setYRot(0);p.setXRot(-90);p.yHeadRot=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AdaptationWheel.EXTERMINATION_SWORD.get()));
        return p;
    }
    private static ServerGamePacketListenerImpl capture(GameTestHelper h,ServerPlayer p,List<Packet<?>> sent) {
        return new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p){
            @Override public void send(Packet<?> packet){sent.add(packet);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){sent.add(packet);}
        };
    }
    /**
     * A real ServerPlayer outside the player list, no wheel. {@code ticks} server ticks flush the first (reset)
     * advancement packet; {@code inventory} adds the slot listener PlayerList.placeNewPlayer would, without which
     * inventory_changed never fires. Packets go to {@code sent} when it is not null.
     */
    private static ServerPlayer player(GameTestHelper h,String name,List<Packet<?>> sent,int ticks,boolean inventory) {
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        p.connection=sent==null?new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p):capture(h,p,sent);
        if(inventory)p.initInventoryMenu();
        for(int i=0;i<ticks;i++)p.tick();
        p.setPos(0,100,0);p.setHealth(20);
        if(sent!=null)sent.clear();
        return p;
    }
    private static final class Fake extends FakePlayer {
        Fake(ServerLevel level) {
            super(level,new GameProfile(UUID.randomUUID(),"AdvFake"));setPos(0,100,0);setHealth(20);
            setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        }
    }
}
