package com.malevolentgods.modularcraft.client.mixin;

import com.malevolentgods.modularcraft.client.AudioOutput;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Release our source/buffers before Minecraft flushes its executor or destroys its device. */
@Mixin(SoundEngine.class)
public abstract class SoundLifecycle {
  @Inject(
      method = {"stopAll", "destroy", "emergencyShutdown"},
      at = @At("HEAD"))
  private void modularcraft$release(CallbackInfo ci) {
    AudioOutput.release((SoundEngine) (Object) this);
  }
}
