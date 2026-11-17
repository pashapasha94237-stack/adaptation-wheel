package ru.pasha.adaptationwheel.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The worn blade's third-person transform, shared by the guard pose and swept collision. */
public final class BladeGuard {
    public static final float ARM_PITCH = -1.7F;
    private BladeGuard() {}

    public static Vec3[] edge(LivingEntity player) {
        boolean right = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getMainArm() == HumanoidArm.RIGHT : player.getMainArm() != HumanoidArm.RIGHT;
        float side = right ? 1 : -1;
        float pitch = (float)Math.toRadians(player.getXRot()) * .5F + ARM_PITCH;
        Matrix4f m = new Matrix4f().rotateY((float)Math.PI-(float)Math.toRadians(player.getYRot()))
                .scale(-.9375F,-.9375F,.9375F).translate(0,-1.501F,0)
                .translate(-side*5/16F,2/16F+(player.isCrouching()?3.2F/16F:0),0)
                .rotateY(-side*.7F).rotateX(pitch)
                .rotateX(-(float)Math.PI/2).rotateY((float)Math.PI)
                .translate(side/16F,.125F,-.625F)
                .translate(side*3/16F,-2/16F,2/16F)
                .rotateX(-(float)Math.PI/2).rotateY(side*(float)Math.PI/2)
                .scale(.55F).translate(-.5F,-.5F,-.5F);
        return new Vec3[]{point(m,player,8/16F),point(m,player,32/16F)};
    }
    private static Vec3 point(Matrix4f m,LivingEntity player,float y) {
        Vector3f v=m.transformPosition(new Vector3f(.5F,y,.5F));
        return player.position().add(v.x,v.y,v.z);
    }
    private static double distanceSquared(Vec3 p,Vec3 a,Vec3 b) {
        Vec3 edge=b.subtract(a);
        double t=Math.max(0,Math.min(1,p.subtract(a).dot(edge)/edge.lengthSqr()));
        return p.distanceToSqr(a.add(edge.scale(t)));
    }
    /** First intersection of the moving projectile with the narrow blade capsule, not the player's box. */
    public static Vec3 contact(LivingEntity player,Vec3 from,Vec3 to) {
        Vec3[] edge=edge(player); Vec3 motion=to.subtract(from);
        double lo=0,hi=1,radius=.18;
        for(int i=0;i<28;i++) {
            double a=(2*lo+hi)/3,b=(lo+2*hi)/3;
            if(distanceSquared(from.add(motion.scale(a)),edge[0],edge[1])<distanceSquared(from.add(motion.scale(b)),edge[0],edge[1])) hi=b; else lo=a;
        }
        double closest=(lo+hi)/2;
        if(distanceSquared(from.add(motion.scale(closest)),edge[0],edge[1])>radius*radius)return null;
        lo=0;hi=closest;
        for(int i=0;i<24;i++) {
            double mid=(lo+hi)/2;
            if(distanceSquared(from.add(motion.scale(mid)),edge[0],edge[1])<=radius*radius)hi=mid;else lo=mid;
        }
        return from.add(motion.scale(hi));
    }
}
