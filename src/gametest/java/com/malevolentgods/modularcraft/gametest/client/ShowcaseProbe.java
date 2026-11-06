package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.game.ModuleBlock;
import java.nio.file.Files;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

/** Verifies the accepted showcase before taking documentation screenshots. */
public final class ShowcaseProbe {
  private static final EnumSet<ModuleKind> CAPTURED = EnumSet.noneOf(ModuleKind.class);
  private static BlockPos stage;
  private static ModuleKind pending;
  private static int stableTicks;

  private ShowcaseProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    Minecraft mc = Minecraft.getInstance();
    mc.options.hideGui = true;
    mc.options.fov().set(tick < 180 ? 30 : 35);
    if (tick == 100) verify();
    if (tick == 140) capture("synth-setup");
    if (tick >= 200
        && stage != null
        && mc.level.getBlockState(stage).getBlock() instanceof ModuleBlock block
        && mc.player.distanceToSqr(stage.getCenter()) < 25) {
      ModuleKind kind = block.kind;
      if (kind != pending) {
        pending = kind;
        stableTicks = 0;
      }
      if (++stableTicks == 20 && CAPTURED.add(kind)) capture(filename(kind));
    }
    if (tick == 1100) {
      if (CAPTURED.size() != 9)
        throw new IllegalStateException("Missing individual showcase captures");
      for (ModuleKind kind : ModuleKind.values())
        if (!Files.isRegularFile(
            mc.gameDirectory.toPath().resolve("screenshots/" + filename(kind) + ".png")))
          throw new IllegalStateException("Screenshot write did not complete: " + kind);
      if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/synth-setup.png")))
        throw new IllegalStateException("Missing synth screenshot");
      System.out.println("MODULARCRAFT_QA showcase screenshots=10 PASS");
    }
  }

  private static void verify() {
    var patch =
        ClientRuntime.patches().stream()
            .filter(p -> p.modules().size() == 9 && p.cables().size() == 10)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Showcase needs one nine-module/ten-cable patch"));
    if (patch.modules().stream().map(m -> m.kind()).distinct().count() != 9
        || patch.modules().stream().noneMatch(m -> m.kind() == ModuleKind.SPEAKER && m.enabled())
        || patch.sequencers().values().stream().noneMatch(s -> s.running()))
      throw new IllegalStateException(
          "Showcase needs all nine kinds, enabled speaker and running sequencer");
    var saw =
        patch.modules().stream()
            .filter(m -> m.kind() == ModuleKind.OSCILLATOR)
            .findFirst()
            .orElseThrow();
    stage = new BlockPos(saw.x() + 8, saw.y() + 1, saw.z() - 6);
    System.out.println(
        "MODULARCRAFT_QA showcase accepted modules=9 cables=10 speaker=enabled sequencer=running"
            + " PASS");
  }

  private static String filename(ModuleKind kind) {
    return switch (kind) {
      case OSCILLATOR -> "saw-oscillator";
      case SQUARE_OSCILLATOR -> "square-oscillator";
      case FILTER -> "low-pass-filter";
      case ENVELOPE -> "envelope-generator";
      case SPEAKER -> "amplifier-speaker";
      case KEYBOARD -> "keyboard";
      case EUCLIDEAN_SEQUENCER -> "euclidean-sequencer";
      case LFO -> "lfo";
      case MIXER -> "audio-mixer";
    };
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void capture(String name) {
    Minecraft mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        name + ".png",
        mc.getMainRenderTarget(),
        message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }
}
