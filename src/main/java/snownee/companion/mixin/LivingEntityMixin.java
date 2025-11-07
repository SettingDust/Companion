package snownee.companion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import snownee.companion.CompanionCommonConfig;
import snownee.companion.CompanionLivingEntity;
import snownee.companion.CompanionPlayer;
import snownee.companion.CompanionTamableAnimal;
import snownee.companion.Hooks;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements CompanionLivingEntity {

	@Unique
	private boolean companion$isFollowingOwner;

	@Unique
	private float companion$targetSpeed = -1;

	@Unique
	private double companion$lastDistSqr = Double.NaN;

	@SuppressWarnings("ConstantValue")
	@Inject(at = @At("TAIL"), method = "hurt")
	private void companion_hurt(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> ci) {
		if (CompanionCommonConfig.petTeleportToOwnerWhenInjured
				&& !damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)
				&& (Object) this instanceof TamableAnimal) {
			((CompanionTamableAnimal) this).companion$tryTeleportToOwner(damageSource);
		}
	}

	@WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
	private void companion_setHealth(LivingEntity entity, float health, Operation<Void> original) {
		if (Hooks.isImmortalDying(entity)) {
			health = 1;
		}
		original.call(entity, health);
	}

	@Inject(method = "baseTick", at = @At("HEAD"))
	private void companion_baseTick(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.tickCount % 20 == 0 && Hooks.isImmortalDying(self)) {
			self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
		}
	}

	@Inject(method = "travel", at = @At("HEAD"))
	private void companion_travel(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (companion$isFollowingOwner && self instanceof TamableAnimal tamable) {
			LivingEntity owner = tamable.getOwner();
			if (owner instanceof CompanionPlayer companionPlayer) {
				double ownerSpeed = companionPlayer.companion$getOwnerAwaySpeed(self.getX(), self.getZ());
				if (ownerSpeed >= 0.01F) {
					double currentDistSqr = tamable.distanceToSqr(owner);
					if (!Double.isNaN(companion$lastDistSqr)) {
						double distChange = companion$lastDistSqr - currentDistSqr;
						if (Math.abs(distChange) >= 0.001) {
							float lastDist = (float) Math.sqrt(companion$lastDistSqr);
							float currentDist = (float) Math.sqrt(currentDistSqr);
							float approachSpeed = Math.abs(lastDist - currentDist);

							double speedRatio = ownerSpeed / approachSpeed;
							float targetSpeed = self.getSpeed();
							if (speedRatio > 1.0) {
								targetSpeed = (float) (self.getSpeed() * speedRatio);
							}

							if (companion$targetSpeed == -1 || Double.isNaN(companion$targetSpeed)) {
								companion$targetSpeed = targetSpeed;
							} else {
								float smoothingFactor = 0.15f;
								companion$targetSpeed = companion$targetSpeed + (targetSpeed - companion$targetSpeed) * smoothingFactor;

								if (companion$targetSpeed < 0.05f) {
									companion$targetSpeed = 0.05f;
								}
							}

							self.setSpeed(companion$targetSpeed);
						}
					}
					companion$lastDistSqr = currentDistSqr;
				} else {
					companion$lastDistSqr = Double.NaN;
					companion$targetSpeed = -1;
				}
			} else {
				companion$targetSpeed = -1;
			}
		} else {
			companion$lastDistSqr = Double.NaN;
			companion$targetSpeed = -1;
		}
	}

	@Unique
	@Override
	public void companion$setFollowingOwner(boolean following) {
		this.companion$isFollowingOwner = following;
		if (!following) {
			// 重置速度相关变量
			this.companion$targetSpeed = -1;
			this.companion$lastDistSqr = Double.NaN;
		}
	}

	@Unique
	@Override
	public boolean companion$isFollowingOwner() {
		return this.companion$isFollowingOwner;
	}
}