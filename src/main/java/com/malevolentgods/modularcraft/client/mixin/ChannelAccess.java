package com.malevolentgods.modularcraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface ChannelAccess {
  @Accessor("source")
  int modularcraft$source();
}
