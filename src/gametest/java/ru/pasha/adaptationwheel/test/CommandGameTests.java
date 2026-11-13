package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.*;

/**
 * /adaptation as typed: every command goes through the server's own dispatcher (parsing, permissions, argument types,
 * execution and the chat it sends), from a test player's source at permission 0 or 2, or from the console. Another
 * player is found by name in the server's player list, so the test players stand in it, only within one call.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandGameTests {
    private static final String ZOMBIE="mob:minecraft:zombie",POISON="effect:minecraft:poison";

    @GameTest(template="empty",timeoutTicks=60)
    public static void learnSetsStageByFullKeyOrShortName(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdLearner",sent);var state=AdaptationEvents.state(p);
        long revision=state.revision();
        check(h,run(h,op(p),"adaptation learn @s 2 "+ZOMBIE)==1&&state.stage(ZOMBIE)==2,"learn by the full key did not set stage two: "+state.stage(ZOMBIE));
        check(h,!state.pending().containsKey(ZOMBIE),"A stage given by command left the adaptation running");
        check(h,state.revision()==revision+1,"A raised stage did not turn the wheel");
        check(h,said(sent,"message.adaptationwheel.stage")&&said(sent,"command.adaptationwheel.learn.one"),"learn did not announce the stage or report it");
        var saved=p.getPersistentData().getCompound(AdaptationWheel.MOD_ID).getList("Entries",Tag.TAG_COMPOUND);
        check(h,saved.stream().anyMatch(t -> ((CompoundTag)t).getString("Key").equals(ZOMBIE)&&((CompoundTag)t).getInt("Stage")==2),"learn did not save the stage");
        sent.clear();
        check(h,run(h,op(p),"adaptation learn @s 4 dry_riptide")==1&&state.stage(MetaMechanic.RIPTIDE.key)==4,"learn by the short name of a meta adaptation failed");
        check(h,said(sent,"meta.adaptationwheel.stage_complete"),"A meta adaptation's stage was not announced as one");
        sent.clear();revision=state.revision();
        check(h,run(h,op(p),"adaptation learn @s 1 zombie")==1&&state.stage(ZOMBIE)==1,"learn did not lower a stage");
        check(h,state.revision()==revision&&!said(sent,"message.adaptationwheel.stage"),"A lowered stage was announced or turned the wheel");
        check(h,run(h,op(p),"adaptation learn @s 3 mob:minecraft:husk")==1&&state.stage(ZOMBIE)==3&&state.snapshot("mob:minecraft:husk")==null,"An alias did not reach the zombie adaptation");
        check(h,run(h,op(p),"adaptation learn @s 5 zombie")==0&&state.stage(ZOMBIE)==3,"learn accepted stage five");
        h.succeed();
    }
    /** The menu's correction through the server entry the packet uses: a real analyzed row, saved in NBT, with its neighbours watched. */
    @GameTest(template="empty",timeoutTicks=60)
    public static void categoryFixMovesOnlyNameAndTime(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdFix",sent);var state=AdaptationEvents.state(p);
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        String key="damage:testmod:yeti_damage",other="damage:testmod:yeti_bite";
        for(String row:List.of(key,other)){
            SmartAttackAnalyzer.observe(p,row,false,AdaptationProfile.PHYSICAL,null,4,4);
            state.hit(row,false,AdaptationProfile.PHYSICAL);
        }
        var before=state.snapshot(key);var neighbour=state.snapshot(other);
        check(h,before!=null&&before.analysisObservations()==1&&SmartAttackAnalyzer.fixable(p,before),"The analyzed row cannot be corrected");
        AdaptationProfile verdict=before.profile(),chosen=verdict==AdaptationProfile.MAGIC?AdaptationProfile.COLD:AdaptationProfile.MAGIC;
        double multiplier=state.damageMultiplier(key);
        check(h,WheelNetwork.fixCategory(p,key,chosen),"The correction was refused");
        var after=state.snapshot(key);
        check(h,after.category()==chosen&&after.profile()==verdict,"Row: "+after.category()+", analyzer: "+after.profile());
        check(h,after.stage()==before.stage()&&after.hits()==before.hits()&&state.damageMultiplier(key)==multiplier,"The correction touched the stage or the protection");
        check(h,after.duration()!=before.duration(),"The stage time did not follow the category");
        check(h,state.snapshot(other).equals(neighbour),"Another row changed");
        var saved=p.getPersistentData().getCompound(AdaptationWheel.MOD_ID).getList("Entries",Tag.TAG_COMPOUND);
        check(h,saved.stream().anyMatch(t -> ((CompoundTag)t).getString("Key").equals(key)&&((CompoundTag)t).getString("ManualProfile").equals(chosen.name())),"The correction was not saved");
        check(h,saved.stream().noneMatch(t -> ((CompoundTag)t).getString("Key").equals(other)&&((CompoundTag)t).contains("ManualProfile")),"Another row got a correction in the save");
        var loaded=AdaptationEvents.loadState(p).snapshot(key);
        check(h,loaded.category()==chosen&&loaded.profile()==verdict&&loaded.duration()==after.duration(),"The correction did not load back");
        SmartAttackAnalyzer.observe(p,key,false,AdaptationProfile.PHYSICAL,null,4,4);
        check(h,state.snapshot(key).analysisObservations()==2&&state.snapshot(key).category()==chosen,"The analyzer stopped learning or overruled the player");
        check(h,AdaptationCatalogue.entries(p).stream().anyMatch(e -> e.key().equals(key)&&e.profile()==chosen),"The catalogue shows the old category");
        sent.clear();run(h,p.createCommandSourceStack(),"adaptation why "+key);
        check(h,said(sent,"command.adaptationwheel.why.manual"),"/adaptation why does not mention the correction");
        check(h,WheelNetwork.fixCategory(p,key,null)&&state.snapshot(key).category()==state.snapshot(key).profile(),"The analyzer did not come back");
        var removed=p.getPersistentData().getCompound(AdaptationWheel.MOD_ID).getList("Entries",Tag.TAG_COMPOUND);
        check(h,removed.stream().noneMatch(t -> ((CompoundTag)t).contains("ManualProfile")),"The correction stayed in the save");
        run(h,op(p),"adaptation learn @s 2 dry_riptide");run(h,op(p),"adaptation learn @s 2 zombie");
        check(h,!WheelNetwork.fixCategory(p,MetaMechanic.RIPTIDE.key,AdaptationProfile.COLD),"A mechanic got a category");
        check(h,!WheelNetwork.fixCategory(p,ZOMBIE,AdaptationProfile.COLD)&&state.snapshot(ZOMBIE).manualProfile()==null,"A row without analysis was corrected");
        check(h,!WheelNetwork.fixCategory(p,"damage:testmod:never_met",AdaptationProfile.COLD),"A row the player never met was corrected");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void forgetRemovesOneAdaptation(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdForget",sent);var state=AdaptationEvents.state(p);
        run(h,op(p),"adaptation learn @s 3 zombie");run(h,op(p),"adaptation learn @s 2 "+POISON);sent.clear();
        check(h,run(h,op(p),"adaptation forget @s zombie")==1&&state.snapshot(ZOMBIE)==null&&state.stage(POISON)==2,"forget removed the wrong adaptations");
        check(h,said(sent,"command.adaptationwheel.forget.one"),"forget did not report");
        sent.clear();
        check(h,run(h,op(p),"adaptation forget @s zombie")==0&&said(sent,"command.adaptationwheel.forget.none"),"Forgetting an adaptation nobody has did not fail");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void unknownAndAmbiguousNamesFail(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdNames",sent);var state=AdaptationEvents.state(p);
        check(h,run(h,op(p),"adaptation learn @s 2 no_such_adaptation")==0&&said(sent,"command.adaptationwheel.unknown"),"An unknown name did not fail");
        sent.clear();
        check(h,run(h,op(p),"adaptation learn @s 2 wither")==0&&said(sent,"command.adaptationwheel.ambiguous"),"wither (the boss and the effect) did not fail as ambiguous");
        check(h,state.snapshot("mob:minecraft:wither")==null&&state.snapshot("effect:minecraft:wither")==null,"A name that failed still changed a stage");
        // A learned key the catalogue does not list (a removed mod's) is found by its last part too.
        state.hit("mob:testmod:twin",false);state.hit("damage:testmod:twin",false);sent.clear();
        check(h,run(h,op(p),"adaptation info twin")==0&&said(sent,"command.adaptationwheel.ambiguous"),"Two learned keys with one last part were not ambiguous");
        check(h,run(h,op(p),"adaptation learn @s 2 mob:testmod:twin")==1&&state.stage("mob:testmod:twin")==2&&state.stage("damage:testmod:twin")==0,"The full key of an ambiguous name did not work");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void onlyOperatorsSetStagesOrTouchOthers(GameTestHelper h) {
        var p=player(h,"CmdPlain",null);var other=player(h,"CmdOther",null);
        AdaptationEvents.state(other).setStage(ZOMBIE,3,null);
        online(h,List.of(p,other),() -> {
            var plain=p.createCommandSourceStack().withPermission(0);
            // By name, not @s: a selector alone already needs permission 2.
            check(h,run(h,plain,"adaptation learn CmdPlain 2 zombie")==0&&AdaptationEvents.state(p).stage(ZOMBIE)==0,"A player without permission set a stage");
            check(h,run(h,plain,"adaptation forget CmdOther zombie")==0&&AdaptationEvents.state(other).stage(ZOMBIE)==3,"A player without permission made another forget");
            check(h,run(h,plain,"adaptation reset CmdOther")==0&&AdaptationEvents.state(other).stage(ZOMBIE)==3,"A player without permission reset another");
            check(h,run(h,plain,"adaptation off CmdOther")==0&&AdaptationEvents.state(other).enabled(),"A player without permission switched another's wheel off");
            check(h,run(h,plain,"adaptation status CmdOther")==0&&run(h,plain,"adaptation list CmdOther")==0,"A player without permission read another's adaptations");
            check(h,run(h,plain,"adaptation list")>0&&run(h,plain,"adaptation status")>0,"A player without permission could not read the own wheel");
            var root=dispatcher(h).getRoot().getChild("adaptation");
            check(h,!root.getChild("learn").canUse(plain)&&!root.getChild("forget").canUse(plain)&&!root.getChild("on").getChild("players").canUse(plain)
                &&!root.getChild("list").getChild("player").canUse(plain)&&root.getChild("list").canUse(plain)&&root.getChild("disable").canUse(plain),
                "The command tree sent to a player without permission is wrong");
            check(h,root.getChild("learn").canUse(op(p)),"An operator cannot use learn");
            var console=h.getLevel().getServer().createCommandSourceStack();
            check(h,run(h,console,"adaptation learn CmdOther 4 zombie")==1&&AdaptationEvents.state(other).stage(ZOMBIE)==4,"The console could not set a stage");
            check(h,run(h,console,"adaptation list")==0,"The console listed a wheel it does not wear");
            check(h,!root.canUse(console.withPermission(0)),"A source that is neither a player nor an operator can use /adaptation");
        });
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void disableStopsEvenAnUnstartedAdaptation(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdSwitch",sent);var state=AdaptationEvents.state(p);
        var plain=p.createCommandSourceStack().withPermission(0);
        check(h,run(h,plain,"adaptation disable zombie")==1&&!state.categoryEnabled(ZOMBIE),"disable did not switch off an adaptation that has not started");
        check(h,said(sent,"command.adaptationwheel.switched.off"),"disable did not report");
        check(h,!state.hit(ZOMBIE,false)&&!state.pending().containsKey(ZOMBIE),"A switched-off adaptation still started");
        check(h,run(h,plain,"adaptation enable zombie")==1&&state.categoryEnabled(ZOMBIE)&&state.hit(ZOMBIE,false),"enable did not let it start");
        check(h,run(h,plain,"adaptation disable zombie")==1,"disable of a running adaptation failed");
        for(int i=0;i<200;i++)state.tick();
        check(h,state.snapshot(ZOMBIE).work()==0,"A switched-off adaptation kept growing");
        run(h,op(p),"adaptation learn @s 4 zombie");
        check(h,state.damageMultiplier(ZOMBIE)==1,"A switched-off adaptation still protects");
        run(h,plain,"adaptation enable zombie");
        check(h,state.damageMultiplier(ZOMBIE)<1,"A switched-on adaptation does not protect");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void listPagesAndInfo(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdList",sent);var state=AdaptationEvents.state(p);
        for(int i=0;i<12;i++)state.setStage("mob:testmod:list_"+(char)('a'+i),i>=10?3:1,null);
        state.setCategoryEnabled("mob:testmod:list_l",false);
        var plain=p.createCommandSourceStack().withPermission(0);
        check(h,run(h,plain,"adaptation list")==12,"list did not count twelve adaptations");
        var header=lines(sent,"command.adaptationwheel.list.header");
        check(h,header.size()==1&&arg(header.get(0),1).equals("12")&&arg(header.get(0),2).equals("1")&&arg(header.get(0),3).equals("2"),"The first page header is wrong");
        var entries=lines(sent,"command.adaptationwheel.list.entry");
        check(h,entries.size()==10&&arg(entries.get(0),1).equals("3")&&arg(entries.get(1),1).equals("3")&&arg(entries.get(2),1).equals("1"),"The first page does not show ten lines, highest stage first");
        check(h,arg(entries.get(0),2).equals("4"),"An ordinary adaptation is not out of four stages");
        check(h,entries.get(1).getArgs()[3] instanceof Component off&&off.getContents() instanceof TranslatableContents note&&note.getKey().equals("command.adaptationwheel.list.off"),"A switched-off adaptation is not marked");
        var next=line(sent,"command.adaptationwheel.list.next");
        check(h,next!=null&&next.getStyle().getClickEvent()!=null&&next.getStyle().getClickEvent().getAction()==ClickEvent.Action.RUN_COMMAND
            &&next.getStyle().getClickEvent().getValue().equals("/adaptation list 2"),"The next page link is missing or wrong");
        sent.clear();
        check(h,run(h,plain,"adaptation list 2")==12&&lines(sent,"command.adaptationwheel.list.entry").size()==2&&line(sent,"command.adaptationwheel.list.next")==null,"The second page is wrong");
        sent.clear();run(h,plain,"adaptation list 9");
        check(h,arg(lines(sent,"command.adaptationwheel.list.header").get(0),2).equals("2"),"A page past the end did not show the last one");
        sent.clear();
        check(h,run(h,plain,"adaptation info dry_riptide")==1&&said(sent,"command.adaptationwheel.info.header")&&said(sent,"command.adaptationwheel.info.stage")
            &&said(sent,MetaMechanic.RIPTIDE.hintKey()),"info of a meta adaptation did not give its hint");
        sent.clear();
        check(h,run(h,plain,"adaptation info zombie")==1,"info of a creature failed");
        var protection=lines(sent,"command.adaptationwheel.info.protection");
        check(h,protection.size()==1&&arg(protection.get(0),3).equals(String.valueOf(AdaptationState.reductionPercent(4))),"info of a creature does not list protection by stage");
        sent.clear();
        check(h,run(h,plain,"adaptation info poison")==1&&said(sent,"command.adaptationwheel.info.effect"),"info of an effect does not list its shortening by stage");
        sent.clear();
        check(h,run(h,plain,"adaptation info cold")==1&&said(sent,"command.adaptationwheel.info.protection"),"info of the cold does not list its protection");
        sent.clear();
        check(h,run(h,plain,"adaptation info environment:darkness")==1&&lines(sent,"command.adaptationwheel.info.darkness").size()==1,"info of the darkness does not list its light by stage");
        var light=lines(sent,"command.adaptationwheel.info.darkness").get(0);
        for(int stage=1;stage<=4;stage++)check(h,arg(light,stage-1).equals(String.valueOf(DarknessLight.brightness(stage))),"info of the darkness gives the wrong light for stage "+stage);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void operatorsManageOtherPlayers(GameTestHelper h) {
        var mine=new ArrayList<Packet<?>>();var theirs=new ArrayList<Packet<?>>();
        var p=player(h,"CmdOperator",mine);var q=player(h,"CmdTarget",theirs);
        online(h,List.of(p,q),() -> {
            var op=op(p);var target=AdaptationEvents.state(q);var own=AdaptationEvents.state(p);
            check(h,run(h,op,"adaptation learn CmdTarget 3 zombie")==1&&target.stage(ZOMBIE)==3&&own.stage(ZOMBIE)==0,"learn changed the wrong player");
            check(h,said(theirs,"message.adaptationwheel.stage")&&said(mine,"command.adaptationwheel.learn.one"),"learn for another player told the wrong one");
            mine.clear();theirs.clear();
            check(h,run(h,op,"adaptation off CmdTarget")==1&&!target.enabled()&&own.enabled(),"off switched the wrong wheel");
            check(h,said(theirs,"message.adaptationwheel.disabled")&&said(mine,"command.adaptationwheel.for"),"off for another player told the wrong one");
            check(h,run(h,op,"adaptation on CmdTarget")==1&&target.enabled(),"on did not switch another's wheel on");
            mine.clear();
            check(h,run(h,op,"adaptation status CmdTarget")>0&&said(mine,"command.adaptationwheel.status.of")&&said(mine,"gui.adaptationwheel.detail"),"status of another player is wrong");
            mine.clear();
            check(h,run(h,op,"adaptation list CmdTarget")==1&&said(mine,"command.adaptationwheel.list.header"),"list of another player is wrong");
            mine.clear();
            check(h,run(h,op,"adaptation learn @a 2 "+POISON)==2&&own.stage(POISON)==2&&target.stage(POISON)==2&&said(mine,"command.adaptationwheel.learn.many"),"learn for several players failed");
            mine.clear();theirs.clear();
            check(h,run(h,op,"adaptation off @a")==2&&!target.enabled()&&!own.enabled()&&said(theirs,"message.adaptationwheel.disabled")
                &&lines(mine,"command.adaptationwheel.for.many").size()==1&&arg(lines(mine,"command.adaptationwheel.for.many").get(0),0).equals("2")
                &&!said(mine,"command.adaptationwheel.for"),"off for several players did not report once with the count");
            check(h,run(h,op,"adaptation on @a")==2&&target.enabled()&&own.enabled(),"on for several players failed");
            check(h,run(h,op,"adaptation reset CmdTarget")==1&&target.snapshots().isEmpty()&&own.stage(POISON)==2,"reset of another player reset the wrong wheel");
            check(h,run(h,op,"adaptation forget @a "+POISON)==1&&own.snapshot(POISON)==null,"forget for several players did not count who had it");
        });
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void suggestionsOfferKeysWithNames(GameTestHelper h) {
        var p=player(h,"CmdSuggest",null);var state=AdaptationEvents.state(p);
        state.setStage(ZOMBIE,1,null);state.setStage("mob:zzz_gone:own",1,null);
        var plain=p.createCommandSourceStack().withPermission(0);
        var zombie=suggest(h,plain,"adaptation info zom");
        check(h,zombie.stream().anyMatch(s -> s.getText().equals(ZOMBIE)&&s.getTooltip()!=null),"No suggestion with a name for the start of a word");
        check(h,suggest(h,plain,"adaptation info riptide").stream().anyMatch(s -> s.getText().equals(MetaMechanic.RIPTIDE.key)),"A word after _ is not matched");
        var all=suggest(h,plain,"adaptation info ");
        check(h,all.size()<=AdaptationCommands.SUGGESTIONS&&all.stream().anyMatch(s -> s.getText().equals("mob:zzz_gone:own")),"Too many suggestions, or the player's own adaptation is cut off: "+all.size());
        check(h,suggest(h,op(p),"adaptation learn @s 2 zom").stream().anyMatch(s -> s.getText().equals(ZOMBIE)),"learn does not suggest names");
        check(h,suggest(h,h.getLevel().getServer().createCommandSourceStack(),"adaptation info zom").isEmpty(),"The console got suggestions without a wheel");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void givenStageShortensARunningEffect(GameTestHelper h) {
        var p=player(h,"CmdEffect",null);
        p.addEffect(new MobEffectInstance(MobEffects.POISON,1000));
        // A new effect is marked for shortening when it arrives; let that mark pass at stage zero first.
        NegativeEffects.maintain(p);
        check(h,p.getEffect(MobEffects.POISON).getDuration()==1000,"Stage zero shortened the effect");
        check(h,run(h,op(p),"adaptation learn @s 2 "+POISON)==1,"learn of an effect failed");
        NegativeEffects.maintain(p);
        var poison=p.getEffect(MobEffects.POISON);
        check(h,poison!=null&&poison.getDuration()<1000,"A stage given by command did not shorten the running effect, as a completed stage does");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void whyExplainsTheDecision(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,"CmdWhy",sent);String key="damage:minecraft:lava";
        run(h,p.createCommandSourceStack(),"adaptation why "+key);
        check(h,said(sent,"command.adaptationwheel.why.none"),"An unseen source must say so");sent.clear();
        SmartAttackAnalyzer.observe(p,key,false,AdaptationProfile.FIRE,h.getLevel().damageSources().lava(),4,4);
        run(h,p.createCommandSourceStack(),"adaptation why "+key);
        check(h,said(sent,"command.adaptationwheel.why.category")&&said(sent,"command.adaptationwheel.why.reason")&&said(sent,"command.adaptationwheel.why.seen")&&said(sent,"command.adaptationwheel.why.facts"),"why must show category, reason, statistics and facts");
        h.succeed();
    }
    private static int run(GameTestHelper h,CommandSourceStack source,String command) {
        return h.getLevel().getServer().getCommands().performPrefixedCommand(source,command);
    }
    private static com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper h) { return h.getLevel().getServer().getCommands().getDispatcher(); }
    private static List<Suggestion> suggest(GameTestHelper h,CommandSourceStack source,String text) {
        var d=dispatcher(h);return d.getCompletionSuggestions(d.parse(text,source)).join().getList();
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
    /** Chat lines (not the action bar) with this translation key, at any depth: a failure comes wrapped in red. */
    private static List<TranslatableContents> lines(List<Packet<?>> sent,String key) {
        var found=new ArrayList<TranslatableContents>();
        for(var packet:sent)if(packet instanceof ClientboundSystemChatPacket chat&&!chat.overlay())find(chat.content(),key,found);
        return found;
    }
    private static void find(Component c,String key,List<TranslatableContents> found) {
        if(c.getContents() instanceof TranslatableContents text){
            if(text.getKey().equals(key))found.add(text);
            for(var a:text.getArgs())if(a instanceof Component inner)find(inner,key,found);
        }
        for(var sibling:c.getSiblings())find(sibling,key,found);
    }
    private static Component line(List<Packet<?>> sent,String key) {
        for(var packet:sent)if(packet instanceof ClientboundSystemChatPacket chat&&!chat.overlay()&&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().equals(key))return chat.content();
        return null;
    }
    private static boolean said(List<Packet<?>> sent,String key) { return !lines(sent,key).isEmpty(); }
    private static String arg(TranslatableContents text,int n) { return String.valueOf(text.getArgs()[n]); }
    private static void check(GameTestHelper h,boolean value,String message) { if(!value)h.fail(message); }
    /** Packets for the player go to {@code sent} when it is not null. */
    private static ServerPlayer player(GameTestHelper h,String name,List<Packet<?>> sent) {
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        var connection=new Connection(PacketFlow.SERVERBOUND);
        p.connection=sent==null?new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p):new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p){
            @Override public void send(Packet<?> packet){sent.add(packet);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){sent.add(packet);}
        };
        for(int i=0;i<60;i++)p.tick();
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        if(sent!=null)sent.clear();
        return p;
    }
}
