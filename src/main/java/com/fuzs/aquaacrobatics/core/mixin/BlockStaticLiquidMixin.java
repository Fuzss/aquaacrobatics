package com.fuzs.aquaacrobatics.core.mixin;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockStaticLiquid.class)
public abstract class BlockStaticLiquidMixin extends BlockLiquid {
    protected BlockStaticLiquidMixin(Material materialIn) {
        super(materialIn);
    }

    @Override
    public int getLightOpacity(IBlockState state) {
        if(state.getMaterial() == Material.WATER)
            return 1;
        else
            return super.getLightOpacity(state);
    }
}
