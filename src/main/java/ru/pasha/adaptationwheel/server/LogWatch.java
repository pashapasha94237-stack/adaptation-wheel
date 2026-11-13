package ru.pasha.adaptationwheel.server;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import ru.pasha.adaptationwheel.core.OutsideSignals;

/** Watches the game's own log. Counts errors always; trims identical repeats only after full adaptation. */
public final class LogWatch {
    public static final int ERRORS_PER_MINUTE=30;
    private static final OutsideSignals.RepeatLimiter LIMITER=new OutsideSignals.RepeatLimiter(3,500,2048);
    private static final OutsideSignals.RateWindow ERRORS=new OutsideSignals.RateWindow(60_000_000_000L,4096);
    private static final AtomicLong TOTAL=new AtomicLong();
    private static volatile boolean serverWants,clientWants,failed;
    private static final RepeatFilter FILTER=new RepeatFilter();
    private static final Set<Object> CONFIGURED=Collections.newSetFromMap(new WeakHashMap<>());
    private LogWatch(){}
    /** Forge gives class loaders separate logger contexts; every one of them gets the filter, including later ones. */
    private static long lastInstall=Long.MIN_VALUE/2;
    /** Immediately covers logger contexts created since the last scheduled pass. */
    public static synchronized void installNow(){lastInstall=Long.MIN_VALUE/2;install();}
    public static synchronized void install(){
        // Listing every logger context costs more than the filter saves if done every second.
        long now=System.nanoTime();if(failed||now-lastInstall<30_000_000_000L)return;lastInstall=now;
        try{
            List<LoggerContext> contexts=new ArrayList<>();
            if(LogManager.getFactory() instanceof Log4jContextFactory factory)contexts.addAll(factory.getSelector().getLoggerContexts());
            contexts.add(((Logger)LogManager.getRootLogger()).getContext());
            for(LoggerContext context:contexts){
                var configuration=context.getConfiguration();
                if(!CONFIGURED.add(configuration))continue;
                // Ours goes first: an existing filter that accepts would otherwise end the chain before it.
                var existing=configuration.getFilter();
                if(existing!=null)configuration.removeFilter(existing);
                configuration.addFilter(FILTER);if(existing!=null)configuration.addFilter(existing);
                context.updateLoggers();
            }
        }catch(Throwable unavailable){failed=true;LogManager.getLogger(LogWatch.class).warn("Log adaptation unavailable: {}",unavailable.toString());}
    }
    public static void serverWants(boolean value){serverWants=value;}
    public static void clientWants(boolean value){clientWants=value;}
    public static int errorsLastMinute(){return ERRORS.count(System.nanoTime());}
    public static long totalErrors(){return TOTAL.get();}
    public static int suppressed(){return LIMITER.suppressed();}
    public static boolean trimming(){return serverWants||clientWants;}

    private static final class RepeatFilter extends AbstractFilter {
        private Result decide(org.apache.logging.log4j.Logger logger,Level level,String text,Throwable thrown){
            // isErrorEnabled()-style checks arrive without a message: they are not log lines and must
            // neither count nor be trimmed, or a guarded logger would go silent after three checks.
            if(level==null||text==null||!level.isMoreSpecificThan(Level.WARN))return Result.NEUTRAL;
            if(level.isMoreSpecificThan(Level.ERROR)){TOTAL.incrementAndGet();ERRORS.add(System.nanoTime());}
            // Fatal lines are never hidden; repeats are trimmed only while someone has the full adaptation.
            if(!trimming()||level.isMoreSpecificThan(Level.FATAL))return Result.NEUTRAL;
            String body=text==null?"":text.length()>200?text.substring(0,200):text;
            String key=(logger==null?"":logger.getName())+'|'+level+'|'+body+'|'+(thrown==null?"":thrown.getClass().getName());
            return LIMITER.allow(key)?Result.NEUTRAL:Result.DENY;
        }
        @Override public Result filter(Logger logger,Level level,Marker marker,String msg,Object... params){
            Throwable thrown=params!=null&&params.length>0&&params[params.length-1] instanceof Throwable t?t:null;
            return decide(logger,level,msg,thrown);
        }
        @Override public Result filter(Logger logger,Level level,Marker marker,Object msg,Throwable t){return decide(logger,level,msg==null?null:String.valueOf(msg),t);}
        @Override public Result filter(Logger logger,Level level,Marker marker,Message msg,Throwable t){
            String text=msg==null?null:msg.getFormat()!=null?msg.getFormat():msg.getFormattedMessage();
            return decide(logger,level,text,t!=null?t:msg==null?null:msg.getThrowable());
        }
        @Override public Result filter(LogEvent event){return Result.NEUTRAL;}
    }
}
