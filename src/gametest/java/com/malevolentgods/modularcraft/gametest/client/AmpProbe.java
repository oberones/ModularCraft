package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.ModuleScreen;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;

/** Real native widget checks and screenshots; not listening or multiplayer acceptance. */
public final class AmpProbe {
  private AmpProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    if (Boolean.getBoolean("modularcraft.qa.load")) return;
    Minecraft mc = Minecraft.getInstance();
    if (tick == 90) {
      mc.options.guiScale().set(1);
      mc.resizeDisplay();
    }
    if (tick == 100 || tick == 230) {
      if (!(mc.screen instanceof ModuleScreen))
        throw new IllegalStateException("Amp/Speaker panel missing");
      capture(tick == 100 ? "amp" : "speaker");
    }
    if (tick == 110 || tick == 150) {
      if (!(mc.screen instanceof ModuleScreen screen))
        throw new IllegalStateException("Amp panel missing");
      // Scroll the real panel to expose its Drone control, then activate its widget.
      screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -20);
      Button drone =
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .filter(b -> b.getMessage().getString().startsWith("Drone:"))
              .findFirst()
              .orElseThrow();
      screen.mouseClicked(drone.getX() + 3, drone.getY() + 3, 0);
      screen.mouseReleased(drone.getX() + 3, drone.getY() + 3, 0);
    }
    if (tick == 130 || tick == 170) {
      boolean changed =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .anyMatch(m -> m.kind() == ModuleKind.AMP && m.value(Parameter.DRONE) == 1);
      if (changed != (tick == 130)) throw new IllegalStateException("Drone widget edit failed");
      System.out.println("MODULARCRAFT_QA amp Drone widget PASS tick=" + tick);
    }
    if (tick == 180) {
      if (!(mc.screen instanceof ModuleScreen screen))
        throw new IllegalStateException("Amp panel missing");
      Button gate =
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .filter(b -> b.getMessage().getString().startsWith("Hold Gate"))
              .findFirst()
              .orElseThrow();
      screen.mouseClicked(gate.getX() + 3, gate.getY() + 3, 0);
    }
    if (tick == 190 || tick == 205) {
      boolean held =
          ClientRuntime.patches().stream().anyMatch(p -> p.baseGates().containsValue(true));
      if (held != (tick == 190))
        throw new IllegalStateException("Amp manual gate did not follow press/release");
      System.out.println("MODULARCRAFT_QA amp manual gate PASS tick=" + tick);
    }
    if (tick == 195 && mc.screen instanceof ModuleScreen screen) screen.mouseReleased(0, 0, 0);
    if (tick == 240 || tick == 260) {
      if (!(mc.screen instanceof ModuleScreen screen))
        throw new IllegalStateException("Speaker panel missing");
      Button toggle =
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .filter(b -> b.getMessage().getString().endsWith("speaker"))
              .findFirst()
              .orElseThrow();
      screen.mouseClicked(toggle.getX() + 3, toggle.getY() + 3, 0);
      screen.mouseReleased(toggle.getX() + 3, toggle.getY() + 3, 0);
    }
    if (tick == 250 || tick == 275) {
      boolean enabled =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .anyMatch(m -> m.kind() == ModuleKind.SPEAKER && m.enabled());
      if (enabled != (tick == 275)) throw new IllegalStateException("Speaker toggle edit failed");
      System.out.println("MODULARCRAFT_QA speaker toggle PASS tick=" + tick);
    }
    if (tick == 285 && mc.screen != null) mc.screen.onClose();
    if (tick == 300) {
      mc.options.hideGui = true;
      mc.options.fov().set(40);
    }
    if (tick == 310) capture("four-voices");
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void capture(String name) {
    Minecraft mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "amp-" + name + ".png",
        mc.getMainRenderTarget(),
        message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }
}
