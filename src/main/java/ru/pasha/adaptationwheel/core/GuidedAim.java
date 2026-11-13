package ru.pasha.adaptationwheel.core;

/** Server-independent intercept geometry; never changes projectile damage or speed. */
public final class GuidedAim {
    public record Vector(double x,double y,double z){
        public double squared(){return x*x+y*y+z*z;}
        public Vector add(Vector b){return new Vector(x+b.x,y+b.y,z+b.z);}
        public Vector scale(double s){return new Vector(x*s,y*s,z*s);}
    }
    public static Vector velocity(Vector delta,Vector targetVelocity,double speed){
        if(!Double.isFinite(speed)||speed<=0||!Double.isFinite(delta.squared())||!Double.isFinite(targetVelocity.squared()))return new Vector(0,0,0);
        double a=targetVelocity.squared()-speed*speed,b=2*(delta.x*targetVelocity.x+delta.y*targetVelocity.y+delta.z*targetVelocity.z),c=delta.squared(),time=0;
        if(Math.abs(a)<1e-8){if(Math.abs(b)>1e-8)time=-c/b;}
        else {double disc=b*b-4*a*c;if(disc>=0){double root=Math.sqrt(disc),t1=(-b-root)/(2*a),t2=(-b+root)/(2*a);time=t1>0&&t2>0?Math.min(t1,t2):Math.max(t1,t2);}}
        if(!Double.isFinite(time)||time<0)time=0;time=Math.min(10,time);
        Vector aim=delta.add(targetVelocity.scale(time));double length=Math.sqrt(aim.squared());
        return length<1e-8?new Vector(0,0,0):aim.scale(speed/length);
    }
    /** Exact minimum segment/AABB distance via its at-most-eight quadratic intervals. */
    public static double distanceSquared(Vector from,Vector to,Vector min,Vector max){
        double[] breaks=new double[8];int n=2;breaks[0]=0;breaks[1]=1;
        double[] p={from.x,from.y,from.z},d={to.x-from.x,to.y-from.y,to.z-from.z},lo={min.x,min.y,min.z},hi={max.x,max.y,max.z};
        for(int axis=0;axis<3;axis++)if(Math.abs(d[axis])>1e-12)for(double edge:new double[]{lo[axis],hi[axis]}){double t=(edge-p[axis])/d[axis];if(t>0&&t<1)breaks[n++]=t;}
        ArraysSort.sort(breaks,n);double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<n-1;i++){
            double left=breaks[i],right=breaks[i+1],mid=(left+right)*.5,num=0,den=0;
            for(int axis=0;axis<3;axis++){double v=p[axis]+d[axis]*mid,edge=v<lo[axis]?lo[axis]:v>hi[axis]?hi[axis]:Double.NaN;
                if(!Double.isNaN(edge)){num+=d[axis]*(p[axis]-edge);den+=d[axis]*d[axis];}}
            double t=den>0?Math.max(left,Math.min(right,-num/den)):left,sum=0;
            for(int axis=0;axis<3;axis++){double v=p[axis]+d[axis]*t,offset=v<lo[axis]?lo[axis]-v:v>hi[axis]?v-hi[axis]:0;sum+=offset*offset;}
            best=Math.min(best,sum);
        }return best;
    }
    private static final class ArraysSort{static void sort(double[] values,int n){java.util.Arrays.sort(values,0,n);}}
    private GuidedAim(){}
}
