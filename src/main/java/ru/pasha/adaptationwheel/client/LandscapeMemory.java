package ru.pasha.adaptationwheel.client;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/** Unmodified LGPL engine through its public API. Archive history is never evicted by this bridge. */
public final class LandscapeMemory {
    private static final String API="com.seibel.distanthorizons.api.";
    private static Object graphics,threads,worldGen;
    private static boolean initialized,failed,rendering;
    private static Boolean optifine;
    private static int calmWindows;
    private static int qualityTier,lastDistance;
    private static long lastApply,lastFrame;
    private static double baselineMs,windowMs;private static int frames;
    private static String archiveRoot="";
    private static Object saveOverride;
    private LandscapeMemory(){}
    private static Object call(Object object,String name)throws ReflectiveOperationException{return object.getClass().getMethod(name).invoke(object);}
    private static void set(Object group,String name,Object value)throws ReflectiveOperationException{
        Object config=call(group,name);Object old=call(config,"getValue");if(Objects.equals(old,value))return;
        Object result=config.getClass().getMethod("setValue",Object.class,String.class).invoke(config,value,"Adaptation Wheel");
        if(Boolean.FALSE.equals(result))throw new IllegalStateException("Landscape API refused "+name);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static Object option(String type,String value)throws ClassNotFoundException{return Enum.valueOf((Class)Class.forName(API+"enums.config."+type),value);}
    public static void initialize(){
        if(initialized||failed)return;
        if(!MetaConfig.CLIENT.isLoaded())return;
        try{
            Object configs=Class.forName(API+"DhApi$Delayed").getField("configs").get(null);if(configs==null)return;
            graphics=call(configs,"graphics");threads=call(configs,"multiThreading");worldGen=call(configs,"worldGenerator");
            set(graphics,"renderingEnabled",false);set(worldGen,"enableDistantWorldGeneration",false);
            // Keep background recording modest even before the visual benefit is unlocked.
            // The engine shares its task budget between I/O and render loaders. A single
            // heavily throttled loader can starve disk reads after a slow first GPU upload.
            set(threads,"threadCount",2);set(threads,"threadRuntimeRatio",.5);
            installSaveRoot();initialized=true;
        }catch(ClassNotFoundException absent){failed=true;LogUtils.getLogger().warn("Landscape engine is unavailable; archive is left untouched.");}
        catch(Throwable e){failed=true;LogUtils.getLogger().error("Could not initialize landscape memory; archive is left untouched.",e);}
    }
    /** Reproducible bundled engine: no silent self-replacement or competing options button. */
    public static void setup(){
        try{
            runtimeFlag("Config$Client$Advanced$AutoUpdater","enableAutoUpdater",false);
            runtimeFlag("Config$Client$Advanced$AutoUpdater","enableSilentUpdates",false);
            runtimeFlag("Config$Client","showDhOptionsButtonInMinecraftUi",false);
        }catch(Throwable e){LogUtils.getLogger().warn("Could not apply bundled landscape UI/update policy",e);}
        initialize();
    }
    private static void runtimeFlag(String owner,String field,boolean value)throws ReflectiveOperationException{
        Object entry=Class.forName("com.seibel.distanthorizons.core.config."+owner).getField(field).get(null);
        entry.getClass().getMethod("setWithoutSaving",Object.class).invoke(entry,value);
    }
    private static void installSaveRoot()throws Exception{
        archiveRoot=MetaConfig.CLIENT.isLoaded()?MetaConfig.ARCHIVE_DIRECTORY.get().strip():"";
        boolean custom=!archiveRoot.isBlank();
        Path root=custom?Path.of(archiveRoot):Minecraft.getInstance().gameDirectory.toPath().resolve("adaptationwheel-terrain").toAbsolutePath();
        if(!root.isAbsolute())throw new IllegalArgumentException("Landscape archive must be an absolute directory");
        if(custom)Files.createDirectories(root);
        Class<?> type=Class.forName(API+"interfaces.override.levelHandling.IDhApiSaveStructure");
        saveOverride=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,m,args)->switch(m.getName()){
            case "getPriority"->1000;case "getDelayedSetupComplete"->true;case "finishDelayedSetup"->null;
            case "toString"->"AdaptationWheelDiskArchive";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];
            case "overrideFilePath"->{
                File original=(File)args[0];if(original==null)yield null;
                String canonical=original.getCanonicalPath();Path database=original.toPath().resolve("DistantHorizons.sqlite");
                // Windows native SQLite can reject long dimension paths even when Java
                // created the directory. Keep ordinary/existing archives exactly where they are.
                if(!custom&&(canonical.length()<=220||Files.isRegularFile(database)&&Files.size(database)>0))yield original;
                String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0,24);
                Path dir=root.resolve(id);Files.createDirectories(dir);yield dir.toFile();
            }
            default->null;
        });
        Object injector=Class.forName(API+"DhApi").getField("overrides").get(null);
        Class<?> impl=Class.forName(API+"interfaces.override.IDhApiOverrideable");injector.getClass().getMethod("bind",Class.class,impl).invoke(injector,type,saveOverride);
    }
    public static void tick(){
        initialize();if(!initialized||failed)return;
        long now=System.nanoTime();if(now-lastApply<1_000_000_000L)return;lastApply=now;
        // OptiFine replaces the chunk renderer the engine draws beside; enabling distant
        // terrain there hides the normal world. Keep recording, but never draw under it.
        boolean active=MetaClient.full(MetaMechanic.LANDSCAPE)&&!optifine();
        try{
            int configured=MetaConfig.CLIENT.isLoaded()?MetaConfig.LANDSCAPE_DISTANCE.get():128;
            // The far view must reach beyond the normal view, otherwise a large vanilla
            // render distance leaves the engine nothing to draw and only overlaps it.
            int vanilla=Minecraft.getInstance().options.getEffectiveRenderDistance();
            int distance=active?Math.min(1024,Math.max(configured,vanilla+32)):32;
            if(distance!=lastDistance){set(graphics,"chunkRenderDistance",distance);lastDistance=distance;}
            set(worldGen,"enableDistantWorldGeneration",false);
            set(graphics,"useCameraPositionForQualityDropOff",true);
            set(graphics,"disableFrustumCulling",false);set(graphics,"lodOnlyMode",false);
            boolean pressure=MetaClient.full(MetaMechanic.MEMORY)&&MetaClient.heapRatio()>.75;
            int effectiveTier=pressure?Math.max(1,qualityTier):qualityTier;
            set(graphics,"horizontalQuality",option("EDhApiHorizontalQuality",effectiveTier==0?"LOW":"LOWEST"));
            set(graphics,"maxHorizontalResolution",option("EDhApiMaxHorizontalResolution",effectiveTier>=2?"FOUR_BLOCKS":"TWO_BLOCKS"));
            set(graphics,"verticalQuality",option("EDhApiVerticalQuality",effectiveTier>=2?"LOW":"MEDIUM"));
            set(threads,"threadCount",2);set(threads,"threadRuntimeRatio",pressure?.25:.5);
            set(graphics,"renderingEnabled",active);rendering=active;
        }catch(Throwable e){failed=true;try{set(graphics,"renderingEnabled",false);}catch(Throwable ignored){}rendering=false;LogUtils.getLogger().error("Landscape bridge stopped after an API error; saved terrain is retained.",e);}
    }
    /** Frame-time feedback changes detail and background CPU share, never deletes stored areas. */
    public static void frame(){
        long now=System.nanoTime();if(lastFrame==0){lastFrame=now;return;}double ms=(now-lastFrame)/1_000_000.0;lastFrame=now;
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.isPaused()||ms>250||ms<1)return;
        windowMs+=ms;frames++;if(frames<120)return;double average=windowMs/frames;windowMs=0;frames=0;
        if(!rendering){baselineMs=baselineMs==0?average:baselineMs*.75+average*.25;return;}
        if(baselineMs>0&&average>Math.max(baselineMs*1.15,baselineMs+2)&&qualityTier<2){qualityTier++;calmWindows=0;lastApply=0;}
        // Detail lowered during a heavy moment (a boss fight, a storm) returns once frames recover.
        else if(baselineMs>0&&average<=baselineMs*1.05&&qualityTier>0){if(++calmWindows>=10){qualityTier--;calmWindows=0;lastApply=0;}}
        else calmWindows=0;
    }
    public static boolean ready(){return initialized&&!failed;}
    /** OptiFine is detected by its own classes; loading them is not required. */
    public static boolean optifine(){
        if(optifine==null){
            boolean found=false;
            for(String name:new String[]{"net.optifine.Config","optifine.OptiFineTransformationService"}){
                try{Class.forName(name,false,LandscapeMemory.class.getClassLoader());found=true;break;}catch(ClassNotFoundException|LinkageError ignored){}
            }
            optifine=found;
            if(found)LogUtils.getLogger().info("OptiFine detected: landscape memory keeps recording, distant terrain drawing stays off.");
        }
        return optifine;
    }
    public static boolean rendering(){return rendering;}
    public static int qualityTier(){return qualityTier;}
    public static String archiveDirectory(){return archiveRoot.isBlank()?"world data / adaptationwheel-terrain (long paths)":archiveRoot;}
    public static void logout(){rendering=false;lastFrame=0;frames=0;windowMs=baselineMs=0;qualityTier=0;calmWindows=0;lastApply=0;if(initialized)try{set(graphics,"renderingEnabled",false);}catch(Throwable ignored){}}
}
