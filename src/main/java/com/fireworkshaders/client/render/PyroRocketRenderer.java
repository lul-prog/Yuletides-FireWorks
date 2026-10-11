package com.fireworkshaders.client.render;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.entity.AbstractPyroShellEntity;
import com.fireworkshaders.pyro.RocketDesign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
        Vec3 position = entity.getPosition(partialTick).subtract(camera);
        float size = 0.24F + design.caliber() * 0.10F;
        int colour = design.colorAt((entity.tickCount % 40) / 40.0F);

        // The flame front is hotter than the salt that is colouring it, so the core is pushed
        // towards white before the shell's own colour is applied.
        float r = Math.min(1.0F, ((colour >> 16) & 0xFF) / 255.0F * 1.15F + 0.35F);
        float g = Math.min(1.0F, ((colour >> 8) & 0xFF) / 255.0F * 1.15F + 0.35F);
        float b = Math.min(1.0F, (colour & 0xFF) / 255.0F * 1.15F + 0.35F);

        pose.pushPose();
        pose.translate(position.x, position.y + 0.05, position.z);
        pose.mulPose(this.entityRenderDispatcher.camera.rotation());
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        quad(glow, pose.last().pose(), size * 1.6F, r, g, b, 0.95F);
        pose.popPose();

        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.02D) {
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
            return;
        }

        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.mulPose(this.entityRenderDispatcher.camera.rotation());
        Matrix4f matrix = pose.last().pose();
        VertexConsumer trail = buffers.getBuffer(RenderType.entityTranslucentEmissive(TRAIL));

        float length = (float) Math.min(4.5D, 0.8D + speed * 2.2D);
        float width = size * 0.8F;
        float nx = (float) motion.x;
        float ny = (float) motion.y;
        float nz = (float) motion.z;
        float norm = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (norm > 1.0E-4F) {
            nx /= norm;
            ny /= norm;
            nz /= norm;
        }
        // The tail is projected along the velocity vector, so it always points where the shell came
        // from regardless of the viewing angle.
        trail.vertex(matrix, -width, -width, 0.0F).uv(0.0F, 1.0F)
                .color(r, g, b, 0.6F).uv2(LightTexture.FULL_BRIGHT).endVertex();
        trail.vertex(matrix, -width, width, 0.0F).uv(0.0F, 0.0F)
                .color(r, g, b, 0.6F).uv2(LightTexture.FULL_BRIGHT).endVertex();
        trail.vertex(matrix, -nx * length - width, -ny * length + width, -nz * length)
                .uv(1.0F, 0.0F).color(r, g, b, 0.0F).uv2(LightTexture.FULL_BRIGHT).endVertex();
        trail.vertex(matrix, -nx * length - width, -ny * length - width, -nz * length)
                .uv(1.0F, 1.0F).color(r, g, b, 0.0F).uv2(LightTexture.FULL_BRIGHT).endVertex();
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, float size, float r, float g,
                             float b, float alpha) {
        buffer.vertex(matrix, -size, -size, 0.0F).uv(0.0F, 1.0F).color(r, g, b, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, -size, size, 0.0F).uv(0.0F, 0.0F).color(r, g, b, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, size, size, 0.0F).uv(1.0F, 0.0F).color(r, g, b, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, size, -size, 0.0F).uv(1.0F, 1.0F).color(r, g, b, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    @Nullable
    @Override
    public ResourceLocation getTextureLocation(AbstractPyroShellEntity entity) {
        return GLOW;
    }
}
