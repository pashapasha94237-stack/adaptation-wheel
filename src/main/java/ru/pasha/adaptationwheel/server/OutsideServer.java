package ru.pasha.adaptationwheel.server;

import com.mojang.logging.LogUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;

/**
 * Adaptations to things outside the world: the mod pack, the wheel's own version, the world folder,
 * crashes, the system clock, the log, config files, the network, commands, idleness and open menus.
 * Facts that outlive a world are kept in {@code <game dir>/adaptationwheel/}.
 */
public final class OutsideServer {
    private record Event(long id,long expires,IntFunction<List<Component>> text){}
    /** Start-of-session facts wait for a wearer; moment-to-moment news is stale after a minute. */
    private static final Set<MetaMechanic> MOMENTARY=EnumSet.of(MetaMechanic.CLOCK,MetaMechanic.NETWORK,MetaMechanic.CONFIG,MetaMechanic.LOGS);
    private static final EnumMap<MetaMechanic,Event> EVENTS=new EnumMap<>(MetaMechanic.class);
    private static final Map<ServerPlayer,OutsideSignals.IdleClock> IDLE=new WeakHashMap<>();
    private static final Set<ServerPlayer> AFK_SHIELD=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerPlayer,Long> MENU_OPENED=new WeakHashMap<>(),MENU_WINDOW=new WeakHashMap<>();
    private static final Set<String> CHEATS=Set.of("kill","effect","gamemode","give","tp","teleport","time","weather","summon","damage",
            "clear","xp","experience","enchant","attribute","setblock","fill","difficulty","gamerule","item","loot","spreadplayers","ride","defaultgamemode");
    public static final int AFK_EXPOSE=3*60*20,AFK_SHIELD_AFTER=60*20,MENU_SHIELD=20*20,MENU_REARM=30*20;
    private static final OutsideSignals.ClockWatch CLOCK=new OutsideSignals.ClockWatch();
    private static final Random IDS=new Random();
    private static long sessionStart=System.nanoTime(),logsPostedAt=Long.MIN_VALUE/2;
    private static Boolean lastOnline;private static String crashWorld="",worldId="";private static Path lock;
    private OutsideServer(){}

