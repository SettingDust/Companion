package snownee.companion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import snownee.companion.Companion;
import snownee.companion.CompanionCommonConfig;
import snownee.companion.CompanionPlayer;
import snownee.companion.Hooks;

@Mixin(value = Player.class, priority = 1050)
public abstract class PlayerMixin implements CompanionPlayer {

	@Unique
	private double companion$xOld;
	@Unique
	private double companion$zOld;

	@Unique
	public void companion$setOldPosition(double x, double z) {
		this.companion$xOld = x;
		this.companion$zOld = z;
	}

	@Override
	public double companion$getOwnerAwaySpeed(double petX, double petZ) {
		Player player = (Player) (Object) this;
		double currentX = player.getX();
		double currentZ = player.getZ();

		double currentDist = Math.sqrt((currentX - petX) * (currentX - petX) + (currentZ - petZ) * (currentZ - petZ));
		double oldDist = Math.sqrt((companion$xOld - petX) * (companion$xOld - petX) + (companion$zOld - petZ) * (companion$zOld - petZ));

		return currentDist - oldDist;
	}

	@Inject(at = @At("TAIL"), method = "aiStep")
	private void companion_aiStep(CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (player.level().isClientSide) {
			return;
		}
		companion$setOldPosition(player.getX(), player.getZ());
		if (player.isSleeping() || player.isInPowderSnow) {
			removeEntitiesOnShoulder();
			return;
		}
		if (CompanionCommonConfig.shoulderDismountInWater && player.isInWater()) {
			removeEntitiesOnShoulder();
			return;
		}
		if (CompanionCommonConfig.shoulderDismountUnderWater && player.isUnderWater()) {
			removeEntitiesOnShoulder();
			return;
		}
		if (player.fallDistance > CompanionCommonConfig.shoulderDismountFallDistance) {
			removeEntitiesOnShoulder();
			return;
		}
		if (CompanionCommonConfig.shoulderDismountWhileFlying && player.getAbilities().flying) {
			removeEntitiesOnShoulder();
			return;
		}
		//TODO under lava???
	}

	@Inject(at = @At("TAIL"), method = "hurt")
	private void companion_hurt(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> ci) {
		if (f > CompanionCommonConfig.shoulderDismountDamageThreshold) {
			removeEntitiesOnShoulder();
		}
	}

	@Shadow
	protected abstract void removeEntitiesOnShoulder();

	@Unique
	private Vec3 companion$jumpPos;

	@Override
	public Vec3 companion$getJumpPos() {
		return companion$jumpPos;
	}

	@Override
	public void companion$setJumpPos(Vec3 pos) {
		this.companion$jumpPos = pos;
	}

	@Override
	public void companion$removeShoulderEntities() {
		removeEntitiesOnShoulder();
	}

	@Inject(at = @At("HEAD"), method = "jumpFromGround")
	private void companion_jumpFromGround(CallbackInfo ci) {
		companion$jumpPos = ((Player) (Object) this).position();
	}

	@Inject(at = @At("HEAD"), method = "attack", cancellable = true)
	private void companion_attack(Entity entity, CallbackInfo ci) {
		if (Hooks.getEntityOwner(entity) == (Object) this) {
			Player self = (Player) (Object) this;
			if (!self.level().getGameRules().getBoolean(Companion.PET_FRIENDLY_FIRE)) {
				ci.cancel();
			}
		}
	}

}
