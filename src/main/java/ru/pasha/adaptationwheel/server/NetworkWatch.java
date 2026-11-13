package ru.pasha.adaptationwheel.server;

import java.net.*;
import java.util.*;

/** Local check of network adapters only: no packets are sent anywhere. */
public final class NetworkWatch {
    private static volatile Boolean online;
    private static volatile long changedAt=System.nanoTime();
    private static Thread thread;
    private NetworkWatch(){}
    public static synchronized void start(){
        if(thread!=null)return;
        thread=new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                boolean now=connected();
                if(online==null||online!=now){online=now;changedAt=System.nanoTime();}
                try{Thread.sleep(15_000);}catch(InterruptedException stop){return;}
            }
        },"Adaptation-network-watch");
        thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);thread.start();
    }
    /** null until the first check finished. */
    public static Boolean online(){return online;}
    public static boolean offline(){return Boolean.FALSE.equals(online);}
    public static long changedAt(){return changedAt;}
    private static boolean connected(){
        try{
            for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!n.isUp()||n.isLoopback()||n.isVirtual())continue;
                String name=(n.getName()+" "+n.getDisplayName()).toLowerCase(Locale.ROOT);
                if(name.matches(".*(virtual|vethernet|vmware|virtualbox|hyper-v|wsl|loopback|bluetooth|tap-|tunnel|teredo|isatap|pseudo).*"))continue;
                for(InetAddress a:Collections.list(n.getInetAddresses()))
                    if(!a.isLoopbackAddress()&&!a.isLinkLocalAddress()&&!a.isAnyLocalAddress())return true;
            }
        }catch(Exception unreadable){return true;}
        return false;
    }
}
