package com.malevolentgods.modularcraft.client.mixin;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineAccess {
  @Accessor("executor")
  SoundEngineExecutor modularcraft$executor();

  @Accessor("library")
  Library modularcraft$library();

  @Accessor("loaded")
  boolean modularcraft$loaded();
}
