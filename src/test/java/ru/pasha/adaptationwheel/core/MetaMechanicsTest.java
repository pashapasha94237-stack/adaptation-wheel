package ru.pasha.adaptationwheel.core;

public final class MetaMechanicsTest {
    public static void run(){
        var window=new SustainedPressure();
        for(int i=0;i<10;i++)check(!window.sample(i*1_000_000_000L,true,10_000_000_000L,3_000_000_000L),"Pressure awarded early");
        check(window.sample(10_000_000_000L,true,10_000_000_000L,3_000_000_000L),"Real pressure window failed");
        check(!window.sample(20_000_000_000L,true,10_000_000_000L,3_000_000_000L),"Loading gap awarded pressure");
        window.sample(21_000_000_000L,false,10_000_000_000L,3_000_000_000L);
        check(!window.sample(22_000_000_000L,true,10_000_000_000L,3_000_000_000L),"Recovered heap retained qualification");
        window.clear();check(!window.sample(0,true,10,3),"Reset failed");check(!window.sample(-1,true,10,3),"Backward clock awarded pressure");
        var n=new NotificationMemory();check(!n.receive("Подобрано: камень",0,true).group(),"First message hidden");
        check(n.receive("Подобрано: камень",1,true).group(),"Safe exact repeat not grouped");
        check(!n.receive("Подобрано: алмаз",2,true).group(),"Distinct message grouped");
        check(n.receive("Подобрано: камень",3,false).count()==3&&!n.receive("Подобрано: камень",4,false).group(),"Locked grouping active");
        check(n.receive("Подобрано: камень",6000,true).count()==1,"Expiry failed");
        for(String s:new String[]{"Получено: опасность", "Received attack warning", "hello", "Умер игрок", "Получено: взрыв рядом"})for(int i=0;i<3;i++)check(!n.receive(s,7000+i,true).group(),"Important/non-system text grouped: "+s);
        for(int i=0;i<65;i++)n.receive("Подобрано: "+i,8000+i,true);
        check(n.receive("Подобрано: 0",8100,true).count()==1,"Bounded eviction failed");
        for(int i=0;i<11000;i++)n.receive("Подобрано: стопка",8200,true);
        check(n.receive("Подобрано: стопка",8201,true).count()==9999,"Repeat counter unbounded");
        check(!n.receive("Подобрано: "+"я".repeat(600),8201,true).group(),"Oversized message accepted");
        n.clear();check(n.receive("Подобрано: стопка",8202,true).count()==1,"Logout retains repeats");
        System.out.println("MetaMechanicsTest: real-time pressure, discontinuities, notification safety/expiry/bounds passed.");
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
