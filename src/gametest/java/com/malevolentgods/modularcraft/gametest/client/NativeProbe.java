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
