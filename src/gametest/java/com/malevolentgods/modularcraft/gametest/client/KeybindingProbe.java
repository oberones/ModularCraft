package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.AudioSettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Checks that K leaves gameplay and screens alone while menu settings remain accessible. */
public final class KeybindingProbe {
  private static Screen expected;

  private KeybindingProbe() {}

  // Minecraft owns the borrowed client and screens.
  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    Minecraft mc = Minecraft.getInstance();
    if (tick == 10 || tick == 20 || tick == 30) {
      expected = tick == 10 ? null : tick == 20 ? new ChatScreen("") : new PauseScreen(true);
      mc.setScreen(expected);
      if (expected != null) {
        var event = new ScreenEvent.KeyPressed.Pre(expected, GLFW.GLFW_KEY_K, 0, 0);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || mc.screen != expected)
          throw new IllegalStateException(
              "K intercepted in " + expected.getClass().getSimpleName());
      }
      KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
    }
    if (tick == 12 || tick == 22 || tick == 32) {
      if (mc.screen != expected) throw new IllegalStateException("K replaced screen at " + tick);
      System.out.println("MODULARCRAFT_QA K leaves context unchanged tick=" + tick);
    }
    if (tick == 34) {
      expected.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(b -> b.getMessage().equals(Component.translatable("modularcraft.local_audio")))
          .findFirst()
          .orElseThrow()
          .onPress();
      if (!(mc.screen instanceof AudioSettingsScreen))
        throw new IllegalStateException("Menu audio settings did not open");
      mc.setScreen(null);
      expected = null;
      System.out.println("MODULARCRAFT_QA keybindings PASS; menu audio settings accessible");
    }
  }
}
