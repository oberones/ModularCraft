package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Exercises native adapters in disposable fixtures without claiming listening acceptance. */
@Mod(value = "modularcraft_tests", dist = Dist.CLIENT)
public final class NativeProbe {
  private int ticks;

  public NativeProbe() {
    if (!Boolean.getBoolean("modularcraft.qa")) return;
    NeoForge.EVENT_BUS.addListener(
        (ClientTickEvent.Post event) -> {
          if (Minecraft.getInstance().level == null) return;
          ticks++;
          if (Boolean.getBoolean("modularcraft.qa.cableRemoval")) NativeCableProbe.tick(ticks);
          if (Boolean.getBoolean("modularcraft.qa.cables")) captureCables();
          if (Boolean.getBoolean("modularcraft.qa.keyboard")) keyboard();
          if (Boolean.getBoolean("modularcraft.qa.detune")
              || Boolean.getBoolean("modularcraft.qa.square")) oscillator();
          if (Boolean.getBoolean("modularcraft.qa.sequencer")) sequencer();
          if (Boolean.getBoolean("modularcraft.qa.lfo")) LfoProbe.tick(ticks);
          if (ticks == 1)
            System.out.println(
                "MODULARCRAFT_QA artifact="
                    + com.malevolentgods.modularcraft.ModularCraft.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation());
          if (Boolean.getBoolean("modularcraft.qa.cycles")
              && ticks >= 100
              && ticks < 300
              && ticks % 10 == 0) {
            ClientRuntime.recover("resync");
            int channels = com.malevolentgods.modularcraft.client.AudioOutput.channels();
            int timers = com.malevolentgods.modularcraft.client.AudioOutput.timers();
            if (channels != 0 || timers != 0)
              throw new IllegalStateException(
                  "Native resource leak: " + channels + " channels, " + timers + " timers");
            System.out.println(
                "MODULARCRAFT_QA cleanup cycle="
                    + ((ticks - 100) / 10 + 1)
                    + " channels=0 timers=0");
          }
          if (ticks % 100 == 0)
            System.out.println("MODULARCRAFT_QA client " + ClientRuntime.audioStats());
          if (ticks == Integer.getInteger("modularcraft.qa.ticks", 1200))
            Minecraft.getInstance().stop();
        });
  }

  // Minecraft owns this borrowed client and framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  private void sequencer() {
    Minecraft mc = Minecraft.getInstance();
    if (ticks >= 90 && ticks <= 210) {
      if (!(mc.screen instanceof com.malevolentgods.modularcraft.client.SequencerScreen screen))
        throw new IllegalStateException("Sequencer editor did not open");
      if (ticks == 100) {
        var fields =
            screen.children().stream()
                .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .toList();
        if (fields.size() != 13)
          throw new IllegalStateException("Expected tempo and twelve numeric channel controls");
        var field = fields.get(1);
        field.setValue("7");
        screen.setFocused(field);
        field.setFocused(true);
        screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
      }
      if (ticks == 130) {
        var module =
            ClientRuntime.patches().stream()
                .flatMap(p -> p.modules().stream())
                .filter(m -> m.id().equals(screen.identity()))
                .findFirst()
                .orElseThrow();
        if (module.value(com.malevolentgods.modularcraft.domain.Parameter.A_STEPS) != 7)
          throw new IllegalStateException("Sequencer numeric edit rejected");
        System.out.println("MODULARCRAFT_QA sequencer accepted A steps=7");
      }
      if (ticks == 150 || ticks == 180 || ticks == 190 || ticks == 210) {
        var buttons =
            screen.children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .toList();
        var button =
            ticks == 190
                ? buttons.get(1)
                : ticks == 210
                    ? buttons.get(0)
                    : buttons.stream()
                        .filter(
                            b ->
                                b.getMessage()
                                    .getString()
                                    .equals(ticks == 150 ? "Mute channel" : "Unmute channel"))
                        .findFirst()
                        .orElseThrow();
        screen.mouseClicked(button.getX() + 4, button.getY() + 4, 0);
        screen.mouseReleased(button.getX() + 4, button.getY() + 4, 0);
      }
      if (ticks == 170 || ticks == 200) {
        var seq = com.malevolentgods.modularcraft.client.SequencerVisuals.get(screen.identity());
        if (seq == null || seq.running() != (ticks == 170))
          throw new IllegalStateException("Sequencer transport state mismatch");
        System.out.println("MODULARCRAFT_QA sequencer tick=" + ticks + " running=" + seq.running());
      }
    }
    if (ticks == 212) {
      mc.options.guiScale().set(4);
      mc.resizeDisplay();
    }
    if (ticks == 214
        && mc.screen instanceof com.malevolentgods.modularcraft.client.SequencerScreen screen)
      for (int i = 0; i < 3; i++) screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
    if (ticks == 216
        && mc.screen instanceof com.malevolentgods.modularcraft.client.SequencerScreen screen) {
      long visible =
          screen.children().stream()
              .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
              .map(net.minecraft.client.gui.components.EditBox.class::cast)
              .filter(w -> w.visible)
              .count();
      if (visible < 4)
        throw new IllegalStateException("Compact sequencer controls are inaccessible");
    }
    if (ticks == 218) {
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
    }
    if (ticks == 220 && mc.screen != null) mc.screen.onClose();
    if (ticks == 216
        || ticks == 90
        || ticks == 130
        || ticks == 170
        || ticks == 240
        || ticks == 280
        || ticks == 320
        || ticks == 360)
      Screenshot.grab(
          mc.gameDirectory,
          "sequencer-" + ticks + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
    if (ticks >= 410 && ticks <= 600 && ticks % 10 == 0) {
      ClientRuntime.recover("resync");
      if (com.malevolentgods.modularcraft.client.AudioOutput.channels() != 0
          || com.malevolentgods.modularcraft.client.AudioOutput.timers() != 0)
        throw new IllegalStateException("Sequencer native cleanup leaked resources");
      System.out.println("MODULARCRAFT_QA sequencer cleanup=" + ((ticks - 410) / 10 + 1));
    }
  }

