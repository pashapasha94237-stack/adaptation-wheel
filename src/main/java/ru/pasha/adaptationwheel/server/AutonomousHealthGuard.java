package ru.pasha.adaptationwheel.server;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Before-write protection for previously unknown mod health setters. Never restores dead entities. */
public final class AutonomousHealthGuard {
    private static final int CLASS_LIMIT=2048,IDENTITY_LIMIT=4096,STACK_LIMIT=96;
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Map<Class<?>,Optional<String>> OWNERS=lru(CLASS_LIMIT);
    private static final Map<String,Site> IDENTITIES=lru(IDENTITY_LIMIT);
    private static final Map<String,Site> BY_KEY=lru(IDENTITY_LIMIT);
    private static final ThreadLocal<Frame> WRITES=new ThreadLocal<>();
    private static List<SourceFile> files;
    private static boolean warned;
    private record SourceFile(String path,String filename,List<String> ids){}
    public record Site(String key,String mod,String owner,String method){}
    private static final class Frame {
        ServerPlayer player;final SynchedEntityData data;final Frame previous;
        Site origin;float before,raw;boolean autonomous,immune;
        Frame(ServerPlayer p,SynchedEntityData data,Frame previous){player=p;this.data=data;this.previous=previous;}
    }
    private static final class Access {
        static final Field OWNER=ObfuscationReflectionHelper.findField(SynchedEntityData.class,"f_135344_");
        static final Object HEALTH=health();
        private static Object health(){try{return ObfuscationReflectionHelper.findField(LivingEntity.class,"f_20961_").get(null);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    }
    private AutonomousHealthGuard(){}
    private static <K,V> Map<K,V> lru(int limit){return new LinkedHashMap<>(128,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<K,V> entry){return size()>limit;}};}
    /** Also suppresses First Aid's downstream generic notifications for this one direct setter. */
    public static boolean isWriting(ServerPlayer p){
        for(Frame f=WRITES.get();f!=null;f=f.previous)if(f.player==p&&f.autonomous)return true;return false;
    }
    public static float beforeSetHealth(LivingEntity entity,float requested){
        if(!(entity instanceof ServerPlayer p)||p.server==null||!p.server.isSameThread())return requested;
        Frame f=new Frame(p,null,WRITES.get());WRITES.set(f);
        try{return adjust(p,requested,f);}catch(RuntimeException|LinkageError failure){report(failure);return requested;}
    }
    public static void afterSetHealth(LivingEntity entity){finish(entity,null,true);}
    public static void failedSetHealth(LivingEntity entity){finish(entity,null,false);}
    /** Only the three-argument native set overload is instrumented; the two-argument one delegates to it. */
    public static Object beforeTrackedHealth(SynchedEntityData data,EntityDataAccessor<?> accessor,Object value){
        // A callback for the outer health write can synchronously write unrelated data
        // on the same entity. Every nested invocation needs its own balanced marker;
        // otherwise its RETURN would remove the outer health scope prematurely.
        Frame previous=WRITES.get(),entered=previous==null?null:new Frame(null,data,previous);
        if(entered!=null)WRITES.set(entered);
        if(!(value instanceof Float requested))return value;
        try{
            if(accessor!=Access.HEALTH||!(Access.OWNER.get(data) instanceof ServerPlayer p)||p.server==null||!p.server.isSameThread())return value;
            // The outer setHealth hook already owns this write, including any First Aid translation.
            for(Frame f=previous;f!=null;f=f.previous)if(f.player==p&&f.data==null)return value;
            if(entered==null){entered=new Frame(p,data,null);WRITES.set(entered);}else entered.player=p;
            return Float.valueOf(adjust(p,requested,entered));
        }catch(IllegalAccessException|RuntimeException|LinkageError failure){report(failure);return value;}
    }
    public static void afterTrackedHealth(SynchedEntityData data){finish(null,data,true);}
    public static void failedTrackedHealth(SynchedEntityData data){finish(null,data,false);}
    private static void finish(LivingEntity entity,SynchedEntityData data,boolean committed){
        Frame f=WRITES.get();if(f==null||(data==null?f.data!=null||f.player!=entity:f.data!=data))return;
        try{
            if(f.autonomous&&f.origin!=null){
                float now=f.player.getHealth();double actual=Float.isFinite(now)?Math.max(0,f.before-now):0;
                // Native tracked data commits before notifying its owner. A later
                // throwing callback cannot undo already measured HP loss.
                if(committed||actual>.001){
                    AdaptationEvents.recordExposure(f.player,f.origin.key(),false,null,f.raw,actual);
                    CombatObserver.directHealth(f.player,f.origin,actual,f.immune);
                }
            }
        }finally{if(f.previous==null)WRITES.remove();else WRITES.set(f.previous);}
    }
    private static float adjust(ServerPlayer p,float requested,Frame entered){
        if(!AdaptationEvents.active(p)||!p.isAlive()||Float.isNaN(requested)||ModAttackCompatibility.healthWriteScoped(p))return requested;
        for(Frame f=entered.previous;f!=null;f=f.previous)if(f.player==p&&f.autonomous)return requested;
        float current=p.getHealth(),maximum=p.getMaxHealth();
        if(!Float.isFinite(current)||!Float.isFinite(maximum)||current<=0||current>maximum+.001F||requested>=current)return requested;
        float target=Math.max(0,Math.min(maximum,requested)),lost=current-target;if(!(lost>0))return requested;
        Site origin=origin();if(origin==null)return requested;
        entered.origin=origin;entered.before=current;entered.raw=lost;entered.autonomous=true;
        double multiplier=AdaptationEvents.state(p).damageMultiplier(origin.key());
        entered.immune=multiplier==0;
        return current-(float)(lost*Math.max(0,Math.min(1,multiplier)));
    }
    private static Site origin(){return findSite("health_write",true);}
    /** Shared bounded provenance resolver; kind is an internal caller-supplied resource path. */
    public static Site site(String kind){
        if(kind==null||!kind.matches("[a-z0-9_/-]{1,80}"))return null;return findSite(kind,false);
    }
    /** The mod whose code called hurt() for this hit. Unlike a site, it looks through the native damage frames. */
    public static String damageMod(){
        List<StackWalker.StackFrame> frames=WALKER.walk(s->s.limit(STACK_LIMIT).toList());
        for(var f:frames)if(lifecycle(f))return null;
        for(var frame:frames){
            String name=frame.getClassName();
            if(name.startsWith("ru.pasha.adaptationwheel.")||platform(name))continue;
            String mod=owner(frame.getDeclaringClass());
            if(mod!=null&&!Set.of("minecraft","forge","adaptationwheel","pashaboost","firstaid").contains(mod))return mod;
        }
        return null;
    }
    private static Site findSite(String kind,boolean health){
        List<StackWalker.StackFrame> frames=WALKER.walk(s->s.limit(STACK_LIMIT).toList());
        // Load/respawn and operator paths remain native even when a mod observes those callbacks.
        for(var f:frames)if(lifecycle(f))return null;
        StackWalker.StackFrame primary=null,consumer=null;String primaryMod=null,consumerMod=null;
        for(var frame:frames){
            String name=frame.getClassName(),method=frame.getMethodName();
            if(name.equals(AutonomousHealthGuard.class.getName())||name.startsWith(AutonomousHealthGuard.class.getName()+"$"))continue;
            if(name.startsWith("ru.pasha.adaptationwheel.")){if(health){if(primary==null)return null;break;}continue;}
            if(nativeDamage(frame)){if(primary==null)return null;break;}
            if(platform(name))continue;
            // First Aid is a health-model adapter: its own model synchronization is
            // not a new attack, while a real content mod below the adapter retains attribution.
            String mod=owner(frame.getDeclaringClass());if(mod==null||Set.of("minecraft","forge","adaptationwheel","pashaboost","firstaid").contains(mod))continue;
            if(primary==null){primary=frame;primaryMod=mod;}
            else if(!mod.equals(primaryMod)){consumer=frame;consumerMod=mod;break;}
        }
        if(primary==null)return null;
        // A shared utility such as Collective must not give all of its consumers
        // one broad immunity. Bytecode positions also distinguish two write sites
        // inside one large handler without depending on player/entity runtime IDs.
        String identity=kind+"|"+primaryMod+"|"+callSite(primary)+(consumer==null?"":"|via:"+consumerMod+"|"+callSite(consumer));
        synchronized(IDENTITIES){
            Site found=IDENTITIES.get(identity);if(found!=null)return found;
            String simple=primary.getClassName().substring(primary.getClassName().lastIndexOf('.')+1);
            Site created=new Site("attack:"+primaryMod+":"+kind+"/"+slug(simple,40)+"/"+slug(semantic(primary.getMethodName()),32)+"/"+hash(identity),primaryMod,primary.getClassName(),semantic(primary.getMethodName()));
            IDENTITIES.put(identity,created);BY_KEY.put(created.key(),created);return created;
        }
    }
    private static String semantic(String method){return method.replaceFirst("^lambda\\$([^$]+)\\$[0-9]+$","$1");}
    private static String slug(String value,int max){String clean=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]","_");return clean.substring(0,Math.min(max,clean.length()));}
    private static String callSite(StackWalker.StackFrame frame){return frame.getClassName()+"#"+semantic(frame.getMethodName())+frame.getDescriptor()+"@"+frame.getByteCodeIndex();}
    private static boolean platform(String name){return name.startsWith("net.minecraft.")||name.startsWith("net.minecraftforge.")||name.startsWith("cpw.mods.")||name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("jdk.")||name.startsWith("sun.")||name.startsWith("org.spongepowered.")||name.startsWith("com.mojang.");}
    private static boolean nativeDamage(StackWalker.StackFrame f){
        String c=f.getClassName(),m=f.getMethodName();
        return (c.equals("net.minecraft.world.entity.LivingEntity")||c.equals("net.minecraft.world.entity.player.Player")||c.equals("net.minecraft.server.level.ServerPlayer"))
                &&Set.of("hurt","m_6469_","actuallyHurt","m_6475_").contains(m);
    }
    private static boolean lifecycle(StackWalker.StackFrame f){
        String c=f.getClassName(),m=f.getMethodName();
        if(c.startsWith("net.minecraft.server.commands."))return true;
        // Blood Magic 3.3.8-50: these exact methods exchange the user's HP for
        // altar LP (native setHealth calls verified with javap). Blocking that
        // voluntary payment would create free resources; hostile Blood Magic
        // attacks and unrelated methods must retain ordinary protection.
        if(c.equals("wayoftime.bloodmagic.common.item.ItemSacrificialDagger")&&(m.equals("m_7203_")||m.equals("use")))return true;
        if(c.equals("wayoftime.bloodmagic.util.helper.PlayerSacrificeHelper")&&m.equals("sacrificePlayerHealth"))return true;
        // Simply Swords 1.70.2: Ward halves the user's own HP as the ability's price (javap-verified).
        if(c.equals("net.sweenus.simplyswords.power.powers.WardPower")&&m.equals("use"))return true;
        if((c.equals("com.brandon3055.draconicevolution.items.tools.Dislocator")||c.equals("com.rekindled.embers.item.InflictorGemItem"))&&(m.equals("m_7203_")||m.equals("use")))return true;
        return c.startsWith("net.minecraft.")&&Set.of("load","m_20258_","readAdditionalSaveData","m_7378_","restoreFrom","m_9015_","respawn","m_11236_","checkTotemDeathProtection","m_21262_","onBelowWorld","m_142391_").contains(m);
    }
    private static String owner(Class<?> type){
        synchronized(OWNERS){
            Optional<String> cached=OWNERS.get(type);if(cached!=null)return cached.orElse(null);
            String selected=null;
            try{
                String module=type.getModule().getName();
                if(module!=null&&ModList.get().getModContainerById(module).isPresent())selected=module;
                if(selected==null&&type.getProtectionDomain()!=null&&type.getProtectionDomain().getCodeSource()!=null){
                    String source=normalize(type.getProtectionDomain().getCodeSource().getLocation().toString());
                    for(SourceFile file:files())if(source.contains(file.path())||source.contains("/"+file.filename()+"#")||source.endsWith("/"+file.filename())){
                        if(file.ids().size()==1)selected=file.ids().get(0);
                        else for(String id:file.ids())if(type.getName().toLowerCase(Locale.ROOT).contains(id.replace('_','.'))){selected=id;break;}
                        if(selected!=null)break;
                    }
                }
            }catch(RuntimeException|LinkageError ignored){}
            OWNERS.put(type,Optional.ofNullable(selected));return selected;
        }
    }
    private static List<SourceFile> files(){
        if(files==null){var rows=new ArrayList<SourceFile>();for(var info:ModList.get().getModFiles())if(info instanceof ModFileInfo concrete){
            var path=concrete.getFile().getFilePath();rows.add(new SourceFile(normalize(path.toAbsolutePath().normalize().toString()),normalize(path.getFileName().toString()),info.getMods().stream().map(m->m.getModId()).sorted().toList()));
        }files=List.copyOf(rows);}return files;
    }
    private static String normalize(String value){try{value=URLDecoder.decode(value,StandardCharsets.UTF_8);}catch(IllegalArgumentException ignored){}return value.replace('\\','/').toLowerCase(Locale.ROOT);}
    private static String hash(String identity){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)),0,12);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    public static Component name(String key){
        if(key==null||!key.startsWith("attack:")||!key.contains(":health_write/"))return null;
        synchronized(IDENTITIES){Site origin=BY_KEY.get(key);if(origin!=null)return label(origin.mod(),origin.owner().substring(origin.owner().lastIndexOf('.')+1),origin.method());}
        // The readable identity survives saves and reaches clients without a server-only cache.
        String[] parts=key.split("/",-1);if(parts.length!=4)return null;
        String mod=parts[0].substring(7,parts[0].lastIndexOf(':'));
        return label(mod,parts[1],parts[2]);
    }
    private static Component label(String mod,String owner,String method){return Component.translatableWithFallback("attack.adaptationwheel.native_health","Direct health change: %s.%s [%s]",owner,method,mod);}
    private static void report(Throwable failure){if(!warned){warned=true;LogUtils.getLogger().warn("Autonomous direct-health hook unavailable; preserving native write",failure);}}
}

