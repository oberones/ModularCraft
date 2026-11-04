package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.ModuleScreen;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/** Real widget input and authoritative state assertions in the disposable oscillator fixture. */
final class OscillatorNoteProbe {
  private OscillatorNoteProbe() {}

  // Minecraft owns the borrowed client and framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  static void tick(ModuleScreen screen, int tick) {
    Minecraft mc = Minecraft.getInstance();
    switch (tick) {
      case 260 -> {
        mc.options.guiScale().set(1);
        mc.resizeDisplay();
      }
      case 270 -> {
        button(screen, "Note: A ");
        button(screen, "Octave: 3 ");
        bounds(screen);
      }
      case 280 -> {
        choose(screen, "Note: ", 0);
        choose(screen, "Octave: ", 4);
      }
      case 290 -> {
        frequency(screen, 220); // Choosing both controls must send no frequency edit.
        click(screen, button(screen, "Note: "));
      }
      case 300 -> {
        Button note = button(screen, "Note: ");
        int popupTop = Math.max(4, Math.min(note.getBottom() + 2, screen.height - 172));
        screen.mouseClicked(note.getX() + 10, popupTop + 14 + 6, 0); // C#/Db by mouse.
        screen.mouseReleased(note.getX() + 10, popupTop + 20, 0);
        button(screen, "Note: C#/Db ");
        click(screen, button(screen, "Set Note"));
      }
      case 330 -> frequency(screen, KeyboardState.frequency(61));
      case 340 -> hz(screen, "432.123");
      case 360 -> {
        frequency(screen, 432.123);
        button(screen, "Note: A ");
        button(screen, "Octave: 4 ");
      }
      case 370 -> {
        choose(screen, "Note: ", 0);
        choose(screen, "Octave: ", 0);
        if (button(screen, "Set Note").active)
          throw new IllegalStateException("Out-of-range note can be applied");
        click(screen, button(screen, "Set Note"));
      }
      case 390 -> frequency(screen, 432.123);
      case 400 -> {
        choose(screen, "Octave: ", 4);
        screen.accept(state(screen), false, "permission");
        for (String label : new String[] {"Note: ", "Octave: ", "Set Note", "Apply"})
          if (button(screen, label).active)
            throw new IllegalStateException("Permission loss left editing enabled: " + label);
        click(screen, button(screen, "Set Note"));
        screen.accept(state(screen), true, "ready");
      }
      case 410 -> {
        frequency(screen, 432.123);
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        button(screen, "Note: C ");
        button(screen, "Octave: 4 ");
        bounds(screen);
      }
      case 430 -> click(screen, button(screen, "Set Note"));
      case 450 -> frequency(screen, KeyboardState.frequency(60));
      case 460 -> {
        click(screen, button(screen, "Note: "));
        key(screen, GLFW.GLFW_KEY_END);
        key(screen, GLFW.GLFW_KEY_ESCAPE);
        button(screen, "Note: C ");
        key(screen, GLFW.GLFW_KEY_ENTER);
        key(screen, GLFW.GLFW_KEY_TAB);
        if (screen.getFocused() != button(screen, "Octave: "))
          throw new IllegalStateException("Tab did not leave the dropdown for Octave");
      }
      case 470 -> {
        choose(screen, "Note: ", 2);
        choose(screen, "Octave: ", 5);
        hz(screen, "330.123");
      }
      case 490 -> {
        frequency(screen, 330.123);
        button(screen, "Note: D ");
        button(screen, "Octave: 5 ");
        screen.resize(mc, 320, 240);
        bounds(screen);
        button(screen, "Note: D ");
        button(screen, "Octave: 5 ");
      }
      case 510 -> {
        mc.resizeDisplay();
        click(screen, button(screen, "Set Note"));
      }
      case 530 -> frequency(screen, KeyboardState.frequency(74));
      case 550 -> screen.onClose();
      default -> {
        // Wait for the server and rendered frames between scripted interactions.
      }
    }
    if (tick == 275
        || tick == 295
        || tick == 335
        || tick == 395
        || tick == 425
        || tick == 455
        || tick == 495)
      Screenshot.grab(
          mc.gameDirectory,
          "notes-" + tick + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }

  private static Button button(ModuleScreen screen, String prefix) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(button -> button.getMessage().getString().startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing button " + prefix));
  }

  private static void click(ModuleScreen screen, AbstractWidget widget) {
    int x = widget.getX() + widget.getWidth() / 2, y = widget.getY() + 8;
    screen.mouseClicked(x, y, 0);
    screen.mouseReleased(x, y, 0);
  }

  private static void key(ModuleScreen screen, int key) {
    screen.keyPressed(key, 0, 0);
    screen.keyReleased(key, 0, 0);
  }

  private static void choose(ModuleScreen screen, String prefix, int index) {
    click(screen, button(screen, prefix));
    key(screen, GLFW.GLFW_KEY_HOME);
    for (int i = 0; i < index; i++) key(screen, GLFW.GLFW_KEY_DOWN);
    key(screen, GLFW.GLFW_KEY_ENTER);
  }

  private static void hz(ModuleScreen screen, String text) {
    EditBox field =
        screen.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .findFirst()
            .orElseThrow();
    field.setValue(text);
    click(screen, button(screen, "Apply"));
  }

  private static ModuleState state(ModuleScreen screen) {
    return ClientRuntime.patches().stream()
        .flatMap(patch -> patch.modules().stream())
        .filter(module -> module.id().equals(screen.identity()))
        .findFirst()
        .orElseThrow();
  }

  private static void frequency(ModuleScreen screen, double expected) {
    double actual = state(screen).value(Parameter.FREQUENCY);
    if (actual != expected)
      throw new IllegalStateException("Expected exact frequency " + expected + " got " + actual);
    System.out.println("MODULARCRAFT_QA note frequency=" + actual);
  }

  private static void bounds(ModuleScreen screen) {
    for (var child : screen.children()) {
      if (child instanceof AbstractWidget widget
          && (widget.getX() < 0
              || widget.getY() < 0
              || widget.getRight() > screen.width
              || widget.getBottom() > screen.height))
        throw new IllegalStateException(
            "Oscillator control outside screen: " + widget.getMessage());
    }
  }
}