  // Minecraft owns this borrowed client and its framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  private void oscillator() {
    if (ticks < 90 || ticks > 550) return;
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.screen instanceof com.malevolentgods.modularcraft.client.ModuleScreen screen))
      throw new IllegalStateException("Oscillator panel did not open through server inspection");
    if (ticks == 90
        && screen.children().stream()
            .filter(net.minecraft.client.gui.components.Button.class::isInstance)
            .map(net.minecraft.client.gui.components.Button.class::cast)
            .noneMatch(button -> button.getMessage().getString().startsWith("Note: ")))
      throw new IllegalStateException("Oscillator note dropdown is missing");
    if (ticks >= 260) {
      OscillatorNoteProbe.tick(screen, ticks);
      return;
    }
    boolean square = Boolean.getBoolean("modularcraft.qa.square");
    var control =
        square
            ? com.malevolentgods.modularcraft.domain.Parameter.PULSE_WIDTH
            : com.malevolentgods.modularcraft.domain.Parameter.DETUNE;
    var detuneField =
        screen.children().stream()
            .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
            .map(net.minecraft.client.gui.components.EditBox.class::cast)
            .toList()
            .get(1);
    int applyX = detuneField.getRight() + 27;
    int rowY = detuneField.getY() + 7;
    if (ticks == 100 || ticks == 180 || ticks == 220) {
      var fields =
          screen.children().stream()
              .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
              .map(net.minecraft.client.gui.components.EditBox.class::cast)
              .toList();
      if (fields.size() != 3) throw new IllegalStateException("Expected three oscillator controls");
      fields.get(1).setValue(ticks == 100 ? "25.125" : ticks == 180 ? "-1" : square ? "50" : "0");
      screen.mouseClicked(applyX, rowY, 0);
      screen.mouseReleased(applyX, rowY, 0);
    }
    if (ticks == 140) {
      screen.mouseClicked(detuneField.getX() - 13, rowY, 0);
      screen.mouseReleased(detuneField.getX() - 13, rowY, 0);
    }
    if (ticks == 120 || ticks == 160 || ticks == 200 || ticks == 240) {
      double expected = ticks == 120 ? 25.125 : ticks == 240 ? control.initial : control.max;
      double accepted =
          ClientRuntime.patches().stream()
              .flatMap(p -> p.modules().stream())
              .filter(m -> m.id().equals(screen.identity()))
              .findFirst()
              .orElseThrow()
              .value(control);
      if (accepted != expected)
        throw new IllegalStateException(control + " expected " + expected + " got " + accepted);
      System.out.println("MODULARCRAFT_QA " + control + " tick=" + ticks + " accepted=" + accepted);
    }
    if (ticks == 90 || ticks == 120 || ticks == 160 || ticks == 240)
      Screenshot.grab(
          mc.gameDirectory,
          (square ? "square-" : "detune-") + ticks + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }

  // Minecraft owns this borrowed client and its framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  private void keyboard() {
    Minecraft mc = Minecraft.getInstance();
    if (ticks < 90 || ticks > 400) return;
    if (ticks < 300
        && !(mc.screen instanceof com.malevolentgods.modularcraft.client.KeyboardScreen))
      throw new IllegalStateException("Keyboard panel did not open through server inspection");
    if (ticks == 310 && mc.screen != null) mc.screen.onClose();
    if (ticks == 320
        && !(mc.screen instanceof com.malevolentgods.modularcraft.client.KeyboardScreen))
      throw new IllegalStateException("Keyboard failed to reopen after audio settings");
    if (mc.screen instanceof com.malevolentgods.modularcraft.client.KeyboardScreen screen) {
      int panel = Math.min(420, screen.width - 16);
      double px = (screen.width - panel) / 2.0 + 10;
      boolean compact = screen.height < 318;
      double panelTop = Math.max(0, (screen.height - (compact ? 236 : 310)) / 2);
      double py = panelTop + (compact ? 52 : 65);
      double keyHeight = compact ? 48 : 86;
      double whiteY = py + keyHeight * .85;
      double white = (panel - 20) / 7.0;
      switch (ticks) {
        case 92 -> screen.mouseClicked(px + 20, panelTop + (compact ? 129 : 186), 0);
        case 94 -> screen.mouseClicked(px + panel - 60, panelTop + (compact ? 129 : 186), 0);
        case 96 -> screen.mouseClicked(px + 30, panelTop + (compact ? 153 : 210), 0);
        case 98 -> screen.mouseClicked(px + 15, whiteY, 0);
        case 99 -> screen.mouseReleased(px + 15, whiteY, 0);
        case 100, 320 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 120 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        case 140 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        case 160 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        case 200 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 210 ->
            screen.midi(
                new com.malevolentgods.modularcraft.client.MidiInput.Event(0x90, 0, 69, 100));
        case 215 ->
            screen.midi(
                new com.malevolentgods.modularcraft.client.MidiInput.Event(0xb0, 0, 64, 127));
        case 220 ->
            screen.midi(new com.malevolentgods.modularcraft.client.MidiInput.Event(0x80, 0, 69, 0));
        case 225 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        case 235 -> {
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0, 0);
          screen.midi(new com.malevolentgods.modularcraft.client.MidiInput.Event(0xb0, 0, 64, 0));
        }
        case 250 -> screen.mouseClicked(px + white, py + 10, 0);
        case 270 -> screen.mouseDragged(px + 1.5 * white, whiteY, 0, white / 2, 60);
        case 285 -> screen.mouseDragged(px - 2, whiteY, 0, -2 * white, 0);
        case 300 ->
            mc.setScreen(new com.malevolentgods.modularcraft.client.AudioSettingsScreen(screen));
        case 335 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 340 -> {
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        }
        case 355 -> mc.player.setPos(mc.player.getX() + 64, mc.player.getY(), mc.player.getZ());
        case 358 -> {
          if (!(screen.getFocused()
                  instanceof net.minecraft.client.gui.components.AbstractWidget widget)
              || widget.active)
            throw new IllegalStateException("Out-of-range keyboard retained active input controls");
        }
        case 360 -> screen.onClose();
        default -> {}
      }
      if (ticks == 130
          || ticks == 150
          || ticks == 180
          || ticks == 230
          || ticks == 240
          || ticks == 260
          || ticks == 280
          || ticks == 295
          || ticks == 330
          || ticks == 350) {
        var note =
            ClientRuntime.patches().stream()
                .flatMap(p -> p.keyboards().entrySet().stream())
                .filter(e -> e.getKey().equals(screen.identity()))
                .findFirst()
                .orElseThrow()
                .getValue();
        int expected =
            switch (ticks) {
              case 130 -> 62;
              case 350 -> 86;
              case 150 -> 60;
              case 230, 240 -> 69;
              case 260 -> 73;
              case 280, 295 -> 74;
              default -> 72;
            };
        if (note.note() != expected || note.gate() != (ticks != 240 && ticks != 295))
          throw new IllegalStateException("Keyboard native state: " + note + " at tick " + ticks);
        System.out.println("MODULARCRAFT_QA keyboard tick=" + ticks + " " + note);
      }
    }
    if (ticks == 380
        && ClientRuntime.patches().stream()
            .anyMatch(p -> p.keyboards().values().stream().anyMatch(k -> k.gate())))
      throw new IllegalStateException("Screen close left a held keyboard gate");
    if (ticks == 216
        || ticks == 90
        || ticks == 130
        || ticks == 180
        || ticks == 230
        || ticks == 240
        || ticks == 280
        || ticks == 400)
      Screenshot.grab(
          mc.gameDirectory,
          "keyboard-" + ticks + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }

  // Minecraft owns this borrowed client and its framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  private void captureCables() {
    Minecraft mc = Minecraft.getInstance();
    mc.options.hideGui = true;
    mc.options.fov().set(50);
    String view =
        switch (ticks) {
          case 160 -> "front";
          case 340 -> "oblique";
          case 520 -> "occlusion";
          default -> null;
        };
    if (view != null)
      Screenshot.grab(
          mc.gameDirectory,
          "cables-" + view + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }
}
