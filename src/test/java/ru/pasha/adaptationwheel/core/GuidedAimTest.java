package ru.pasha.adaptationwheel.core;

public final class GuidedAimTest {
    private static GuidedAim.Vector v(double x,double y,double z){return new GuidedAim.Vector(x,y,z);}
    public static void run(){
        var guided=GuidedAim.velocity(v(10,0,0),v(0,0,.2),2);
        check(Math.abs(guided.squared()-4)<1e-8&&guided.z()>0,"Guidance must lead a moving target and preserve projectile speed");
        check(GuidedAim.velocity(v(0,0,0),v(0,0,0),2).squared()==0,"Coincident positions must not create NaN");
        check(GuidedAim.velocity(v(1,1,1),v(0,0,0),Double.NaN).squared()==0,"Invalid mod velocity must not poison entity position");
        var min=v(-.3,0,5);var max=v(.3,2,6);
        check(GuidedAim.distanceSquared(v(1.8,1,0),v(1.8,1,10),min,max)>2.24&&GuidedAim.distanceSquared(v(1.8,1,0),v(1.8,1,10),min,max)<2.26,"Fast shots must count their swept 1.5-block miss rather than only endpoints");
        check(GuidedAim.distanceSquared(v(0,1,0),v(0,1,10),min,max)==0,"A real intersecting hit must not count as a miss");
        check(GuidedAim.distanceSquared(v(3,1,0),v(3,1,10),min,max)>4,"A distant shot must not trigger close-miss learning");
        check(GuidedAim.distanceSquared(v(1,3,5),v(1,3,5),min,max)>0,"Zero-length segments remain finite");
        System.out.println("GuidedAimTest: moving interception, unchanged speed, exact swept near-miss geometry and finite safeguards passed.");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
