package com.malevolentgods.modularcraft.client.mixin;

import com.malevolentgods.modularcraft.game.ServerRuntime;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** IntegratedServer skips NeoForge tick events while paused, so observe its pause boundary here. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedPause {
  @Inject(method = "tickServer", at = @At("HEAD"))
  private void modularcraft$pause(BooleanSupplier time, CallbackInfo ci) {
    ServerRuntime.pause((IntegratedServer) (Object) this, Minecraft.getInstance().isPaused());
  }
}
