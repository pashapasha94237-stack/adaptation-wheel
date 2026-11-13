package ru.pasha.adaptationwheel.core;

/**
 * The World Slash as a thin diagonal plane: it runs {@code length} blocks ahead of the swing,
 * spans {@code span} blocks to each side along a blade tilted like a diagonal cut, and is
 * {@code thickness} blocks thick. Pure geometry, so it is unit-tested without Minecraft.
 */
public final class SlashPlane {
    public final double ox,oy,oz,length,span,thickness;
    /** forward, blade and normal unit vectors. */
    public final double[] f,b,n;
    private SlashPlane(double ox,double oy,double oz,double[] f,double[] b,double[] n,double length,double span,double thickness){
        this.ox=ox;this.oy=oy;this.oz=oz;this.f=f;this.b=b;this.n=n;this.length=length;this.span=span;this.thickness=thickness;
    }
    /** @param tiltDegrees angle of the blade from the horizontal, as the camera sees it. */
    public static SlashPlane of(double ox,double oy,double oz,double lx,double ly,double lz,double tiltDegrees,double length,double span,double thickness){
        double[] f=normalize(new double[]{lx,ly,lz});
        double[] right=cross(f,new double[]{0,1,0});
        if(dot(right,right)<1.0E-6)right=new double[]{1,0,0};
        right=normalize(right);
        double[] up=normalize(cross(right,f));
        double t=Math.toRadians(tiltDegrees),c=Math.cos(t),s=Math.sin(t);
        double[] blade=normalize(new double[]{right[0]*c+up[0]*s,right[1]*c+up[1]*s,right[2]*c+up[2]*s});
        double[] normal=normalize(cross(f,blade));
        return new SlashPlane(ox,oy,oz,f,blade,normal,length,span,thickness);
    }
    /** Separating-axis test of an axis-aligned box (centre, half sizes) against the slab. */
    public boolean hits(double cx,double cy,double cz,double hx,double hy,double hz){
        double dx=cx-ox,dy=cy-oy,dz=cz-oz;
        double alongN=dx*n[0]+dy*n[1]+dz*n[2],rn=hx*Math.abs(n[0])+hy*Math.abs(n[1])+hz*Math.abs(n[2]);
        if(Math.abs(alongN)>thickness/2+rn)return false;
        double alongF=dx*f[0]+dy*f[1]+dz*f[2],rf=hx*Math.abs(f[0])+hy*Math.abs(f[1])+hz*Math.abs(f[2]);
        if(alongF+rf<0||alongF-rf>length)return false;
        double alongB=dx*b[0]+dy*b[1]+dz*b[2],rb=hx*Math.abs(b[0])+hy*Math.abs(b[1])+hz*Math.abs(b[2]);
        return Math.abs(alongB)<=span+rb;
    }
    /** A point on the plane: {@code forward} blocks ahead, {@code side} blocks along the blade. */
    public double[] point(double forward,double side){
        return new double[]{ox+f[0]*forward+b[0]*side,oy+f[1]*forward+b[1]*side,oz+f[2]*forward+b[2]*side};
    }
    /** Axis-aligned bounds of the whole plane: minX,minY,minZ,maxX,maxY,maxZ. */
    public double[] bounds(){
        double[] lo={Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE},hi={-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        for(double d:new double[]{0,length})for(double s:new double[]{-span,span}){
            double[] p=point(d,s);for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],p[i]);hi[i]=Math.max(hi[i],p[i]);}
        }
        double pad=thickness/2+1;
        return new double[]{lo[0]-pad,lo[1]-pad,lo[2]-pad,hi[0]+pad,hi[1]+pad,hi[2]+pad};
    }
    private static double dot(double[] a,double[] c){return a[0]*c[0]+a[1]*c[1]+a[2]*c[2];}
    private static double[] cross(double[] a,double[] c){return new double[]{a[1]*c[2]-a[2]*c[1],a[2]*c[0]-a[0]*c[2],a[0]*c[1]-a[1]*c[0]};}
    private static double[] normalize(double[] a){double l=Math.sqrt(dot(a,a));return l<1.0E-9?new double[]{0,0,1}:new double[]{a[0]/l,a[1]/l,a[2]/l};}
}
