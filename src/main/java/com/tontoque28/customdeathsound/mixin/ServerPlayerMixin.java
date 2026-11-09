package com.tontoque28.customdeathsound.mixin;

import com.tontoque28.customdeathsound.CustomDeathSound;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void customdeathsound$onDie(DamageSource source, CallbackInfo ci) {
        CustomDeathSound.onPlayerDeath((ServerPlayer) (Object) this);
    }
}
