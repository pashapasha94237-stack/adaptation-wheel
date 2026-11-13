package ru.pasha.adaptationwheel.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.pasha.adaptationwheel.AdaptationWheel;

/** Cuboid geometry: a faceted gold ring, eight spokes and nine stepped ornaments. */
public final class WheelModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(AdaptationWheel.MOD_ID, "adaptation_wheel"), "main");

    private WheelModel() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition wheel = mesh.getRoot().addOrReplaceChild("wheel", CubeListBuilder.create(), PartPose.ZERO);

        // Short tangential cuboids make a distinctly Minecraft-style circular rim.
        for (int index = 0; index < 24; index++) {
            float angle = index * Mth.TWO_PI / 24.0F;
            wheel.addOrReplaceChild("rim_" + index,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-1.39F, -0.7F, -0.7F, 2.78F, 1.4F, 1.4F),
                    PartPose.offsetAndRotation(Mth.sin(angle) * 10.0F, 0,
                            Mth.cos(angle) * 10.0F, 0, angle, 0));
        }

        for (int index = 0; index < 8; index++) {
            float angle = index * Mth.PI / 4.0F;
            wheel.addOrReplaceChild("spoke_" + index,
                    CubeListBuilder.create().texOffs(0, 8)
                            .addBox(-0.5F, -0.55F, 0.0F, 1.0F, 1.1F, 14.5F),
                    PartPose.rotation(0, angle, 0));
            addOrnament(wheel, "ornament_" + index,
                    Mth.sin(angle) * 14.5F, Mth.cos(angle) * 14.5F, angle);
        }
        addOrnament(wheel, "hub", 0, 0, 0);
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static void addOrnament(PartDefinition wheel, String name, float x, float z, float angle) {
        // Intersecting boxes create stepped edges without smooth spheres or external models.
        wheel.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.4F, -1.8F, -2.4F, 4.8F, 3.6F, 4.8F)
                        .texOffs(0, 0).addBox(-1.8F, -2.4F, -1.8F, 3.6F, 4.8F, 3.6F)
                        .texOffs(0, 0).addBox(-2.6F, -1.3F, -1.3F, 5.2F, 2.6F, 2.6F)
                        .texOffs(0, 0).addBox(-1.3F, -1.3F, -2.6F, 2.6F, 2.6F, 5.2F),
                PartPose.offsetAndRotation(x, 0, z, 0, angle, 0));
    }
}
