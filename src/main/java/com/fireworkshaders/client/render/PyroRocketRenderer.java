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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

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
 *
 * <p>Coordinates below are relative to the shell. The pose handed to an entity renderer already
 * carries the entity's offset from the camera and no rotation at all — see vanilla's own name tags,
 * which start from the same pose and billboard it themselves — so the renderer never translates by
 * the entity's world position; doing that again would draw every shell at twice its distance.
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

        // The head: a camera-facing quad a hair above the shell's origin, billboarded by the camera
        // rotation exactly the way a name tag is.
        pose.pushPose();
        pose.translate(0.0F, 0.05F, 0.0F);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        quad(glow, pose.last(), size * 1.6F, r, g, b, 0.95F);
        pose.popPose();

        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.02D) {
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
            return;
        }

        // The tail is a ribbon laid along the real flight path, so it always points where the shell
        // came from no matter where the camera is. That is why it is built in world axes and not
        // inside a camera-aligned pose: a velocity vector written as local coordinates of a rotated
        // pose only happens to point backwards while the camera faces -Z, and swings around like a
        // clock hand as the player turns.
        Vec3 direction = motion.scale(1.0D / speed);
        Vec3 side = direction.cross(new Vec3(this.entityRenderDispatcher.camera.getLookVector()));
        if (side.lengthSqr() < 1.0E-6D) {
            // Coming straight at or away from the camera: the ribbon has no width to speak of, so
            // any perpendicular will do - a shell heading for your face is a dot either way.
            side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        float length = (float) Math.min(4.5D, 0.8D + speed * 2.2D);
        float width = size * 0.8F;
        Vec3 halfWidth = side.normalize().scale(width);
        Vec3 butt = direction.scale(-length);
        // The ribbon's own facing: perpendicular to the flight path and pointing back at the camera,
        // which is the side a shaderpack's bloom and speculars expect to find.
        Vec3 normal = direction.cross(halfWidth).normalize();

        PoseStack.Pose trailPose = pose.last();
        VertexConsumer trail = buffers.getBuffer(RenderType.entityTranslucentEmissive(TRAIL));
        vertex(trail, trailPose, halfWidth, r, g, b, 0.6F, 0.0F, 0.0F, normal);
        vertex(trail, trailPose, halfWidth.scale(-1.0D), r, g, b, 0.6F, 0.0F, 1.0F, normal);
        vertex(trail, trailPose, butt.subtract(halfWidth), r, g, b, 0.0F, 1.0F, 1.0F, normal);
        vertex(trail, trailPose, butt.add(halfWidth), r, g, b, 0.0F, 1.0F, 0.0F, normal);

        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float size, float r, float g,
                             float b, float alpha) {
        Vec3 normal = new Vec3(0.0D, 0.0D, 1.0D);
        vertex(buffer, pose, new Vec3(-size, -size, 0.0D), r, g, b, alpha, 0.0F, 1.0F, normal);
        vertex(buffer, pose, new Vec3(-size, size, 0.0D), r, g, b, alpha, 0.0F, 0.0F, normal);
        vertex(buffer, pose, new Vec3(size, size, 0.0D), r, g, b, alpha, 1.0F, 0.0F, normal);
        vertex(buffer, pose, new Vec3(size, -size, 0.0D), r, g, b, alpha, 1.0F, 1.0F, normal);
    }

    /**
     * Appends one vertex, filling <em>every</em> element of the render type's format and filling
     * them in the order that format declares them.
     *
     * <p>{@code entityTranslucentEmissive} draws through {@code DefaultVertexFormat.NEW_ENTITY},
     * which is position, colour, UV0, UV1 (overlay), UV2 (lightmap), normal — plus a padding element
     * the buffer skips on its own. {@code BufferBuilder} refuses to close a vertex that has not
     * consumed all of them, and the per-element setters it inherits from {@code BufferVertexConsumer}
     * quietly do <em>nothing</em> when they are handed an element the vertex is not currently on.
     * A chain that never sends a normal therefore stalls at UV0, and the very first shell drawn dies
     * in {@code endVertex()} with "Not filled all elements of the vertex".
     *
     * <p>The normal is given in the pose's local frame, the same contract vanilla's entity models
     * work to.
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 position, float r,
                               float g, float b, float alpha, float u, float v, Vec3 normal) {
        buffer.vertex(pose.pose(), (float) position.x, (float) position.y, (float) position.z)
                .color(r, g, b, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
    }

    @Nullable
    @Override
    public ResourceLocation getTextureLocation(AbstractPyroShellEntity entity) {
        return GLOW;
    }
}
