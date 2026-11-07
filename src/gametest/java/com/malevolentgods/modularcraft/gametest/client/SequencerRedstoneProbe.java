package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.SequencerScreen;
import com.malevolentgods.modularcraft.client.SequencerVisuals;
import com.malevolentgods.modularcraft.domain.Parameter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import org.lwjgl.glfw.GLFW;

/** Native keyboard, button-state, snapshot and power checks in the disposable sequencer world. */
final class SequencerRedstoneProbe {
  private SequencerRedstoneProbe() {}

  // Minecraft owns the borrowed client and framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  static void tick(int ticks) {
    if (ticks < 225 || ticks > 354) return;
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.screen instanceof SequencerScreen screen))
      throw new IllegalStateException("Redstone probe needs sequencer panel");
    var buttons =
        screen.children().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .toList();
    Button toggle =
        buttons.stream()
            .filter(b -> b.getMessage().getString().startsWith("Redstone Mode:"))
            .findFirst()
            .orElseThrow();
    if (ticks == 225) {
      screen.setFocused(null);
      for (int i = 0; i < screen.children().size() + 1 && screen.getFocused() != toggle; i++)
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
      if (screen.getFocused() != toggle)
        throw new IllegalStateException("Redstone toggle not keyboard reachable");
      screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    if (ticks == 235 || ticks == 265 || ticks == 310 || ticks == 340) {
      boolean controlled = ticks != 340;
      var module =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .filter(m -> m.id().equals(screen.identity()))
              .findFirst()
              .orElseThrow();
      if ((module.value(Parameter.REDSTONE_MODE) == 1) != controlled)
        throw new IllegalStateException("Redstone mode acknowledgement mismatch");
      if (buttons.get(0).active == controlled
          || buttons.get(1).active == controlled
          || !buttons.get(2).active
          || !toggle.active)
        throw new IllegalStateException("Redstone transport button eligibility mismatch");
      var seq = SequencerVisuals.get(screen.identity());
      if (seq == null || seq.running() != (ticks == 265))
        throw new IllegalStateException("Redstone playback snapshot mismatch at " + ticks);
      Screenshot.grab(
          mc.gameDirectory,
          "sequencer-redstone-" + ticks + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
      System.out.println(
          "MODULARCRAFT_QA redstone tick="
              + ticks
              + " mode="
              + controlled
              + " running="
              + seq.running()
              + " buttons=PASS");
    }
    if (ticks == 320) {
      screen.mouseClicked(toggle.getX() + 4, toggle.getY() + 4, 0);
      screen.mouseReleased(toggle.getX() + 4, toggle.getY() + 4, 0);
    }
    if (ticks == 354 && (!toggle.visible || toggle.getX() < 0 || toggle.getRight() > screen.width))
      throw new IllegalStateException("Compact redstone toggle is inaccessible");
  }
}
