package com.fuzs.fivefeetsmall.core.mixin;

import com.fuzs.fivefeetsmall.entity.EntitySize;
import com.fuzs.fivefeetsmall.entity.Pose;
import com.fuzs.fivefeetsmall.network.datasync.PoseSerializer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin extends EntityLivingBase {

    private static final EntitySize SLEEPING_SIZE = EntitySize.fixed(0.2F, 0.2F);
    private static final EntitySize STANDING_SIZE = EntitySize.flexible(0.6F, 1.8F);
    private static final Map<Pose, EntitySize> SIZE_BY_POSE = ImmutableMap.<Pose, EntitySize>builder().put(Pose.STANDING, STANDING_SIZE).put(Pose.SLEEPING, SLEEPING_SIZE).put(Pose.FALL_FLYING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SWIMMING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SPIN_ATTACK, EntitySize.flexible(0.6F, 0.6F)).put(Pose.CROUCHING, EntitySize.flexible(0.6F, 1.5F)).put(Pose.DYING, EntitySize.fixed(0.2F, 0.2F)).build();
    private static final DataParameter<Pose> POSE = EntityDataManager.createKey(EntityPlayer.class, PoseSerializer.POSE);

    protected boolean eyesInWater;
    protected boolean eyesInWaterPlayer;
    private EntitySize size;
    private float eyeHeight;

    @Shadow
    public PlayerCapabilities capabilities;

    public EntityPlayerMixin(World worldIn) {

        super(worldIn);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo callbackInfo) {

        this.size = EntitySize.flexible(0.6F, 1.8F);
        this.eyeHeight = this.getEyeHeight(Pose.STANDING, this.size);
        this.dataManager.register(POSE, Pose.STANDING);
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {

        if (POSE.equals(key)) {

            this.recalculateSize();
        }

        super.notifyDataManagerChange(key);
    }

    public void onEntityUpdate() {

        super.onEntityUpdate();

        // updateAquatics
        this.updateEyesInWater();
        this.updateSwimming();
    }

    public boolean canSwim() {
        return this.eyesInWater && this.isInWater();
    }

    public void updateSwimming() {

        if (this.isSwimming()) {

            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isRiding());
        } else {

            this.setSwimming(this.isSprinting() && this.canSwim() && !this.isRiding());
        }
    }

    private void updateEyesInWater() {

        this.eyesInWater = this.isInsideOfMaterial(Material.WATER);
    }

    protected boolean updateEyesInWaterPlayer() {

        this.eyesInWaterPlayer = this.isInsideOfMaterial(Material.WATER);
        return this.eyesInWaterPlayer;
    }

    public EntitySize getSize(Pose poseIn) {

        return poseIn == Pose.SLEEPING ? SLEEPING_SIZE : SIZE_BY_POSE.getOrDefault(poseIn, STANDING_SIZE);
    }

    public void recalculateSize() {

        EntitySize entitysize = this.size;
        Pose pose = this.getPose();
        EntitySize entitysize1 = this.getSize(pose);
        this.size = entitysize1;
        this.eyeHeight = this.getEyeHeight(pose, entitysize1);
        if (entitysize1.width < entitysize.width) {

            double d0 = (double)entitysize1.width / 2.0D;
            this.setEntityBoundingBox(new AxisAlignedBB(this.posX - d0, this.posY, this.posZ - d0, this.posX + d0, this.posY + (double) entitysize1.height, this.posZ + d0));
        } else {

            AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();
            this.setEntityBoundingBox(new AxisAlignedBB(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ, axisalignedbb.minX + (double) entitysize1.width, axisalignedbb.minY + (double) entitysize1.height, axisalignedbb.minZ + (double) entitysize1.width));
            if (entitysize1.width > entitysize.width && !this.firstUpdate && !this.world.isRemote) {

                float f = entitysize.width - entitysize1.width;
                this.move(MoverType.SELF, f, 0.0, f);
            }

        }
    }

    protected float getEyeHeight(Pose poseIn, EntitySize sizeIn) {

        return sizeIn.height * 0.85F;
    }

    @SideOnly(Side.CLIENT)
    public float getEyeHeight(Pose poseIn) {

        return this.getEyeHeight(poseIn, this.getSize(poseIn));
    }

    @Overwrite
    public final float getEyeHeight() {

        return this.eyeHeight;
    }

    @Shadow
    public abstract boolean isSpectator();

    protected void setPose(Pose poseIn) {

        this.dataManager.set(POSE, poseIn);
    }

    public Pose getPose() {

        return this.dataManager.get(POSE);
    }

    protected boolean isPoseClear(Pose poseIn) {

        return this.world.getCollisionBoxes(this, this.getBoundingBox(poseIn)).isEmpty();
    }

    @Inject(method = "onUpdate", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/entity/player/EntityPlayer;isPlayerSleeping()Z", ordinal = 0))
    public void onUpdate(CallbackInfo callbackInfo) {

        this.updateEyesInWaterPlayer();
    }

    @Overwrite
    protected void updateSize() {

        this.updatePose();
        FMLCommonHandler.instance().onPlayerPostTick((EntityPlayer) (Object) this);
        // TODO
        System.out.println(this.getPose());
    }


    protected void updatePose() {

        if (this.isPoseClear(Pose.SWIMMING)) {

            Pose pose;
            if (this.isElytraFlying()) {

                pose = Pose.FALL_FLYING;
            } else if (this.isPlayerSleeping()) {

                pose = Pose.SLEEPING;
            } else if (this.isSwimming()) {

                pose = Pose.SWIMMING;
            } else if (this.isSneaking() && !this.capabilities.isFlying) {

                pose = Pose.CROUCHING;
            } else {

                pose = Pose.STANDING;
            }

            Pose pose1;
            if (!this.isSpectator() && !this.isRiding() && !this.isPoseClear(pose)) {

                if (this.isPoseClear(Pose.CROUCHING)) {

                    pose1 = Pose.CROUCHING;
                } else {

                    pose1 = Pose.SWIMMING;
                }
            } else {

                pose1 = pose;
            }

            this.setPose(pose1);

            // update those as they're still in use in a lot of places
            this.width = this.getSize(pose1).width;
            this.height = this.getSize(pose1).height;
        }
    }

    protected AxisAlignedBB getBoundingBox(Pose p_213321_1_) {
        
        EntitySize entitysize = this.getSize(p_213321_1_);
        float f = entitysize.width / 2.0F;
        Vec3d vec3d = new Vec3d(this.posX - (double)f, this.posY, this.posZ - (double)f);
        Vec3d vec3d1 = new Vec3d(this.posX + (double)f, this.posY + (double)entitysize.height, this.posZ + (double)f);
        return new AxisAlignedBB(vec3d, vec3d1);
    }

    public boolean isSwimming() {

        return !this.capabilities.isFlying && !this.isSpectator() && this.getFlag(4);
    }

    public boolean isActuallySwimming() {

        return this.getPose() == Pose.SWIMMING || !this.isElytraFlying() && this.getPose() == Pose.FALL_FLYING;
    }

    public void setSwimming(boolean flag) {

        this.setFlag(4, flag);
    }

}