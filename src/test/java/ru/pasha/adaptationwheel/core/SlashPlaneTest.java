package ru.pasha.adaptationwheel.core;

public final class SlashPlaneTest {
    public static void run(){
        // Looking along +Z, blade tilted 35 degrees, 64 long, 10 to each side, 1 thick.
        var plane=SlashPlane.of(0,0,0,0,0,1,35,64,10,1);
        double hx=.3,hy=.9,hz=.3;
        check(plane.hits(0,0,30,hx,hy,hz),"A mob straight ahead was not cut");
        check(plane.hits(0,0,63.5,hx,hy,hz),"The far end of the slash missed");
        check(!plane.hits(0,0,70,hx,hy,hz),"The slash reached beyond its length");
        check(!plane.hits(0,0,-3,hx,hy,hz),"The slash cut behind the swing");
        double[] edge=plane.point(20,9.5);
        check(plane.hits(edge[0],edge[1],edge[2],hx,hy,hz),"The blade edge did not cut");
        double[] outside=plane.point(20,13);
        check(!plane.hits(outside[0],outside[1],outside[2],hx,hy,hz),"The slash reached beyond its span");
        double[] off=plane.point(20,0);double[] n=plane.n;
        check(!plane.hits(off[0]+n[0]*3,off[1]+n[1]*3,off[2]+n[2]*3,hx,hy,hz),"A mob beside the thin plane was cut");
        check(plane.hits(off[0]+n[0]*1,off[1]+n[1]*1,off[2]+n[2]*1,hx,hy,hz),"A mob grazed by the plane was missed");
        check(Math.abs(plane.b[1]-Math.sin(Math.toRadians(35)))<1e-9,"The blade is not diagonal");
        var straightDown=SlashPlane.of(0,0,0,0,-1,0,35,64,10,1);
        check(straightDown.hits(0,-20,0,hx,hy,hz),"Looking straight down broke the slash");
        var b=plane.bounds();
        check(b[2]<=0&&b[5]>=64&&b[0]<=-8&&b[3]>=8,"Bounds do not contain the slash");
        System.out.println("SlashPlaneTest: length, span, thickness, diagonal blade, vertical look and bounds passed.");
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
