package com.fireworkshaders.client.render;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.entity.AbstractPyroShellEntity;
import com.fireworkshaders.pyro.RocketDesign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The shell itself: a glowing head plus a stretched tail, drawn as two camera-facing quads through
 * {@link RenderType#entityTranslucentEmissive} — vanilla's own emissive render type, which every
 * shaderpack already knows how to treat as "lit from within".
 *
 * <p>No model and no animation framework. At the speeds these travel (a Sonic Boom covers ten blocks
 * a tick) a shell <em>is</em> a streak of light, and the honest rendering of a streak of light is
 * two quads. The textures are soft radial gradients, so the head reads as a burning star and the
 * tail as a trail of sparks; the real geometry (hundreds of individual sparks) is emitted by the
 * particle engine behind it.
 */
public class PyroRocketRenderer extends EntityRenderer<AbstractPyroShellEntity> {

    private static final ResourceLocation GLOW =
            new ResourceLocation(FireworkShaders.MOD_ID, "textures/entity/pyro_shell.png");
    private static final ResourceLocation TRAIL =
            new ResourceLocation(FireworkShaders.MOD_ID, "textures/entity/pyro_trail.png");

    public PyroRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(AbstractPyroShellEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        RocketDesign design = entity.getDesign();
        if (design == null) {
            return;
        }
        float size = 0.24F + design.caliber() * 0.10F;
        int colour = design.colorAt((entity.tickCount % 40) / 40.0F);

        // The flame front is hotter than the salt that is colouring it, so the core is pushed
        // towards white before the shell's own colour is applied.
        float r = Math.min(1.0F, ((colour >> 16) & 0xFF) / 255.0F * 1.15F + 0.35F);
        float g = Math.min(1.0F, ((colour >> 8) & 0xFF) / 255.0F * 1.15F + 0.35F);
        float b = Math.min(1.0F, (colour & 0xFF) / 255.0F * 1.15F + 0.35F);

        // EntityRenderDispatcher has already translated this pose to the interpolated entity
        // position relative to the camera. Applying entity.getPosition() here would translate the
        // quad a second time (and would also fail for entities far from the origin).
        pose.pushPose();
        pose.translate(0.0D, 0.05D, 0.0D);
        pose.mulPose(this.entityRenderDispatcher.camera.rotation());
        PoseStack.Pose glowPose = pose.last();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        quad(glow, glowPose, size * 1.6F, r, g, b, 0.95F);
        pose.popPose();

        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.02D) {
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
            return;
        }

        // The quad is laid out in billboard (camera) space, so project the world-space velocity
        // into that same space before choosing the tail direction.
        Quaternionf worldToCamera = new Quaternionf(this.entityRenderDispatcher.camera.rotation()).conjugate();
        Vector3f cameraMotion = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z)
                .rotate(worldToCamera);
        float projectedSpeed = (float) Math.sqrt(cameraMotion.x * cameraMotion.x
                + cameraMotion.y * cameraMotion.y);
        float dx = projectedSpeed > 1.0E-4F ? cameraMotion.x / projectedSpeed : 0.0F;
        float dy = projectedSpeed > 1.0E-4F ? cameraMotion.y / projectedSpeed : 1.0F;
        float px = -dy;
        float py = dx;

        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.camera.rotation());
        PoseStack.Pose trailPose = pose.last();
        VertexConsumer trail = buffers.getBuffer(RenderType.entityTranslucentEmissive(TRAIL));

        float length = (float) Math.min(4.5D, 0.8D + speed * 2.2D);
        float width = size * 0.8F;
        float sideX = px * width;
        float sideY = py * width;
        float tailX = -dx * length;
        float tailY = -dy * length;

        vertex(trail, trailPose, -sideX, -sideY, 0.0F, 0.0F, 1.0F, r, g, b, 0.6F);
        vertex(trail, trailPose, sideX, sideY, 0.0F, 0.0F, 0.0F, r, g, b, 0.6F);
        vertex(trail, trailPose, tailX + sideX, tailY + sideY, 0.0F,
                1.0F, 0.0F, r, g, b, 0.0F);
        vertex(trail, trailPose, tailX - sideX, tailY - sideY, 0.0F,
                1.0F, 1.0F, r, g, b, 0.0F);
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float size, float r, float g,
                             float b, float alpha) {
        vertex(buffer, pose, -size, -size, 0.0F, 0.0F, 1.0F, r, g, b, alpha);
        vertex(buffer, pose, -size, size, 0.0F, 0.0F, 0.0F, r, g, b, alpha);
        vertex(buffer, pose, size, size, 0.0F, 1.0F, 0.0F, r, g, b, alpha);
        vertex(buffer, pose, size, -size, 0.0F, 1.0F, 1.0F, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float r, float g, float b, float alpha) {
        buffer.vertex(pose.pose(), x, y, z)
                .color(r, g, b, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    @Nullable
    @Override
    public ResourceLocation getTextureLocation(AbstractPyroShellEntity entity) {
        return GLOW;
    }
}
