package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.pasha.adaptationwheel.item.ExterminationSwordItem;

/**
 * The Sword of Extermination is strapped along the outside of the forearm, as Mahoraga wears it, not gripped.
 * The hand models (extermination_sword_worn.json) place it on the arm; this class keeps the arm visible in
 * first person, where vanilla hides it whenever the hand holds an item, and gives the parry a guard pose.
 */
public final class ForearmBlade {
    /** Forearm raised across the chest. Not UseAnim.BLOCK: vanilla would then treat the blade as a shield. */
    private static final HumanoidModel.ArmPose GUARD = HumanoidModel.ArmPose.create("ADAPTATIONWHEEL_FOREARM_GUARD", false, (model, entity, arm) -> {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        part.xRot = model.head.xRot * 0.5F + ru.pasha.adaptationwheel.item.BladeGuard.ARM_PITCH;
        part.yRot = side * -0.7F;
        part.zRot = 0;
    });

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return entity.isUsingItem() && entity.getUsedItemHand() == hand ? GUARD : null;
        }
    };

    private ForearmBlade() {
    }

    static void renderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!(stack.getItem() instanceof ExterminationSwordItem) || player == null) return;
        event.setCanceled(true);
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1 : -1;
        float swing = event.getSwingProgress(), root = Mth.sqrt(swing);
        boolean guard = player.isUsingItem() && player.getUsedItemHand() == event.getHand();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        pose.pushPose();
        // The empty-hand transform of ItemInHandRenderer.renderPlayerArm, so punches and equipping look the same.
        pose.translate(side * (-0.3F * Mth.sin(root * Mth.PI) + 0.64F),
                0.4F * Mth.sin(root * Mth.TWO_PI) - 0.6F - event.getEquipProgress() * 0.6F,
                -0.4F * Mth.sin(swing * Mth.PI) - 0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * Mth.sin(root * Mth.PI) * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(swing * swing * Mth.PI) * -20.0F));
        // Parry: lift the arm in view space, then swing the forearm across so the blade stands before the crosshair.
        if (guard) pose.translate(side * -0.1F, 0.25F, 0.0F);
        pose.translate(side * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        pose.translate(side * 5.6F, 0.0F, 0.0F);
        if (guard) {
            pose.mulPose(Axis.XP.rotationDegrees(-30.0F));
            pose.mulPose(Axis.YP.rotationDegrees(side * -40.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -30.0F));
        }
        PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!player.isInvisible()) {
            if (right) renderer.renderRightHand(pose, buffers, light, player);
            else renderer.renderLeftHand(pose, buffers, light, player);
        }
        // From here on, the same path ItemInHandLayer takes for a third-person hand, so both views share one model.
        (right ? renderer.getModel().rightArm : renderer.getModel().leftArm).translateAndRotate(pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(side / 16.0F, 0.125F, -0.625F);
        minecraft.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, !right, pose, buffers, light);
        pose.popPose();
    }
}
