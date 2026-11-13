package ru.pasha.adaptationwheel;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import ru.pasha.adaptationwheel.core.OutsideSignals;

/**
 * Notices this mod's config files changing while the game runs and reports what changed.
 * Forge's file watcher does not always report an edit (seen with client configs on Windows),
 * so the files are also compared by modification time and re-read the way Forge would.
 */
public final class ConfigWatch {
    private static final Map<String,Map<String,String>> SNAPSHOTS=new HashMap<>();
    private static final Map<String,ModConfig> CONFIGS=new ConcurrentHashMap<>();
    private static final Map<String,ModConfig> ALL=new ConcurrentHashMap<>();
    private static final Map<String,Long> SEEN=new ConcurrentHashMap<>(),PENDING=new ConcurrentHashMap<>(),OWN_UNTIL=new ConcurrentHashMap<>();
    /** Forge saves on every ConfigValue.set (autosave); its watcher reloads the file a moment later. */
    private static final long OWN_WRITE_WINDOW=3_000_000_000L;
    private static Thread poller;
    private ConfigWatch(){}
    /** Never lets an exception escape into Forge's config loading: that would stop the world from opening. */
    public static void changed(ModConfigEvent event){
        try{onEvent(event);}catch(RuntimeException failure){LogUtils.getLogger().warn("Config change tracking skipped for {}: {}",event.getConfig().getFileName(),failure.toString());}
    }
    private static void onEvent(ModConfigEvent event){
        ModConfig config=event.getConfig();String file=config.getFileName();
        if(event instanceof ModConfigEvent.Unloading){synchronized(SNAPSHOTS){SNAPSHOTS.remove(file);}CONFIGS.remove(file);ALL.remove(file);SEEN.remove(file);PENDING.remove(file);OWN_UNTIL.remove(file);return;}
        ALL.put(file,config);
        // Only client files need the extra check: Forge's watcher reports server files itself, and a second
        // reader there would race with Forge correcting the file while a world opens.
        if(config.getType()==ModConfig.Type.CLIENT){CONFIGS.put(file,config);SEEN.put(file,modified(config));PENDING.remove(file);startPoller();}
        handle(config,event instanceof ModConfigEvent.Reloading);
    }
    /**
     * The wheel writing its own values (FPS adaptation progress) is not someone editing the file. All the
     * set() calls go inside {@code writes}: each one already saves the file, so the window covers them all.
     */
    public static void ownSave(Object spec,Runnable writes){
        ModConfig config=null;for(ModConfig c:ALL.values())if(c.getSpec()==spec)config=c;
        String file=config==null?null:config.getFileName();
        if(file!=null)OWN_UNTIL.put(file,System.nanoTime()+OWN_WRITE_WINDOW);
        writes.run();
        if(config!=null){
            Map<String,String> now=snapshot(config);
            if(now!=null)synchronized(SNAPSHOTS){SNAPSHOTS.put(file,now);}
            SEEN.put(file,modified(config));PENDING.remove(file);OWN_UNTIL.put(file,System.nanoTime()+OWN_WRITE_WINDOW);
        }
    }
    private static void handle(ModConfig config,boolean reloading){
        String file=config.getFileName();
        Map<String,String> now=snapshot(config);if(now==null)return;
        Map<String,String> before;
        synchronized(SNAPSHOTS){before=SNAPSHOTS.put(file,now);}
        if(!reloading||before==null)return;
        Long own=OWN_UNTIL.get(file);if(own!=null&&own-System.nanoTime()>0)return;
        List<String> lines=OutsideSignals.diffConfig(before,now);
        if(lines.isEmpty())return;
        String name=file.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);String shown=name;
        if(config.getType()==ModConfig.Type.CLIENT)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ru.pasha.adaptationwheel.client.OutsideClient.configChanged(shown,lines));
        else{var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)server.execute(()->ru.pasha.adaptationwheel.server.OutsideServer.configChanged(shown,lines));}
    }
    private static long modified(ModConfig config){
        try{return config.getConfigData() instanceof CommentedFileConfig?Files.getLastModifiedTime(config.getFullPath()).toMillis():-1;}
        catch(Exception unreadable){return -1;}
    }
    private static synchronized void startPoller(){
        if(poller!=null)return;
        poller=new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                try{Thread.sleep(2000);}catch(InterruptedException stop){return;}
                for(ModConfig config:CONFIGS.values())try{poll(config);}catch(Throwable failure){LogUtils.getLogger().warn("Config check failed for {}: {}",config.getFileName(),failure.toString());}
            }
        },"Adaptation-config-watch");
        poller.setDaemon(true);poller.start();
    }
    private static void poll(ModConfig config){
        String file=config.getFileName();long now=modified(config);Long seen=SEEN.get(file);
        if(now<0||seen==null||now==seen){PENDING.remove(file);return;}
        // Give Forge's own watcher one round first, then re-read the file ourselves.
        if(PENDING.putIfAbsent(file,now)==null)return;
        if(!(config.getConfigData() instanceof CommentedFileConfig data))return;
        data.load();
        var spec=config.getSpec();
        if(!spec.isCorrect(data)){spec.correct(data);data.save();}
        spec.afterReload();
        if(spec==AdaptationConfig.SPEC)AdaptationConfig.apply();
        SEEN.put(file,modified(config));PENDING.remove(file);
        handle(config,true);
    }
    /** Forge's watcher thread may be reloading the same file right now; retry a few times instead of failing. */
    private static Map<String,String> snapshot(ModConfig config){
        for(int attempt=0;attempt<5;attempt++){
            var data=config.getConfigData();if(data==null)return null;
            try{Map<String,String> out=new TreeMap<>();flatten("",data,out);return out;}
            catch(ConcurrentModificationException busy){Thread.onSpinWait();}
        }
        return null;
    }
    private static void flatten(String prefix,UnmodifiableConfig config,Map<String,String> out){
        for(var entry:config.entrySet()){
            Object value=entry.getValue();String path=prefix.isEmpty()?entry.getKey():prefix+"."+entry.getKey();
            if(value instanceof UnmodifiableConfig sub)flatten(path,sub,out);else out.put(path,String.valueOf(value));
        }
    }
}
