package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.StepSequencerScreen;
import com.malevolentgods.modularcraft.domain.Parameter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.lwjgl.glfw.GLFW;

/** Real UI input and accepted-state checks; captures are not listening acceptance. */
public final class StepProbe {
  private StepProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    if (Boolean.getBoolean("modularcraft.qa.load") || tick < 85) return;
    Minecraft mc = Minecraft.getInstance();
    if (tick == 85) {
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
    }
    if (tick >= 400) {
      if (tick == 400 && mc.screen instanceof StepSequencerScreen s) s.onClose();
      if (tick == 420) capture("world");
      return;
    }
    if (!(mc.screen instanceof StepSequencerScreen screen))
      throw new IllegalStateException("Missing step editor " + tick);
    if (tick == 100) {
      click(screen, "Record");
      piano(screen);
      screen.keyPressed(GLFW.GLFW_KEY_D, 0, 0);
    }
    if (tick == 110) screen.keyPressed(GLFW.GLFW_KEY_D, 0, 0);
    if (tick == 120) {
      var state =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .filter(m -> m.id().equals(screen.identity()))
              .findFirst()
              .orElseThrow();
      if (state.value(Parameter.STEP_1) != 64 || state.value(Parameter.STEP_2) != 64)
        throw new IllegalStateException("Recording first note or repeat suppression failed");
      screen.keyReleased(GLFW.GLFW_KEY_D, 0, 0);
      screen.keyPressed(GLFW.GLFW_KEY_G, 0, 0);
      screen.keyReleased(GLFW.GLFW_KEY_G, 0, 0);
    }
    if (tick == 140) click(screen, "Rest");
    if (tick == 160) {
      var state =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .filter(m -> m.id().equals(screen.identity()))
              .findFirst()
              .orElseThrow();
      if (state.value(Parameter.STEP_2) != 67 || state.value(Parameter.STEP_3) != -1)
        throw new IllegalStateException("Advance/rest failed");
      var piano = piano(screen);
      screen.mouseClicked(piano.getX() + 2, piano.getY() + 35, 0);
      screen.mouseReleased(piano.getX() + 2, piano.getY() + 35, 0);
    }
    if (tick == 180) {
      capture("full");
      System.out.println("MODULARCRAFT_QA step typing/repeat/rest/mouse PASS");
    }
    if (tick == 200) {
      screen.resize(mc, 380, 240);
    }
    if (tick == 220) {
      for (var child : screen.children())
        if (child instanceof AbstractWidget w
            && (w.getX() < 0
                || w.getRight() > screen.width
                || w.getY() < 0
                || w.getBottom() > screen.height))
          throw new IllegalStateException("Clipped step control");
      capture("compact");
      System.out.println("MODULARCRAFT_QA step compact PASS");
    }
    if (tick == 240) {
      mc.resizeDisplay();
      click(screen, "16 ");
      click(screen, "Record");
      click(screen, "Record");
      piano(screen);
      screen.keyPressed(GLFW.GLFW_KEY_J, 0, 0);
      screen.keyReleased(GLFW.GLFW_KEY_J, 0, 0);
    }
    if (tick == 270) {
      var state =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .filter(m -> m.id().equals(screen.identity()))
              .findFirst()
              .orElseThrow();
      if (state.value(Parameter.STEP_16) != 71)
        throw new IllegalStateException("Final step entry failed");
      if (screen.children().stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .noneMatch(b -> b.getMessage().getString().equals("Record")))
        throw new IllegalStateException("Record did not finish");
      System.out.println("MODULARCRAFT_QA step finish-at-end PASS");
      capture("recorded");
    }
  }

  private static AbstractWidget piano(StepSequencerScreen screen) {
    var widget =
        screen.children().stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(w -> w.getMessage().getString().startsWith("Piano:"))
            .findFirst()
            .orElseThrow();
    screen.setFocused(widget);
    return widget;
  }

  private static void click(StepSequencerScreen screen, String prefix) {
    var b =
        screen.children().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(w -> w.getMessage().getString().startsWith(prefix))
            .findFirst()
            .orElseThrow();
    screen.mouseClicked(b.getX() + 3, b.getY() + 3, 0);
    screen.mouseReleased(b.getX() + 3, b.getY() + 3, 0);
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void capture(String name) {
    Minecraft mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory, "step-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
  }
}
