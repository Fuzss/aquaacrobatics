package com.fuzs.aquaacrobatics.core.mixin;

import com.fuzs.aquaacrobatics.integration.IntegrationManager;
import com.fuzs.aquaacrobatics.integration.artemislib.ArtemisLibIntegration;
import com.fuzs.aquaacrobatics.integration.morph.MorphIntegration;
import com.fuzs.aquaacrobatics.integration.wings.WingsIntegration;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.network.datasync.PoseSerializer;
import com.fuzs.aquaacrobatics.util.math.MathHelper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.passive.EntityFlying;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import java.util.Map;

@SuppressWarnings({"unused", "ConstantConditions"})
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin extends EntityLivingBase implements IPlayerResizeable {

    private static final EntitySize STANDING_SIZE = EntitySize.flexible(0.6F, 1.8F);
    private static final Map<Pose, EntitySize> SIZE_BY_POSE = ImmutableMap.<Pose, EntitySize>builder().put(Pose.STANDING, STANDING_SIZE).put(Pose.SLEEPING, EntitySize.fixed(0.2F, 0.2F)).put(Pose.FALL_FLYING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SWIMMING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SPIN_ATTACK, EntitySize.flexible(0.6F, 0.6F)).put(Pose.CROUCHING, EntitySize.flexible(0.6F, 1.5F)).put(Pose.DYING, EntitySize.fixed(0.2F, 0.2F)).build();
    private static final DataParameter<Pose> POSE = EntityDataManager.createKey(EntityPlayer.class, PoseSerializer.POSE);

    @Shadow
    public PlayerCapabilities capabilities;
    @Shadow
    public float prevCameraYaw;
    @Shadow
    public float cameraYaw;
    @Shadow(remap = false)
    @Final  // don't accidentally write to this
    public float eyeHeight;

    protected boolean eyesInWater;
    protected boolean eyesInWaterPlayer;
    private EntitySize size;
    // Forge adds an eyeHeight field, we need a different name
    private float playerEyeHeight;
    private float previousEyeHeight;
    private float swimAnimation;
    private float lastSwimAnimation;
    private float timeUnderwater;

    public EntityPlayerMixin(World worldIn) {

        super(worldIn);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo callbackInfo) {

        this.size = EntitySize.flexible(0.6F, 1.8F);
        this.playerEyeHeight = this.getEyeHeight(Pose.STANDING, this.size);
        this.dataManager.register(POSE, Pose.STANDING);
    }

    @Override
    public void notifyDataManagerChange(@Nonnull DataParameter<?> key) {

        if (POSE.equals(key)) {

            this.recalculateEyeHeight();
            this.recalculateSize();
            if (IntegrationManager.isArtemisLibEnabled()) {

                ArtemisLibIntegration.updateSwimmingSize(this.getPlayer(), this.getPose());
            }
        }

        super.notifyDataManagerChange(key);
    }

    @Override
    public void onEntityUpdate() {

        int air = this.getAir();
        super.onEntityUpdate();
        if (ConfigHandler.MiscellaneousConfig.slowAirReplenish && air < this.getAir() && this.getAir() > 0) {

            this.setAir(Math.min(air + 4, 300));
        }

        if (this.isInWater()) {
            int i = this.isSpectator() ? 10 : 1;
            this.timeUnderwater = MathHelper.clamp(this.timeUnderwater + i, 0, 600);
        } else if (this.timeUnderwater > 0) {
            this.timeUnderwater = MathHelper.clamp(this.timeUnderwater - 10, 0, 600);
        }

        // updateAquatics
        this.updateEyesInWater();
        this.updateSwimming();
    }

    // based on 1.16
    @Override
    public float getWaterVision() {
        if (!this.isInWater()) {
            return 0.0f;
        } else {
            float f = 600.0f;
            float f1 = 100.0f;
            if (this.timeUnderwater >= 600.0f) {
                return 1.0f;
            } else {
                float f2 = MathHelper.clamp(this.timeUnderwater / 100.0f, 0.0f, 1.0f);
                float f3 = this.timeUnderwater < 100.0f ? 0.0f : MathHelper.clamp(((float)this.timeUnderwater - 100.0f) / 500.0f, 0.0f, 1.0f);
                return f2 * 0.6f + f3 * 0.39999998f;
            }
        }
    }


    @Override
    public boolean canSwim() {

        return this.eyesInWater && this.isInWater();
    }

    @Override
    public void updateSwimming() {

        if (this.capabilities.isFlying) {

            this.setSwimming(false);
        } else if (this.isSwimming()) {

            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isRiding());
        } else {

            this.setSwimming(this.isSprinting() && this.canSwim() && !this.isRiding());
        }
    }

    private void updateEyesInWater() {

        this.eyesInWater = this.isInsideOfMaterial(Material.WATER);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean updateEyesInWaterPlayer() {

        this.eyesInWaterPlayer = this.isInsideOfMaterial(Material.WATER);
        return this.eyesInWaterPlayer;
    }

    @Override
    public boolean getEyesInWaterPlayer() {

        return this.eyesInWaterPlayer;
    }

    @Override
    public final float getWidth() {

        return this.size.width;
    }

    @Override
    public final float getHeight() {

        return this.size.height;
    }

    @Override
    public EntitySize getSize(Pose poseIn) {

        return SIZE_BY_POSE.getOrDefault(poseIn, STANDING_SIZE);
    }

    @Override
    public void recalculateSize() {

        EntitySize oldSize = this.size;
        Pose pose = this.getPose();
        EntitySize newSize = this.getSize(pose);
        if (this.isResizingAllowed()) {

            this.recalculateSize(oldSize, newSize);
            // don't forget to update those
            this.width = newSize.width;
            this.height = newSize.height;
        }

        // update after calling #isResizingAllowed
        this.size = newSize;
    }

    protected void recalculateSize(EntitySize oldSize, EntitySize newSize) {

        if (newSize.width < oldSize.width) {

            double d0 = (double) newSize.width / 2.0;
            this.setEntityBoundingBox(new AxisAlignedBB(this.posX - d0, this.posY, this.posZ - d0, this.posX + d0, this.posY + (double) newSize.height, this.posZ + d0));
        } else {

            AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();
            this.setEntityBoundingBox(new AxisAlignedBB(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ, axisalignedbb.minX + (double) newSize.width, axisalignedbb.minY + (double) newSize.height, axisalignedbb.minZ + (double) newSize.width));
            if (newSize.width > oldSize.width && !this.firstUpdate && !this.world.isRemote) {

                float distance = oldSize.width - newSize.width;
                this.move(MoverType.SELF, distance, 0.0, distance);
            }
        }
    }

    private void recalculateEyeHeight() {

        Pose pose = this.getPose();
        EntitySize entitysize = this.getSize(pose);
        this.playerEyeHeight = this.getEyeHeight(pose, entitysize);
        this.previousEyeHeight = this.eyeHeight;
    }

    @Override
    public boolean isResizingAllowed() {

        if (IntegrationManager.isMorphEnabled() && MorphIntegration.isMorphing(this.getPlayer())) {

            return false;
        }

        // is another mod interfering
        final float delta = 0.025F;
        AxisAlignedBB bb = this.getEntityBoundingBox();
        // something is not right
        if (this.width < delta || this.height < delta || bb.maxX - bb.minX < delta || bb.maxY - bb.minY < delta) {

            return true;
        }

        boolean sizeIsOk = Math.abs(this.width / this.getWidth() - 1.0F) < delta && Math.abs(this.height / this.getHeight() - 1.0F) < delta;
        boolean boundingBoxIsOk = Math.abs((bb.maxX - bb.minX) / this.getWidth() - 1.0F) < delta && Math.abs((bb.maxY - bb.minY) / this.getHeight() - 1.0F) < delta;
        return sizeIsOk && boundingBoxIsOk;
    }

    protected float getEyeHeight(Pose poseIn, EntitySize sizeIn) {

        return poseIn == Pose.SLEEPING || poseIn ==  Pose.DYING ? 0.2F : this.getStandingEyeHeight(poseIn, sizeIn);
    }

    @Override
    public boolean isActuallySneaking() {

        return this.isSneaking();
    }

    @Override
    public float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {

        switch(poseIn) {

            case SWIMMING:
            case FALL_FLYING:
            case SPIN_ATTACK:
                return 0.4F;
            case CROUCHING:
                // far less than in vanilla 1.12, so better treat mods differently
                return this.eyeHeight - (this.isResizingAllowed() ? 0.35F : 0.08F);
            default:
                return this.eyeHeight;
        }
    }

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    public final void getEyeHeight(CallbackInfoReturnable<Float> callbackInfoReturnable) {

        callbackInfoReturnable.setReturnValue(this.playerEyeHeight);
    }

    @Shadow
    public abstract boolean isSpectator();

    @Override
    public void setPose(Pose poseIn) {

        this.dataManager.set(POSE, poseIn);
    }

    @Override
    public Pose getPose() {

        return this.dataManager.get(POSE);
    }

    @Override
    public boolean isPoseClear(Pose poseIn) {

        return this.world.getCollisionBoxes(this, this.getBoundingBox(poseIn)).isEmpty();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void preparePlayerToSpawn() {

        // need to overwrite whole method due to this being client exclusive
        this.setPose(Pose.STANDING);
        super.preparePlayerToSpawn();
        this.setHealth(this.getMaxHealth());
        this.deathTime = 0;
    }

    @Inject(method = "updateSize", at = @At("HEAD"), cancellable = true)
    protected void updateSize(CallbackInfo callbackInfo) {

        this.updateSwimAnimation();
        this.updateEyesInWaterPlayer();
        FMLCommonHandler.instance().onPlayerPostTick(this.getPlayer());
        // run after Forge event in case a mod still wants to do changes
        this.updatePose();
        this.updateEyeHeight();
        // cancel vanilla updateSize
        callbackInfo.cancel();
    }

    protected void updatePose() {

        if (this.getShouldBeDead()) {

            // this is completely ignored in vanilla
            this.setPose(Pose.DYING);
        } else if (this.isPlayerSleeping()) {

            // handle this before swimming pose clear check
            this.setPose(Pose.SLEEPING);
        } else if (this.isPoseClear(Pose.SWIMMING)) {

            Pose pose;
            if (IntegrationManager.isWingsEnabled() ? WingsIntegration.onFlightCheck(this.getPlayer(), this.isElytraFlying()) : this.isElytraFlying()) {

                pose = Pose.FALL_FLYING;
            } else if (this.isSwimming()) {

                pose = Pose.SWIMMING;
                // otherwise unable to sneak on client when there is not enough space for the pose, but actual player size is smaller
            } else if (this.isActuallySneaking() && !this.capabilities.isFlying && (this.onGround || !this.isInWater()) && !this.isOnLadder()) {

                pose = Pose.CROUCHING;
            } else {

                pose = Pose.STANDING;
            }

            Pose pose1;
            if (!this.noClip && !this.isRiding() && this.isResizingAllowed() && !this.isPoseClear(pose)) {

                if (this.isPoseClear(Pose.CROUCHING)) {

                    pose1 = Pose.CROUCHING;
                } else {

                    pose1 = Pose.SWIMMING;
                }
            } else {

                pose1 = pose;
            }

            this.setPose(pose1);
        }
    }

    private void updateEyeHeight() {

        if (this.eyeHeight != this.previousEyeHeight) {

            this.recalculateEyeHeight();
        }
    }

    protected AxisAlignedBB getBoundingBox(Pose p_213321_1_) {
        
        EntitySize entitysize = this.getSize(p_213321_1_);
        float f = entitysize.width / 2.0F;
        return new AxisAlignedBB(this.posX - (double) f, this.posY, this.posZ - (double) f, this.posX + (double) f, this.posY + (double) entitysize.height, this.posZ + (double) f);
    }

    @Override
    public boolean getShouldBeDead() {

        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean isSwimming() {

        return !this.capabilities.isFlying && !this.isSpectator() && this.getFlag(4);
    }

    @Override
    public boolean isActuallySwimming() {

        boolean isFallFlying = !this.isElytraFlying() && this.getPose() == Pose.FALL_FLYING;
        return this.getPose() == Pose.SWIMMING || (IntegrationManager.isWingsEnabled() ? !WingsIntegration.onFlightCheck(this.getPlayer(), !isFallFlying) : isFallFlying);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isVisuallySwimming() {

        return this.isActuallySwimming() && !this.isInWater();
    }

    @Override
    public void setSwimming(boolean flag) {

        this.setFlag(4, flag);
    }

    @Override
    public float getSwimAnimation(float partialTicks) {

        return MathHelper.lerp(partialTicks, this.lastSwimAnimation, this.swimAnimation);
    }

    private void updateSwimAnimation() {

        this.lastSwimAnimation = this.swimAnimation;
        if (this.isActuallySwimming()) {

            this.swimAnimation = Math.min(1.0F, this.swimAnimation + 0.09F);
        } else {

            this.swimAnimation = Math.max(0.0F, this.swimAnimation - 0.09F);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travel(float strafe, float vertical, float forward, CallbackInfo callbackInfo) {

        double d0 = this.posX;
        double d1 = this.posY;
        double d2 = this.posZ;
        if (this.isSwimming() && !this.isRiding()) {

            double d3 = this.getLookVec().y;
            double d4 = d3 < -0.2 ? 0.085 : 0.06;
            IBlockState fluidState = this.world.getBlockState(new BlockPos(this.posX, this.posY + 1.0 - 0.1, this.posZ));
            if (d3 <= 0.0 || this.isJumping || fluidState.getBlock() instanceof BlockLiquid || fluidState.getBlock() instanceof IFluidBlock) {

                double d5 = this.motionY;
                this.motionY += (d3 - d5) * d4;
            }
        }

        double d3 = this.motionY;
        float f = this.jumpMovementFactor;
        if (this.capabilities.isFlying && !this.isRiding()) {

            this.jumpMovementFactor = this.capabilities.getFlySpeed() * (float) (this.isSprinting() ? 2 : 1);
        }

        // replaces a section in super method, therefore super is called otherwise
        if (!this.capabilities.isFlying && this.isInWater()) {

            if (this.isServerWorld() || this.canPassengerSteer()) {

                double d8 = this.posY;
                float f5 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f6 = 0.02F;
                float f7 = (float) EnchantmentHelper.getDepthStriderModifier(this);
                if (f7 > 3.0F) {

                    f7 = 3.0F;
                }

                if (!this.onGround) {

                    f7 *= 0.5F;
                }

                if (f7 > 0.0F) {

                    f5 += (0.54600006F - f5) * f7 / 3.0F;
                    f6 += (this.getAIMoveSpeed() - f6) * f7 / 3.0F;
                }

                this.moveRelative(strafe, vertical, forward, f6);
                this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                if (this.collidedHorizontally && this.isOnLadder()) {

                    this.motionY = 0.2;
                }

                this.motionX *= f5;
                this.motionY *= 0.8;
                this.motionZ *= f5;
                this.applyGravity();
                if (this.collidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6 - this.posY + d8, this.motionZ)) {

                    this.motionY = 0.3;
                }

                this.updateLimbSwing();
            }
        } else {

            super.travel(strafe, vertical, forward);
        }

        if (this.capabilities.isFlying && !this.isRiding()) {

            this.motionY = d3 * 0.6D;
            this.jumpMovementFactor = f;
            this.fallDistance = 0.0F;
            this.setFlag(7, false);
        }

        this.addMovementStat(this.posX - d0, this.posY - d1, this.posZ - d2);
        callbackInfo.cancel();
    }

    public void applyGravity() {

        if (!this.hasNoGravity() && !this.isSprinting()) {

            if (this.motionY <= 0.0 && Math.abs(this.motionY - 0.005) >= 0.003 && Math.abs(this.motionY - 0.08 / 16.0) < 0.003) {

                this.motionY = -0.003;
            } else {

                this.motionY -= 0.08 / 16.0;
            }
        }
    }

    private void updateLimbSwing() {

        this.prevLimbSwingAmount = this.limbSwingAmount;
        double d5 = this.posX - this.prevPosX;
        double d7 = this.posZ - this.prevPosZ;
        double d9 = this instanceof EntityFlying ? this.posY - this.prevPosY : 0.0;
        float f10 = MathHelper.sqrt(d5 * d5 + d9 * d9 + d7 * d7) * 4.0F;

        if (f10 > 1.0F) {

            f10 = 1.0F;
        }

        this.limbSwingAmount += (f10 - this.limbSwingAmount) * 0.4F;
        this.limbSwing += this.limbSwingAmount;
    }

    @Shadow
    public abstract void addMovementStat(double p_71000_1_, double p_71000_3_, double p_71000_5_);

    @Inject(method = "onLivingUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isRemote:Z"), cancellable = true, slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;playShoulderEntityAmbientSound(Lnet/minecraft/nbt/NBTTagCompound;)V")))
    public void onLivingUpdate(CallbackInfo callbackInfo) {

        // disable bobbing view when swimming
        float f = 0.0F;
        if (this.onGround && !this.getShouldBeDead() && !this.isSwimming()) {

            f = Math.min(0.1F, MathHelper.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ));
        }

        this.cameraYaw = this.prevCameraYaw + (f - this.prevCameraYaw) * 0.4F;
        // no longer exists in 1.13+
        this.cameraPitch = 0.0F;

        if (!ConfigHandler.MiscellaneousConfig.sneakingForParrots) {

            return;
        }

        if (!this.world.isRemote && (this.isSneaking() || this.isInWater())) {

            this.spawnShoulderEntities();
        }

        callbackInfo.cancel();
    }

    @Inject(method = "addShoulderEntity", at = @At("HEAD"), cancellable = true)
    public void addShoulderEntity(NBTTagCompound p_192027_1_, CallbackInfoReturnable<Boolean> callbackInfo) {

        if (ConfigHandler.MiscellaneousConfig.sneakingForParrots && this.isSneaking()) {

            callbackInfo.setReturnValue(false);
        }
    }

    @Shadow
    protected abstract void spawnShoulderEntities();

    @Redirect(method = "trySleep", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setSize(FF)V"))
    public void setSizeTrySleep(EntityPlayer player, float width, float height) {

        this.setPose(Pose.SLEEPING);
    }

    // removed wakeUpPlayer hook as it's not important and is conflicting with sponge forge (they're using overwrite for that method)

    private EntityPlayer getPlayer() {

        return (EntityPlayer) (Object) this;
    }

}
