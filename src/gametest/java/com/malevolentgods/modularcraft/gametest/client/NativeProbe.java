package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Observes the real native adapter without synthesizing input or claiming a listening test. */
@Mod(value = "modularcraft_tests", dist = Dist.CLIENT)
public final class NativeProbe {
  private int ticks;

  public NativeProbe() {
    if (!Boolean.getBoolean("modularcraft.qa")) return;
    NeoForge.EVENT_BUS.addListener(
        (ClientTickEvent.Post event) -> {
          if (Minecraft.getInstance().level == null) return;
          ticks++;
          if (Boolean.getBoolean("modularcraft.qa.cables")) captureCables();
          if (Boolean.getBoolean("modularcraft.qa.keyboard")) keyboard();
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
      double py = Math.max(4, (screen.height - 236) / 2) + 65;
      double white = (panel - 20) / 7.0;
      switch (ticks) {
        case 100, 320 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 120 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        case 140 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        case 160 -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        case 200 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 250 -> screen.mouseClicked(px + white, py + 10, 0);
        case 270 -> screen.mouseDragged(px + 1.5 * white, py + 70, 0, white / 2, 60);
        case 285 -> screen.mouseDragged(px - 2, py + 70, 0, -2 * white, 0);
        case 300 ->
            mc.setScreen(new com.malevolentgods.modularcraft.client.AudioSettingsScreen(screen));
        case 335 -> screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
        case 340 -> {
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, 0);
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
          screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
        }
        case 360 -> screen.onClose();
        default -> {}
      }
      if (ticks == 130
          || ticks == 150
          || ticks == 180
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
    if (ticks == 90 || ticks == 130 || ticks == 180 || ticks == 240 || ticks == 280 || ticks == 400)
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
