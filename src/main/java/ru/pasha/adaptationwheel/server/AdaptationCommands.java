package ru.pasha.adaptationwheel.server;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import ru.pasha.adaptationwheel.core.AdaptationAnalysis;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.AdaptationState;
import ru.pasha.adaptationwheel.core.AdaptationTimings;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.net.WheelNetwork;

/**
 * /adaptation: the wheel's own commands for the player's own character, and (permission 2, like /give) the same for
 * other players plus setting stages. An adaptation key such as mob:minecraft:zombie has colons, which an unquoted
 * Brigadier word does not take, so it is always the last argument, a greedy string; a unique last part ("zombie",
 * "dry_riptide") is enough.
 */
public final class AdaptationCommands {
    public static final int OPERATOR=2,PAGE=10,SUGGESTIONS=200;
    private static final DynamicCommandExceptionType UNKNOWN=new DynamicCommandExceptionType(key -> Component.translatable("command.adaptationwheel.unknown",key));
    private static final Dynamic2CommandExceptionType AMBIGUOUS=new Dynamic2CommandExceptionType((key,options) -> Component.translatable("command.adaptationwheel.ambiguous",key,options));
    private static final DynamicCommandExceptionType NOT_LEARNED=new DynamicCommandExceptionType(key -> Component.translatable("command.adaptationwheel.forget.none",key));
    private AdaptationCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adaptation")
            .requires(source -> source.getEntity() instanceof ServerPlayer || source.hasPermission(OPERATOR))
            .executes(c -> menu(c.getSource()))
            .then(Commands.literal("menu").executes(c -> menu(c.getSource())))
            .then(Commands.literal("on").executes(c -> toggle(c.getSource(),self(c),true))
                .then(players().executes(c -> toggle(c.getSource(),EntityArgument.getPlayers(c,"players"),true))))
            .then(Commands.literal("off").executes(c -> toggle(c.getSource(),self(c),false))
                .then(players().executes(c -> toggle(c.getSource(),EntityArgument.getPlayers(c,"players"),false))))
            .then(Commands.literal("reset").executes(c -> reset(c.getSource(),self(c)))
                .then(players().executes(c -> reset(c.getSource(),EntityArgument.getPlayers(c,"players")))))
            .then(Commands.literal("status").executes(c -> status(c.getSource(),self(c).get(0)))
                .then(player().executes(c -> status(c.getSource(),EntityArgument.getPlayer(c,"player")))))
            .then(Commands.literal("list").executes(c -> list(c.getSource(),self(c).get(0),1))
                .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c -> list(c.getSource(),self(c).get(0),IntegerArgumentType.getInteger(c,"page"))))
                .then(player().executes(c -> list(c.getSource(),EntityArgument.getPlayer(c,"player"),1))
                    .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c -> list(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"page"))))))
            .then(Commands.literal("info").then(adaptation().executes(c -> info(c.getSource(),text(c)))))
            .then(Commands.literal("why").then(adaptation().executes(c -> why(c.getSource(),text(c)))))
            .then(Commands.literal("enable").then(adaptation().executes(c -> switchOne(c.getSource(),text(c),true))))
            .then(Commands.literal("disable").then(adaptation().executes(c -> switchOne(c.getSource(),text(c),false))))
            .then(Commands.literal("learn").requires(source -> source.hasPermission(OPERATOR))
                .then(Commands.argument("players",EntityArgument.players())
                    .then(Commands.argument("stage",IntegerArgumentType.integer(0,4))
                        .then(adaptation().executes(c -> learn(c.getSource(),EntityArgument.getPlayers(c,"players"),IntegerArgumentType.getInteger(c,"stage"),text(c)))))))
            .then(Commands.literal("forget").requires(source -> source.hasPermission(OPERATOR))
                .then(Commands.argument("players",EntityArgument.players())
                    .then(adaptation().executes(c -> forget(c.getSource(),EntityArgument.getPlayers(c,"players"),text(c)))))));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,?> players() {
        return Commands.argument("players",EntityArgument.players()).requires(source -> source.hasPermission(OPERATOR));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,?> player() {
        return Commands.argument("player",EntityArgument.player()).requires(source -> source.hasPermission(OPERATOR));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,String> adaptation() {
        return Commands.argument("adaptation",StringArgumentType.greedyString()).suggests(SUGGEST);
    }
    private static String text(CommandContext<CommandSourceStack> c) { return StringArgumentType.getString(c,"adaptation"); }
    private static List<ServerPlayer> self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return List.of(c.getSource().getPlayerOrException());
    }

    // ---- adaptations by name ----------------------------------------------------------------------------------

    /** This server's catalogue plus whatever the player has learned that it no longer lists (a removed mod's). */
    static Map<String,CatalogueEntry> known(ServerPlayer player) {
        Map<String,CatalogueEntry> known=new LinkedHashMap<>();
        for (var entry:AdaptationCatalogue.entries(player)) known.put(entry.key(),entry);
        for (var snapshot:AdaptationEvents.state(player).snapshots())
            known.putIfAbsent(snapshot.key(),new CatalogueEntry(snapshot.key(),DamageCategories.translationKey(snapshot.key()),DamageCategories.fallbackName(snapshot.key()),snapshot.category()));
        return known;
    }
    /** The exact key, its alias, or the one key that ends with ":" + the text. */
    static CatalogueEntry resolve(Map<String,CatalogueEntry> known,String text) throws CommandSyntaxException {
        String typed=text.trim();
        var exact=known.get(typed);if(exact==null)exact=known.get(DamageCategories.canonicalKey(typed));
        if(exact!=null)return exact;
        var matches=known.values().stream().filter(entry -> entry.key().endsWith(":"+typed)).toList();
        if(matches.size()==1)return matches.get(0);
        if(matches.isEmpty())throw UNKNOWN.create(typed);
        String options=String.join(", ",matches.stream().limit(5).map(CatalogueEntry::key).toList())+(matches.size()>5?", …":"");
        throw AMBIGUOUS.create(typed,options);
    }
    /** The typed text at the start of the key or of any part after ":" or "_"; the player's own adaptations first. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST=(c,builder) -> {
        if(!(c.getSource().getEntity() instanceof ServerPlayer player))return builder.buildFuture();
        String typed=builder.getRemaining().toLowerCase(Locale.ROOT);
        var own=AdaptationEvents.state(player).snapshots().stream().map(AdaptationState.Snapshot::key).toList();
        var keys=new LinkedHashSet<String>(own);keys.addAll(known(player).keySet());
        int count=0;
        for(String key:keys)if(matches(key,typed)&&count++<SUGGESTIONS)builder.suggest(key,DamageCategories.displayName(key));
        return builder.buildFuture();
    };
    static boolean matches(String key,String typed) {
        String lower=key.toLowerCase(Locale.ROOT);
        if(lower.startsWith(typed))return true;
        for(int i=0;i<lower.length();i++){char ch=lower.charAt(i);if((ch==':'||ch=='_')&&lower.startsWith(typed,i+1))return true;}
        return false;
    }

    // ---- the player's own wheel, or (operators) another player's -------------------------------------------------

    private static int menu(CommandSourceStack source) throws CommandSyntaxException {
        WheelNetwork.openMenu(source.getPlayerOrException()); return 1;
    }
    private static int toggle(CommandSourceStack source,Collection<ServerPlayer> players,boolean enabled) {
        Component message=Component.translatable(enabled ? "message.adaptationwheel.enabled" : "message.adaptationwheel.disabled");
        for (ServerPlayer player:players) {
            AdaptationEvents.state(player).setEnabled(enabled);
            AdaptationEvents.discardQueuedDamage(player); AdaptationEvents.saveAndSync(player);
        }
        tell(source,players,message);
        return players.size();
    }
    private static int reset(CommandSourceStack source,Collection<ServerPlayer> players) {
        Component message=Component.translatable("message.adaptationwheel.reset");
        for (ServerPlayer player:players) {
            AdaptationEvents.state(player).reset();
            SmartAttackAnalyzer.clear(player);
            VoidTextAdaptation.reset(player);
            AdaptationEvents.discardQueuedDamage(player); AdaptationEvents.saveAndSync(player);
        }
        tell(source,players,message);
        return players.size();
    }
    /**
     * Every player hears it. An operator who changed someone else's wheel sees it with the name, or once with the
     * count for several players, as learn and forget do (and so do other operators).
     */
    private static void tell(CommandSourceStack source,Collection<ServerPlayer> players,Component message) {
        for (ServerPlayer player:players) if (source.getEntity()!=player) player.sendSystemMessage(message);
        ServerPlayer only=players.size()==1 ? players.iterator().next() : null;
        if (only!=null && source.getEntity()==only) source.sendSuccess(() -> message,false);
        else if (only!=null) source.sendSuccess(() -> Component.translatable("command.adaptationwheel.for",only.getDisplayName(),message),true);
        else source.sendSuccess(() -> Component.translatable("command.adaptationwheel.for.many",players.size(),message),true);
    }
    private static int status(CommandSourceStack source,ServerPlayer player) {
        AdaptationState state=AdaptationEvents.state(player);
        if (source.getEntity()!=player) source.sendSuccess(() -> Component.translatable("command.adaptationwheel.status.of",player.getDisplayName()),false);
        if (state.snapshots().isEmpty()) source.sendSuccess(() -> Component.translatable("command.adaptationwheel.none"),false);
        for (AdaptationState.Snapshot entry : state.snapshots())
            source.sendSuccess(() -> Component.translatable("gui.adaptationwheel.detail",
                DamageCategories.displayName(entry.key()),entry.reduction(),entry.remaining()/20,
                String.format(java.util.Locale.ROOT,"%.2f",entry.speed()+state.experiencePercent(entry.key())/100.0)),false);
        return Math.max(1,state.snapshots().size());
    }
    /** One line per adaptation, highest stage first; a click on a line starts /adaptation info, the last line turns the page. */
    private static int list(CommandSourceStack source,ServerPlayer player,int page) {
        var entries=new ArrayList<>(AdaptationEvents.state(player).snapshots());
        entries.sort(Comparator.comparingInt(AdaptationState.Snapshot::stage).reversed().thenComparing(AdaptationState.Snapshot::key));
        if (entries.isEmpty()) { source.sendSuccess(() -> Component.translatable("command.adaptationwheel.none"),false); return 1; }
        int pages=(entries.size()+PAGE-1)/PAGE,shown=Math.min(page,pages);
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.list.header",player.getDisplayName(),entries.size(),shown,pages).withStyle(ChatFormatting.GOLD),false);
        for (var entry:entries.subList((shown-1)*PAGE,Math.min(entries.size(),shown*PAGE))) {
            String note=!entry.enabled()?"command.adaptationwheel.list.off":entry.running()?"command.adaptationwheel.list.running":"command.adaptationwheel.list.idle";
            MutableComponent line=Component.translatable("command.adaptationwheel.list.entry",DamageCategories.displayName(entry.key()),entry.stage(),top(entry),Component.translatable(note));
            source.sendSuccess(() -> line.withStyle(style -> style.withColor(entry.enabled()?ChatFormatting.WHITE:ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(entry.key())))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/adaptation info "+entry.key()))),false);
        }
        if (shown<pages) {
            String next="/adaptation list "+(source.getEntity()==player?"":player.getScoreboardName()+" ")+(shown+1);
            source.sendSuccess(() -> Component.translatable("command.adaptationwheel.list.next").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,next))),false);
        }
        return entries.size();
    }
    /** The last stage: a boss's fifth, existence, when the server has it. */
    private static int top(AdaptationState.Snapshot entry) {
        return entry!=null && entry.boss() && AdaptationTimings.existenceEnabled() ? 5 : 4;
    }
    /** What the adaptation is, the player's stage, and what its stages give. */
    private static int info(CommandSourceStack source,String text) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var entry=resolve(known(player),text);String key=entry.key();
        var state=AdaptationEvents.state(player);var snapshot=state.snapshot(key);
        int stage=state.stage(key);boolean enabled=state.categoryEnabled(key);
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.info.header",DamageCategories.displayName(key).copy().withStyle(ChatFormatting.GOLD),key),false);
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.info.stage",stage,top(snapshot),
            Component.translatable(!enabled?"command.adaptationwheel.list.off":snapshot!=null&&snapshot.running()?"command.adaptationwheel.list.running":"command.adaptationwheel.list.idle")),false);
        MutableComponent about=about(key);
        if (about!=null) source.sendSuccess(() -> about.withStyle(ChatFormatting.GRAY),false);
        return 1;
    }
    /** Why the analyzer decided what it did: the category, the reason, the facts behind it and how dangerous the source looks. */
    private static int why(CommandSourceStack source,String text) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        String key=resolve(known(player),text).key();
        var state=AdaptationEvents.state(player);var snapshot=state.snapshot(key);var memory=SmartAttackAnalyzer.memory(player,key);
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.info.header",DamageCategories.displayName(key).copy().withStyle(ChatFormatting.GOLD),key),false);
        if(snapshot==null||memory==null||snapshot.analysisObservations()==0){
            source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.none").withStyle(ChatFormatting.GRAY),false);return 1;
        }
        var profile=snapshot.profile();
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.category",Component.translatable(profile.translationKey()),snapshot.analysisConfidence()),false);
        if(snapshot.manualProfile()!=null)source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.manual",Component.translatable(snapshot.manualProfile().translationKey())),false);
        String reason=snapshot.analysisReason();
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.reason",Component.translatableWithFallback("analysis.adaptationwheel.reason."+reason,reason)),false);
        source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.seen",memory.observations(),Math.round(memory.meanRisk()*100),Math.round(memory.peakRisk()*100)),false);
        MutableComponent facts=null;
        for(var fact:AdaptationAnalysis.facts(memory.evidenceMask())){
            Component line=factName(fact);if(line==null)continue;
            facts=facts==null?line.copy():facts.append(", ").append(line);
        }
        if(facts!=null){Component all=facts;source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.facts",all),false);}
        MutableComponent votes=null;
        for(var row:memory.votes().entrySet()){
            Component line=Component.translatable(row.getKey().translationKey()).append(" "+row.getValue());
            votes=votes==null?line.copy():votes.append(", ").append(line);
        }
        if(votes!=null){Component all=votes;source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.votes",all),false);}
        int mastered=state.mastered(profile,key),experience=state.experiencePercent(key);
        if(experience>0)source.sendSuccess(() -> Component.translatable("command.adaptationwheel.why.experience",mastered,experience),false);
        return 1;
    }
    /** One saved fact in words; null for bookkeeping that says nothing to a player. */
    private static Component factName(AdaptationAnalysis.Evidence fact) {
        String name=fact.name();
        switch(fact){
            case SOURCE_CHECKED,BENEFICIAL_EFFECT:return null;
            case CONTROL,HARMFUL_EFFECT,ATTRIBUTED_EFFECT,COMPAT_PROFILE,DIRECT_HEALTH:return Component.translatable("command.adaptationwheel.why.fact."+name.toLowerCase(Locale.ROOT));
            default:break;
        }
        String kind=name.startsWith("REGISTRY_")?"registry":name.startsWith("TAG_")?"tag":"native";
        var profile=AdaptationProfile.valueOf(name.substring(name.indexOf('_')+1));
        return Component.translatable("command.adaptationwheel.why.fact."+kind,Component.translatable(profile.translationKey()));
    }
    /** What the stages give, in the J menu's words where it has them; null for an adaptation with nothing more to say. */
    private static MutableComponent about(String key) {
        var mechanic=MetaMechanic.fromKey(key);
        if (mechanic!=null) return Component.translatable(mechanic.hintKey());
        if (key.equals(InfinityAdaptation.KEY)) return Component.translatable("gui.adaptationwheel.infinity_explained");
        if (key.equals(AdaptationProfile.DARK_KEY)) return Component.translatable("command.adaptationwheel.info.darkness",
            DarknessLight.brightness(1),DarknessLight.brightness(2),DarknessLight.brightness(3),DarknessLight.brightness(4));
        boolean effect=key.startsWith("effect:") || key.startsWith("condition:");
        if (!effect && !key.startsWith("mob:") && !key.startsWith("damage:") && !key.startsWith("attack:") && !key.equals(AdaptationProfile.COLD_KEY)) return null;
        return Component.translatable(effect?"command.adaptationwheel.info.effect":"command.adaptationwheel.info.protection",
            AdaptationState.reductionPercent(1),AdaptationState.reductionPercent(2),AdaptationState.reductionPercent(3),AdaptationState.reductionPercent(4));
    }
    /** The J-menu switch for one adaptation; one that has not started can be switched off too, so it never starts. */
    private static int switchOne(CommandSourceStack source,String text,boolean enabled) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var entry=resolve(known(player),text);
        AdaptationEvents.state(player).setCategoryEnabled(entry.key(),enabled,entry.profile());
        AdaptationEvents.discardQueuedDamage(player); AdaptationEvents.saveAndSync(player);
        source.sendSuccess(() -> Component.translatable(enabled?"command.adaptationwheel.switched.on":"command.adaptationwheel.switched.off",DamageCategories.displayName(entry.key())),false);
        return 1;
    }

    // ---- operators ----------------------------------------------------------------------------------------------

    private static int learn(CommandSourceStack source,Collection<ServerPlayer> players,int stage,String text) throws CommandSyntaxException {
        var entry=resolve(known(players.iterator().next()),text);String key=entry.key();
        for (ServerPlayer player:players) {
            int before=AdaptationEvents.state(player).setStage(key,stage,entry.profile());
            if (stage>before) AdaptationEvents.stageGiven(player,key);
            AdaptationEvents.discardQueuedDamage(player); AdaptationEvents.saveAndSync(player);
        }
        Component name=DamageCategories.displayName(key);
        if (players.size()==1) source.sendSuccess(() -> Component.translatable("command.adaptationwheel.learn.one",players.iterator().next().getDisplayName(),name,stage),true);
        else source.sendSuccess(() -> Component.translatable("command.adaptationwheel.learn.many",players.size(),name,stage),true);
        return players.size();
    }
    private static int forget(CommandSourceStack source,Collection<ServerPlayer> players,String text) throws CommandSyntaxException {
        var entry=resolve(known(players.iterator().next()),text);String key=entry.key();
        int forgotten=0;
        for (ServerPlayer player:players) if (AdaptationEvents.state(player).forget(key)) {
            forgotten++; AdaptationEvents.discardQueuedDamage(player); AdaptationEvents.saveAndSync(player);
        }
        if (forgotten==0) throw NOT_LEARNED.create(DamageCategories.displayName(key));
        int count=forgotten;Component name=DamageCategories.displayName(key);
        if (players.size()==1) source.sendSuccess(() -> Component.translatable("command.adaptationwheel.forget.one",players.iterator().next().getDisplayName(),name),true);
        else source.sendSuccess(() -> Component.translatable("command.adaptationwheel.forget.many",count,name),true);
        return count;
    }
}
