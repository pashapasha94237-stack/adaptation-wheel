package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class WheelRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public WheelRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        WheelSkinClient.Preview preview = WheelSkinClient.preview;
        if (preview != null && preview.player() == player) {
            // the look menu's preview: the chosen look, its own turn and clock, worn or not
            draw(pose, buffers, packedLight, preview.skin(), preview.turn(), preview.pulse(), preview.seconds());
            return;
        }
        ClientWheelState.State state = ClientWheelState.get(player.getId());
        if (state == null || !state.equipped() || player.isInvisible() || player.isSpectator() || !player.isAlive()) {
            return;
        }

        float clock = ClientWheelState.time(partialTick);
        float turn = state.turn(partialTick);
        boolean local=player==net.minecraft.client.Minecraft.getInstance().player;
        if(local) turn+=FpsAdaptation.angle(partialTick);
        float age=local?Math.min(state.completionAge(partialTick),FpsAdaptation.completionAge(partialTick)):state.completionAge(partialTick);
        float pulse = 1.0F + Math.max(0, 1.0F - age / 18.0F) * 0.08F;
        draw(pose, buffers, packedLight, WheelSkinClient.of(player), turn, pulse, clock / 20.0F);
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int packedLight, ru.pasha.adaptationwheel.core.WheelSkin skin,
                      float turn, float pulse, float seconds) {
        pose.pushPose();
        // Player model coordinates point downwards; head.y follows crouching and swimming poses.
        pose.translate(getParentModel().head.x / 16.0F, getParentModel().head.y / 16.0F, getParentModel().head.z / 16.0F);
        SkinRenderer.render(skin, pose, buffers, packedLight, turn, pulse, seconds);
        pose.popPose();
    }
}
