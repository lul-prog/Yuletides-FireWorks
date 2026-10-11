package com.fireworkshaders.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Particle render types.
 *
 * <p><b>The shader compatibility decision, in full.</b> It would be easy to write a custom GLSL
 * program for glowing sparks, and it would be the wrong call. Iris/Oculus shaderpacks work by
 * replacing the shader programs <em>vanilla already uses</em>: they patch the particle program, the
 * terrain program, the entity program. A hand-rolled program is not one of those, so a shaderpack
 * can never apply its bloom, its tonemapping, its fog or its exposure to it — the very sparks that
 * are supposed to glow would be the only flat-looking thing on screen.
 *
 * <p>So every type below renders through {@code GameRenderer#getParticleShader} against the vanilla
 * particle atlas. The "emissive" look is built from three things a shaderpack <em>does</em> see:
 * <ul>
 *   <li>a fully-bright lightmap coordinate (see {@code Particle#getLightColor}), so no world
 *       lighting is multiplied in,</li>
 *   <li>additive blending, so overlapping sparks sum instead of overwriting,</li>
 *   <li>bright, soft, white-cored textures — additive stacking pushes the core past most packs'
 *       bloom threshold, which is exactly what makes it look like it emits light.</li>
 * </ul>
 */
public final class FSRenderTypes {

    /**
     * The workhorse: sparks, glitter, flashes, shockwaves, spectrum streamers.
     *
     * <p>{@code depthMask(false)} matters more than it looks: with the depth buffer writable, a
     * bright near spark occludes the smoke behind it and a burst turns into a pile of black
     * silhouettes. Additive light should never write depth.
     */
    public static final ParticleRenderType EMISSIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, net.minecraft.client.renderer.texture.TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "yuletide:emissive";
        }
    };

    /**
     * Heavy embers. Deliberately <em>not</em> additive.
     *
     * <p>An ember is a lump of burning material: dense, opaque, and often seen in clouds hundreds
     * strong. Additive blending on a cloud that dense stacks to pure white and every ember in it
     * becomes the same flat blob — the exact failure mode that makes cheap firework mods look like
     * a screen wipe. Alpha blending keeps the individual coals legible and lets a shaderpack's
     * bloom pick out only the genuinely bright ones, so a shower of embers reads as hundreds of
     * separate burning objects rather than one glow.
     */
    public static final ParticleRenderType EMBER = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, net.minecraft.client.renderer.texture.TextureManager manager) {
            // No depth write, but depth *testing* stays on: an ember behind a hill is still hidden,
            // while two embers in front of each other blend instead of z-fighting.
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "yuletide:ember";
        }
    };

    /**
     * Lingering smoke. Deliberately <em>not</em> emissive: smoke is lit by the world, not by itself,
     * so it uses the standard translucent blend and the standard lightmap. It is the smoke that
     * makes a burst read as a physical event happening in a lit space rather than a flat sprite
     * overlay, and it is the one element that must not be additive.
     */
    public static final ParticleRenderType SMOKE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, net.minecraft.client.renderer.texture.TextureManager manager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "yuletide:smoke";
        }
    };

    private FSRenderTypes() {}
}
