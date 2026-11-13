package ru.pasha.adaptationwheel.client;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
public final class WheelRenderLayer extends LayerRenderer<AbstractClientPlayerEntity,PlayerModel<AbstractClientPlayerEntity>> {
 private final ModelRenderer wheel=WheelModel.create();
 private static final ResourceLocation TEXTURE=new ResourceLocation("adaptationwheel","textures/entity/wheel.png");
 public WheelRenderLayer(PlayerRenderer p){super(p);}
 @Override public void render(MatrixStack pose,IRenderTypeBuffer buffers,int light,AbstractClientPlayerEntity player,float ls,float la,float pt,float age,float yaw,float pitch){
  ClientWheelState.State state=ClientWheelState.get(player.getId());if(state==null||!state.equipped()||player.isInvisible()||player.isSpectator()||!player.isAlive())return;
  float pulse=1+Math.max(0,1-state.completionAge(pt)/18)*.08F;pose.pushPose();
  pose.translate(getParentModel().head.x/16,getParentModel().head.y/16-.92,getParentModel().head.z/16);
  pose.mulPose(Vector3f.YP.rotation(state.turn(pt)));pose.scale(pulse,pulse,pulse);
  wheel.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,OverlayTexture.NO_OVERLAY,1,1,1,1);pose.popPose();
 }
}