    // ---------- shared plumbing ----------
    static Path home(){return FMLPaths.GAMEDIR.get().resolve("adaptationwheel");}
    private static Properties read(Path file){
        var p=new Properties();
        if(Files.isRegularFile(file))try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(r);}catch(IOException|IllegalArgumentException e){LogUtils.getLogger().warn("Unreadable {}: {}",file,e.toString());}
        return p;
    }
    private static void write(Path file,Properties p){
        try{Files.createDirectories(file.getParent());try(Writer w=Files.newBufferedWriter(file,StandardCharsets.UTF_8)){p.store(w,"Adaptation Wheel: facts that outlive worlds");}}
        catch(IOException e){LogUtils.getLogger().warn("Could not save {}: {}",file,e.toString());}
    }
    static MutableComponent line(String key,Object... args){return Component.translatable("meta.adaptationwheel.outside.prefix").withStyle(ChatFormatting.GOLD).append(Component.translatable(key,args).withStyle(ChatFormatting.YELLOW));}
    static MutableComponent detail(String text){return Component.literal("  "+text).withStyle(ChatFormatting.GRAY);}
    /** A fact every equipped player learns once: starts the adaptation and shows the stage's message. */
    public static void post(MetaMechanic m,IntFunction<List<Component>> text){
        // Ids are compared for equality only, so a system clock moved backwards cannot hide new events.
        long id;do id=IDS.nextLong();while(id==0);
        EVENTS.put(m,new Event(id,MOMENTARY.contains(m)?System.nanoTime()+60_000_000_000L:Long.MAX_VALUE,text));
    }
    private static void deliver(ServerPlayer p){
        CompoundTag seen=MetaServer.meta(p).getCompound("Seen");long now=System.nanoTime();
        for(var e:EVENTS.entrySet()){
            MetaMechanic m=e.getKey();Event event=e.getValue();
            if(seen.getLong(m.id)==event.id||event.expires!=Long.MAX_VALUE&&now-event.expires>0||!MetaServer.active(p,m))continue;
            seen.putLong(m.id,event.id);
            int stage=AdaptationEvents.state(p).stage(m.key);
            for(Component c:event.text.apply(stage))p.sendSystemMessage(c);
            // The protection is for the rejoin itself, not for a wheel put on much later.
            if(m==MetaMechanic.CRASH&&stage>=4&&!crashWorld.isEmpty()&&crashWorld.equals(worldId)&&p.tickCount<2400)shieldAfterCrash(p);
            MetaServer.expose(p,m);
        }
        MetaServer.meta(p).put("Seen",seen);
    }

    // ---------- server start: mod pack, own version, world folder, crash ----------
    @SubscribeEvent public static void started(ServerStartedEvent event){
        MinecraftServer server=event.getServer();sessionStart=System.nanoTime();CLOCK.reset();lastOnline=null;
        NetworkWatch.start();
        // None of these may ever stop a world from starting: a broken file only costs the event.
        try{Path memoryFile=home().resolve("outside-memory.properties");Properties memory=read(memoryFile);modsAndVersion(server,memory);write(memoryFile,memory);}
        catch(RuntimeException e){LogUtils.getLogger().warn("Mod list / version memory skipped: {}",e.toString());}
        try{worldName(server);}catch(RuntimeException e){worldId="";LogUtils.getLogger().warn("World name memory skipped: {}",e.toString());}
        try{crash(server);}catch(RuntimeException e){LogUtils.getLogger().warn("Crash memory skipped: {}",e.toString());}
    }
    /** "4.29.1-trained-online" (or an older "4.20.1-offline") to "4.29.1", the release its changelog line is kept under. */
    public static String release(String version){return version.replaceFirst("-.*$","");}
    private static void modsAndVersion(MinecraftServer server,Properties memory){
        Map<String,String> mods=new TreeMap<>();
        for(var info:ModList.get().getMods())mods.put(info.getModId(),info.getVersion().toString());
        String stored=memory.getProperty("mods");
        if(stored!=null){
            var changes=OutsideSignals.diffMods(OutsideSignals.decodeMods(stored),mods);
            if(!changes.empty()){
                Map<String,Integer> entities=new HashMap<>(),damage=new HashMap<>();
                for(var id:BuiltInRegistries.ENTITY_TYPE.keySet())entities.merge(id.getNamespace(),1,Integer::sum);
                for(var id:server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).keySet())damage.merge(id.getNamespace(),1,Integer::sum);
                post(MetaMechanic.MODS,stage->{
                    List<Component> out=new ArrayList<>();
                    out.add(line("meta.adaptationwheel.foreign_mods.event",changes.added().size(),changes.removed().size(),changes.updated().size()));
                    if(stage<1)return out;
                    int shown=0;
                    for(String id:changes.added())if(shown++<8)out.add(stage>=4?detail("+ "+id+" · "+entities.getOrDefault(id,0)+" mob · "+damage.getOrDefault(id,0)+" dmg"):detail("+ "+id));
                    for(String id:changes.removed())if(shown++<12)out.add(detail("− "+id));
                    if(stage>=4)for(String id:changes.updated())if(shown++<16)out.add(detail("↻ "+id));
                    return out;
                });
            }
        }
        memory.setProperty("mods",OutsideSignals.encodeMods(mods));
        String version=ModList.get().getModContainerById(AdaptationWheel.MOD_ID).map(c->c.getModInfo().getVersion().toString()).orElse("?");
        String old=memory.getProperty("wheelVersion");
        if(old!=null&&!old.equals(version)){
            String base=release(version);
            post(MetaMechanic.UPDATE,stage->{
                List<Component> out=new ArrayList<>();out.add(line("meta.adaptationwheel.wheel_update.event",old,version));
                if(stage>=4)out.add(Component.translatableWithFallback("meta.adaptationwheel.changelog."+base,"—").withStyle(ChatFormatting.GRAY));
                return out;
            });
        }
        memory.setProperty("wheelVersion",version);
    }
    static String worldFolder(MinecraftServer server){
        Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return root.getFileName()==null?"":root.getFileName().toString();
    }
    private static void worldName(MinecraftServer server){
        Path marker=server.getWorldPath(LevelResource.ROOT).resolve("adaptationwheel-world.properties");
        Properties p=read(marker);String folder=worldFolder(server),name=server.getWorldData().getLevelName();
        String oldFolder=p.getProperty("folder"),oldName=p.getProperty("name"),history=p.getProperty("history","");
        if(!p.containsKey("id"))p.setProperty("id",UUID.randomUUID().toString());
        worldId=p.getProperty("id");
        if(oldFolder!=null&&(!oldFolder.equals(folder)||!Objects.equals(oldName,name))){
            boolean titleChanged=oldName!=null&&!oldName.equals(name);
            String previous=titleChanged?oldName:oldFolder,current=titleChanged?name:folder;
            String allNames=history.isEmpty()?previous:history+" → "+previous;
            post(MetaMechanic.RENAME,stage->{
                List<Component> out=new ArrayList<>();out.add(line("meta.adaptationwheel.world_rename.event",previous,current));
                if(stage>=4)out.add(line("meta.adaptationwheel.world_rename.full",allNames,p.getProperty("created","?")));
                return out;
            });
            p.setProperty("history",allNames.length()>400?allNames.substring(allNames.length()-400):allNames);
        }
        if(!p.containsKey("created"))p.setProperty("created",LocalDate.now().toString());
        p.setProperty("folder",folder);p.setProperty("name",name);write(marker,p);
    }
    private static void crash(MinecraftServer server){
        lock=home().resolve("session.lock");Properties previous=read(lock);
        if(Files.isRegularFile(lock)&&previous.containsKey("started")){
            long parsed;try{parsed=Long.parseLong(previous.getProperty("started","0").trim());}catch(NumberFormatException damaged){parsed=0;}
            long started=parsed;
            String world=previous.getProperty("name",previous.getProperty("folder","?")),when=DateTimeFormatter.ofPattern("dd.MM HH:mm").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(started),ZoneId.systemDefault()));
            String report="";
            try(var files=Files.list(FMLPaths.GAMEDIR.get().resolve("crash-reports"))){
                report=files.filter(f->f.toFile().lastModified()>=started).max(Comparator.comparingLong(f->f.toFile().lastModified())).map(f->f.getFileName().toString()).orElse("");
            }catch(IOException|UncheckedIOException ignored){}
            crashWorld=previous.getProperty("id","");String file=report;
            post(MetaMechanic.CRASH,stage->{
                List<Component> out=new ArrayList<>();out.add(line("meta.adaptationwheel.crash_memory.event",world,when));
                if(stage>=1&&!file.isEmpty())out.add(detail("crash-reports/"+file));
                if(stage>=4)out.add(line(crashWorld.equals(worldId)?"meta.adaptationwheel.crash_memory.full":"meta.adaptationwheel.crash_memory.other_world"));
                return out;
            });
        }
        Properties now=new Properties();now.setProperty("started",Long.toString(System.currentTimeMillis()));now.setProperty("folder",worldFolder(server));
        now.setProperty("name",server.getWorldData().getLevelName());now.setProperty("id",worldId);write(lock,now);
    }
    private static void shieldAfterCrash(ServerPlayer p){
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,600,4,false,false,true));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,600,0,false,false,true));
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,600,0,false,false,true));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        // A clean stop removes the marker; a crash leaves it for the next start.
        if(lock!=null)try{Files.deleteIfExists(lock);}catch(IOException ignored){}
        EVENTS.clear();IDLE.clear();AFK_SHIELD.clear();MENU_OPENED.clear();MENU_WINDOW.clear();crashWorld=worldId="";lock=null;
    }

    // ---------- every second: clock, network, log ----------
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%20!=0)return;
        long shift=CLOCK.sample(System.currentTimeMillis(),System.nanoTime());
        if(shift!=0){
            long real=(System.nanoTime()-sessionStart)/1_000_000L;String moved=OutsideSignals.duration(shift),session=OutsideSignals.duration(real).substring(1);
            post(MetaMechanic.CLOCK,stage->stage>=4?List.of(line("meta.adaptationwheel.clock_shift.event",moved),line("meta.adaptationwheel.clock_shift.full",session)):List.of(line("meta.adaptationwheel.clock_shift.event",moved)));
        }
        Boolean online=NetworkWatch.online();
        if(online!=null&&lastOnline!=null&&!online.equals(lastOnline)){
            if(!online)post(MetaMechanic.NETWORK,stage->stage>=4?List.of(line("meta.adaptationwheel.connection_loss.lost"),line("meta.adaptationwheel.connection_loss.full")):List.of(line("meta.adaptationwheel.connection_loss.lost")));
            else{String after=OutsideSignals.duration((System.nanoTime()-lostAt)/1_000_000L).substring(1);post(MetaMechanic.NETWORK,stage->List.of(line("meta.adaptationwheel.connection_loss.back",after)));}
        }
        if(online!=null&&!online.equals(lastOnline)&&!online)lostAt=System.nanoTime();
        lastOnline=online;
        int errors=LogWatch.errorsLastMinute();long now=System.nanoTime();
        if(errors>=LogWatch.ERRORS_PER_MINUTE&&now-logsPostedAt>300_000_000_000L){
            logsPostedAt=now;
            post(MetaMechanic.LOGS,stage->stage>=4?List.of(line("meta.adaptationwheel.log_errors.event",errors),line("meta.adaptationwheel.log_errors.full",LogWatch.suppressed())):List.of(line("meta.adaptationwheel.log_errors.event",errors)));
        }
        LogWatch.install();
        boolean trim=false;for(ServerPlayer p:server.getPlayerList().getPlayers())if(MetaServer.full(p,MetaMechanic.LOGS)){trim=true;break;}
        LogWatch.serverWants(trim);
    }
    private static long lostAt=System.nanoTime();
    public static void configChanged(String file,List<String> lines){
        post(MetaMechanic.CONFIG,stage->{
            List<Component> out=new ArrayList<>();out.add(line("meta.adaptationwheel.config_edit.event",file,lines.size()));
            if(stage>=4)for(int i=0;i<Math.min(8,lines.size());i++)out.add(detail(lines.get(i)));
            return out;
        });
    }
    /** Offline build ignores this; the online build skips remote requests while the computer is offline. */
    public static boolean offlineFor(ServerPlayer p){return NetworkWatch.offline()&&MetaServer.full(p,MetaMechanic.NETWORK);}

    // ---------- every second per player: delivery, idleness, open menus ----------
    static void playerTick(ServerPlayer p){
        deliver(p);
        var clock=IDLE.get(p);
        // A fresh login is not a return from being idle: the old idle time belongs to the last session.
        if(clock==null){clock=new OutsideSignals.IdleClock();IDLE.put(p,clock);MetaServer.meta(p).putLong("IdleTicks",0);}
        long idle=clock.update(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),p.tickCount);
        boolean wasShielded=AFK_SHIELD.contains(p);
        if(idle==0){
            long away=MetaServer.meta(p).getLong("IdleTicks");
            if(away>=AFK_EXPOSE&&AdaptationEvents.state(p).stage(MetaMechanic.AFK.key)>=1&&MetaServer.active(p,MetaMechanic.AFK))
                p.displayClientMessage(Component.translatable("meta.adaptationwheel.idle_player.back",OutsideSignals.duration(away*50).substring(1)),true);
            AFK_SHIELD.remove(p);
        }
        MetaServer.meta(p).putLong("IdleTicks",idle);
        if(idle>=AFK_EXPOSE)MetaServer.expose(p,MetaMechanic.AFK);
        if(idle>=AFK_SHIELD_AFTER&&MetaServer.full(p,MetaMechanic.AFK)&&!wasShielded){
            AFK_SHIELD.add(p);
            // Mobs already hunting the player give up; new ones never pick an idle wearer.
            for(Mob mob:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(48),m->m.getTarget()==p))mob.setTarget(null);
            p.displayClientMessage(Component.translatable("meta.adaptationwheel.idle_player.shield"),true);
        }
        if(menuOpen(p)&&inCombat(p))MetaServer.expose(p,MetaMechanic.INVENTORY);
    }
    private static boolean inCombat(ServerPlayer p){return p.getLastHurtByMob()!=null&&p.tickCount-p.getLastHurtByMobTimestamp()<100;}
    private static boolean menuOpen(ServerPlayer p){
        Long opened=MENU_OPENED.get(p);
        return opened!=null&&p.serverLevel().getGameTime()-opened<=MENU_SHIELD;
    }
    /** The player's own inventory exists only on the client; it reports opening and closing. */
    public static void inventory(ServerPlayer p,boolean open){
        if(open)opened(p);else if(p.containerMenu==p.inventoryMenu)MENU_OPENED.remove(p);
    }
    /** A new 20-second window at most every 30 seconds: reopening (or a client spamming "open") cannot make it permanent. */
    private static void opened(ServerPlayer p){
        long now=p.serverLevel().getGameTime();Long window=MENU_WINDOW.get(p);
        long start=window!=null&&now-window<MENU_REARM?window:now;
        MENU_WINDOW.put(p,start);MENU_OPENED.put(p,start);
    }
    @SubscribeEvent public static void containerOpen(PlayerContainerEvent.Open e){if(e.getEntity() instanceof ServerPlayer p)opened(p);}
    @SubscribeEvent public static void containerClose(PlayerContainerEvent.Close e){if(e.getEntity() instanceof ServerPlayer p)MENU_OPENED.remove(p);}
    @SubscribeEvent public static void playerAttacks(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p)MENU_OPENED.remove(p);}

    // ---------- protections ----------
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attacked(LivingAttackEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        var source=e.getSource();
        if(source.is(DamageTypes.GENERIC_KILL)&&MetaServer.full(p,MetaMechanic.COMMANDS)){
            e.setCanceled(true);p.sendSystemMessage(line("meta.adaptationwheel.console_commands.refused"));return;
        }
        if(!(source.getEntity() instanceof Mob))return;
        if(AFK_SHIELD.contains(p)&&MetaServer.full(p,MetaMechanic.AFK)){e.setCanceled(true);return;}
        if(menuOpen(p)){
            MetaServer.expose(p,MetaMechanic.INVENTORY);
            if(MetaServer.full(p,MetaMechanic.INVENTORY))e.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void hurt(LivingHurtEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getSource().getEntity() instanceof Mob&&menuOpen(p)
                &&MetaServer.active(p,MetaMechanic.INVENTORY)&&AdaptationEvents.state(p).stage(MetaMechanic.INVENTORY.key)>=2)e.setAmount(e.getAmount()*.5F);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e){
        if(e.getNewTarget() instanceof ServerPlayer p&&!(e.getEntity() instanceof Player)&&AFK_SHIELD.contains(p)&&MetaServer.full(p,MetaMechanic.AFK))e.setCanceled(true);
    }
    @SubscribeEvent public static void command(CommandEvent e){
        var parse=e.getParseResults();var source=parse.getContext().getSource();
        if(!(source.getEntity() instanceof ServerPlayer p)||!source.hasPermission(2))return;
        String input=parse.getReader().getString().trim();if(input.startsWith("/"))input=input.substring(1);
        String root=input.split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        int colon=root.indexOf(':');if(colon>=0)root=root.substring(colon+1);
        if(CHEATS.contains(root))MetaServer.expose(p,MetaMechanic.COMMANDS);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){IDLE.remove(p);AFK_SHIELD.remove(p);MENU_OPENED.remove(p);MENU_WINDOW.remove(p);}}
}
