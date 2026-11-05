package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.ModuleScreen;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/** Real widget actions and accepted-state assertions in the disposable native LFO fixture. */
public final class LfoProbe {
  private LfoProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    if (Boolean.getBoolean("modularcraft.qa.load") || tick < 90 || tick > 590) return;
    if (tick > 480) {
      if (tick >= 500 && tick % 10 == 0) {
        ClientRuntime.recover("resync");
        if (com.malevolentgods.modularcraft.client.AudioOutput.channels() != 0
            || com.malevolentgods.modularcraft.client.AudioOutput.timers() != 0)
          throw new IllegalStateException("LFO audio recovery retained resources");
        System.out.println(
            "MODULARCRAFT_QA LFO cleanup cycle="
                + ((tick - 500) / 10 + 1)
                + " channels=0 timers=0");
      }
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.screen instanceof ModuleScreen screen))
      throw new IllegalStateException("LFO fixture editor missing at " + tick);
    var module =
        ClientRuntime.patches().stream()
            .flatMap(p -> p.modules().stream())
            .filter(m -> m.id().equals(screen.identity()))
            .findFirst()
            .orElseThrow();
    if (tick == 90 && module.kind() != ModuleKind.LFO)
      throw new IllegalStateException("LFO editor did not open");
    if (tick >= 100 && tick <= 140 && tick % 10 == 0) {
      var buttons =
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .toList();
      click(screen, buttons.getFirst());
    }
    if (tick == 150) edit(screen, Parameter.LFO_FREQUENCY, "2.125");
    if (tick == 160 || tick == 170) {
      String label = tick == 160 ? "Bipolar" : "1/16 note";
      click(
          screen,
          screen.children().stream()
              .filter(Button.class::isInstance)
              .map(Button.class::cast)
              .filter(b -> b.getMessage().getString().startsWith(label))
              .findFirst()
              .orElseThrow());
    }
    if (tick == 185) {
      if (module.value(Parameter.LFO_WAVE) != 1
          || module.value(Parameter.LFO_FREQUENCY) != 2.125
          || module.value(Parameter.LFO_POLARITY) != 1
          || module.value(Parameter.LFO_DIVISION) != 1)
        throw new IllegalStateException("LFO controls were not accepted: " + module.values());
      System.out.println("MODULARCRAFT_QA LFO all shapes/frequency/polarity/division accepted");
    }
    if (tick == 230) edit(screen, Parameter.RESONANCE_DEPTH, "25.125");
    if (tick == 250 && module.value(Parameter.RESONANCE_DEPTH) != 25.125)
      throw new IllegalStateException("Resonance depth not accepted");
    if (tick == 320) screen.resize(mc, 380, 240);
    if (tick >= 340 && tick <= 400 && tick % 20 == 0) {
      Parameter p =
          List.of(
                  Parameter.ATTACK_DEPTH,
                  Parameter.DECAY_DEPTH,
                  Parameter.SUSTAIN_DEPTH,
                  Parameter.RELEASE_DEPTH)
              .get((tick - 340) / 20);
      edit(screen, p, "-25.125");
    }
    if (tick == 420) edit(screen, Parameter.RELEASE_DEPTH, "101");
    if (tick == 450) {
      for (Parameter p :
          List.of(
              Parameter.ATTACK_DEPTH,
              Parameter.DECAY_DEPTH,
              Parameter.SUSTAIN_DEPTH,
              Parameter.RELEASE_DEPTH))
        if (module.value(p) != -25.125)
          throw new IllegalStateException("ADSR depth/invalid input failed: " + p);
      System.out.println(
          "MODULARCRAFT_QA LFO compact ADSR depth controls accepted; invalid depth refused");
    }
    if (tick == 90 || tick == 180 || tick == 250 || tick == 330 || tick == 410 || tick == 450) {
      for (var child : screen.children())
        if (child instanceof AbstractWidget widget
            && widget.visible
            && (widget.getY() < 0 || widget.getBottom() > screen.height))
          throw new IllegalStateException("Clipped control");
      Screenshot.grab(
          mc.gameDirectory,
          "lfo-" + tick + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
    }
    if (tick == 480) screen.onClose();
  }

  private static void click(ModuleScreen screen, AbstractWidget widget) {
    for (int i = 0; i < 8 && !widget.visible; i++)
      screen.keyPressed(widget.getY() < 30 ? GLFW.GLFW_KEY_PAGE_UP : GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
    if (!widget.visible) throw new IllegalStateException("Cannot reach selector");
    screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + 8, 0);
    screen.mouseReleased(widget.getX() + widget.getWidth() / 2.0, widget.getY() + 8, 0);
  }

  private static void edit(ModuleScreen screen, Parameter parameter, String value) {
    String label =
        net.minecraft.network.chat.Component.translatable(
                "parameter.modularcraft." + parameter.name().toLowerCase(java.util.Locale.ROOT))
            .getString();
    EditBox field =
        screen.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .filter(f -> f.getMessage().getString().equals(label))
            .findFirst()
            .orElseThrow();
    for (int i = 0; i < 8 && !field.visible; i++) {
      screen.keyPressed(field.getY() < 30 ? GLFW.GLFW_KEY_PAGE_UP : GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
    }
    if (!field.visible) throw new IllegalStateException("Cannot scroll to " + parameter);
    field.setValue(value);
    screen.mouseClicked(field.getRight() + 27, field.getY() + 7, 0);
    screen.mouseReleased(field.getRight() + 27, field.getY() + 7, 0);
  }
}
