package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.StepSequencerScreen;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

/** Real UI input and accepted-state checks; captures are not listening acceptance. */
public final class StepProbe {
  private StepProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    if (Boolean.getBoolean("modularcraft.qa.load") || tick < 85) return;
    Minecraft mc = Minecraft.getInstance();
    if (tick == 85) {
      mc.options.guiScale().set(3);
      mc.options.hideGui = true;
      mc.resizeDisplay();
    }
    if (tick >= 400) {
      if (tick == 400) {
        if (mc.screen instanceof StepSequencerScreen s) s.onClose();
        mc.options.hideGui = true;
        mc.options.fov().set(35);
      }
      if (tick == 420) {
        verifyPatch();
        if (mc.screen != null || !mc.options.hideGui)
          throw new IllegalStateException("Step patch overlay visible");
        capture("world");
      }
      if (tick == 460) {
        for (String name : new String[] {"full", "compact", "recorded", "world"})
          if (!Files.isRegularFile(
              mc.gameDirectory.toPath().resolve("screenshots/step-" + name + ".png")))
            throw new IllegalStateException("Missing step screenshot: " + name);
        System.out.println("MODULARCRAFT_QA step documentation screenshots=4 PASS");
      }
      return;
    }
    if (!(mc.screen instanceof StepSequencerScreen screen))
      throw new IllegalStateException("Missing step editor " + tick);
    parkCursor(mc);
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

  // Borrow Minecraft's callback; a host cursor warp alone can be ignored by an unfocused window.
  @SuppressWarnings("PMD.CloseResource")
  private static void parkCursor(Minecraft mc) {
    long window = mc.getWindow().getWindow();
    GLFWCursorPosCallback callback = GLFW.glfwSetCursorPosCallback(window, null);
    if (callback == null) throw new IllegalStateException("Missing Minecraft cursor callback");
    GLFW.glfwSetCursorPosCallback(window, callback);
    GLFW.glfwSetCursorPos(window, 4, 4);
    callback.invoke(window, 4, 4);
    if (mc.mouseHandler.xpos() != 4 || mc.mouseHandler.ypos() != 4)
      throw new IllegalStateException("Step capture cursor remains over controls");
  }

  private static void verifyPatch() {
    var patch =
        ClientRuntime.patches().stream()
            .filter(p -> p.steps().size() == 1)
            .findFirst()
            .orElseThrow();
    if (patch.modules().size() != 4
        || patch.cables().size() != 4
        || patch.steps().values().stream().noneMatch(s -> s.clock().running()))
      throw new IllegalStateException(
          "Step screenshot needs four modules/four cables and playback");
    for (ModuleKind kind :
        new ModuleKind[] {
          ModuleKind.STEP_SEQUENCER, ModuleKind.OSCILLATOR, ModuleKind.AMP, ModuleKind.SPEAKER
        })
      if (patch.modules().stream().filter(m -> m.kind() == kind).count() != 1)
        throw new IllegalStateException("Missing melody patch block: " + kind);
    var seq =
        patch.modules().stream()
            .filter(m -> m.kind() == ModuleKind.STEP_SEQUENCER)
            .findFirst()
            .orElseThrow();
    var amp =
        patch.modules().stream().filter(m -> m.kind() == ModuleKind.AMP).findFirst().orElseThrow();
    if (seq.value(Parameter.STEP_3) != -1
        || seq.value(Parameter.GATE_TIME) != 80
        || amp.value(Parameter.DRONE) != 0
        || patch.modules().stream().noneMatch(m -> m.kind() == ModuleKind.SPEAKER && m.enabled())
        || patch.cables().stream()
                .filter(c -> c.output() == Port.CV_OUT && c.input() == Port.PITCH_IN)
                .count()
            != 1
        || patch.cables().stream()
                .filter(c -> c.output() == Port.GATE_OUT && c.input() == Port.GATE_IN)
                .count()
            != 1
        || patch.cables().stream()
                .filter(c -> c.output() == Port.AUDIO_OUT && c.input() == Port.AUDIO_IN)
                .count()
            != 2) throw new IllegalStateException("Melody screenshot does not match RP-009");
    System.out.println(
        "MODULARCRAFT_QA step RP-009 accepted modules=4 cables=4 gateMs=80 rest=3 PASS");
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
