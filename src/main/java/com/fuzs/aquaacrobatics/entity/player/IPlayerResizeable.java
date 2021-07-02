package com.fuzs.aquaacrobatics.entity.player;

import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IPlayerResizeable {

    boolean canSwim();

    void updateSwimming();

    boolean getEyesInWaterPlayer();

    float getWaterVision();

    float getWidth();

    float getHeight();

    EntitySize getSize(Pose poseIn);

    void recalculateSize();

    boolean isResizingAllowed();

    boolean isActuallySneaking();

    float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn);

    void setPose(Pose poseIn);

    Pose getPose();

    boolean isPoseClear(Pose poseIn);

    boolean getShouldBeDead();

    boolean isSwimming();

    boolean isActuallySwimming();

    @SideOnly(Side.CLIENT)
    boolean isVisuallySwimming();

    void setSwimming(boolean flag);

    float getSwimAnimation(float partialTicks);

}
