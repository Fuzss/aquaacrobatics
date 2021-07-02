package com.fuzs.aquaacrobatics.client.handler;

import com.fuzs.aquaacrobatics.AquaAcrobatics;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.util.math.MathHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;

/**
 * Uses Forge events to adjust water rendering so it more closely approximates 1.13+.
 *
 * Some of the code in this class is based off of Minecraft 1.16.
 */
public class FogHandler {

    private int targetFogColor = -1;
    private int prevFogColor = -1;
    private long fogAdjustTime = -1L;

    @SubscribeEvent
    public void onRenderFogDensity(EntityViewRenderEvent.FogDensity event) {
        if(event.getState().getMaterial() == Material.WATER) {
            GlStateManager.setFog(GlStateManager.FogMode.EXP2);
            float density = 0.05f;
            if(event.getEntity() instanceof EntityPlayer) {
                EntityPlayer playerEntity = (EntityPlayer)event.getEntity();
                float waterVision = ((IPlayerResizeable)playerEntity).getWaterVision();
                density -= waterVision * waterVision * 0.03F;
                Biome biome = playerEntity.world.getBiome(playerEntity.getPosition());
                if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP)) {
                    density += 0.005F;
                }
            }
            event.setDensity(density);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderFogColor(EntityViewRenderEvent.FogColors event) {
        if(event.getState().getMaterial() == Material.WATER && event.getEntity() instanceof EntityPlayer) {
            float fogRed, fogGreen, fogBlue;
            EntityPlayer playerEntity = (EntityPlayer) event.getEntity();
            long i = System.nanoTime() / 1000000L;
            /* TODO: replace with proper water color derived from 1.12 water color values */
            int j = 329011; //playerEntity.world.getBiome(new BlockPos(playerEntity.getPosition())).getWaterColor();
            if (fogAdjustTime < 0L) {
                targetFogColor = j;
                prevFogColor = j;
                fogAdjustTime = i;
            }
            int k = targetFogColor >> 16 & 255;
            int l = targetFogColor >> 8 & 255;
            int i1 = targetFogColor & 255;
            int j1 = prevFogColor >> 16 & 255;
            int k1 = prevFogColor >> 8 & 255;
            int l1 = prevFogColor & 255;
            float f = MathHelper.clamp((float)(i - fogAdjustTime) / 5000.0F, 0.0F, 1.0F);
            float f1 = MathHelper.lerp(f, (float)j1, (float)k);
            float f2 = MathHelper.lerp(f, (float)k1, (float)l);
            float f3 = MathHelper.lerp(f, (float)l1, (float)i1);
            fogRed = f1 / 255.0F;
            fogGreen = f2 / 255.0F;
            fogBlue = f3 / 255.0F;
            if (targetFogColor != j) {
                targetFogColor = j;
                prevFogColor = MathHelper.floor(f1) << 16 | MathHelper.floor(f2) << 8 | MathHelper.floor(f3);
                fogAdjustTime = i;
            }
            float f6 = ((IPlayerResizeable)playerEntity).getWaterVision();
            float f9 = Math.min(1.0F / fogRed, Math.min(1.0F / fogGreen, 1.0F / fogBlue));
            fogRed = fogRed * (1.0F - f6) + fogRed * f9 * f6;
            fogGreen = fogGreen * (1.0F - f6) + fogGreen * f9 * f6;
            fogBlue = fogBlue * (1.0F - f6) + fogBlue * f9 * f6;

            event.setRed(fogRed);
            event.setGreen(fogGreen);
            event.setBlue(fogBlue);
        } else if(event.getState().getMaterial() == Material.LAVA) {
            event.setRed(0.6f);
            event.setGreen(0.1f);
            event.setBlue(0.0f);
            fogAdjustTime = -1L;
        }
    }

    /**
     * The water overlay is pretty much invisible in 1.13+, and the 1.12 texture is very pixelated, so we simply hide it.
     */
    @SubscribeEvent
    public void onRenderWaterOverlay(RenderBlockOverlayEvent event) {
        if(event.getOverlayType() == RenderBlockOverlayEvent.OverlayType.WATER) {
            event.setCanceled(true);
        }
    }
}
