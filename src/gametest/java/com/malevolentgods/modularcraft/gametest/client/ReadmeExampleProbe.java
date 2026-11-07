package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.KeyboardScreen;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

/** Checks the documented recipes and real keyboard interaction before capturing native images. */
public final class ReadmeExampleProbe {
  private ReadmeExampleProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    String example = System.getProperty("modularcraft.qa.readmeExample");
    Minecraft mc = Minecraft.getInstance();
    if (tick == 100 || tick == 330) verify(example);
    if (example.equals("lead") && tick >= 110 && tick <= 220) keyboard(tick);
    if (tick == 300) {
      if (mc.screen != null) mc.screen.onClose();
      mc.options.hideGui = true;
      mc.options.fov().set(35);
    }
    if (tick == 350) {
      if (mc.screen != null || !mc.options.hideGui || mc.options.fov().get() != 35)
        throw new IllegalStateException("README capture has a visible overlay");
      Screenshot.grab(
          mc.gameDirectory,
          filename(example),
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
      System.out.println(
          "MODULARCRAFT_QA readme "
              + example
              + " framebuffer="
              + mc.getMainRenderTarget().width
              + "x"
              + mc.getMainRenderTarget().height);
    }
    if (tick == 400
        && !Files.isRegularFile(
            mc.gameDirectory.toPath().resolve("screenshots").resolve(filename(example))))
      throw new IllegalStateException("README screenshot write did not complete");
    if (tick == 400) System.out.println("MODULARCRAFT_QA readme " + example + " screenshot PASS");
  }

  private static String filename(String example) {
    return switch (example) {
      case "drone" -> "example-drone.png";
      case "lead" -> "example-keyboard-lead.png";
      case "rhythms" -> "example-four-voice-rhythms.png";
      default -> throw new IllegalArgumentException("Unknown README example: " + example);
    };
  }

  private static PatchSnapshot patch() {
    if (ClientRuntime.patches().size() != 1)
      throw new IllegalStateException("Expected exactly one README patch");
    return ClientRuntime.patches().iterator().next();
  }

  private static ModuleState one(PatchSnapshot patch, ModuleKind kind) {
    var matches = patch.modules().stream().filter(m -> m.kind() == kind).toList();
    if (matches.size() != 1) throw new IllegalStateException("Expected one " + kind);
    return matches.getFirst();
  }

  private static void verify(String example) {
    filename(example);
    var patch = patch();
    boolean rhythms = example.equals("rhythms"), lead = example.equals("lead");
    int modules = rhythms ? 15 : lead ? 5 : 4;
    int cables = rhythms ? 17 : lead ? 5 : 3;
    if (patch.modules().size() != modules || patch.cables().size() != cables)
      throw new IllegalStateException(
          "README "
              + example
              + " expected "
              + modules
              + " modules / "
              + cables
              + " cables, got "
              + patch.modules().size()
              + " / "
              + patch.cables().size());
    var speaker = one(patch, ModuleKind.SPEAKER);
    if (!speaker.enabled()) throw new IllegalStateException("README Speaker is disabled");
    List<Cable> expected = new ArrayList<>();
    var sources =
        patch.modules().stream()
            .filter(m -> m.kind().isOscillator())
            .sorted(Comparator.comparingInt(ModuleState::z))
            .toList();
    if (sources.size() != (rhythms ? 4 : 1)) throw new IllegalStateException("Wrong voice count");
    for (int i = 0; i < sources.size(); i++) {
      var source = sources.get(i);
      ModuleKind kind =
          lead || (rhythms && i % 2 == 1) ? ModuleKind.SQUARE_OSCILLATOR : ModuleKind.OSCILLATOR;
      if (source.kind() != kind) throw new IllegalStateException("Wrong oscillator kind");
      var filter =
          patch.modules().stream()
              .filter(m -> m.kind() == ModuleKind.FILTER && m.z() == source.z())
              .findFirst()
              .orElseThrow();
      var amp =
          patch.modules().stream()
              .filter(m -> m.kind() == ModuleKind.AMP && m.z() == source.z())
              .findFirst()
              .orElseThrow();
      expected.add(new Cable(source.id(), Port.AUDIO_OUT, filter.id(), Port.AUDIO_IN));
      expected.add(new Cable(filter.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN));
      if (rhythms) {
        var mixer = one(patch, ModuleKind.MIXER);
        var sequencer = one(patch, ModuleKind.EUCLIDEAN_SEQUENCER);
        expected.add(new Cable(amp.id(), Port.AUDIO_OUT, mixer.id(), MixerSettings.INPUTS.get(i)));
        expected.add(
            new Cable(
                sequencer.id(),
                List.of(Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D).get(i),
                amp.id(),
                Port.GATE_IN));
      } else {
        expected.add(new Cable(amp.id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
        if (lead) {
          var keyboard = one(patch, ModuleKind.KEYBOARD);
          expected.add(new Cable(keyboard.id(), Port.CV_OUT, source.id(), Port.PITCH_IN));
          expected.add(new Cable(keyboard.id(), Port.GATE_OUT, amp.id(), Port.GATE_IN));
        }
      }
      if (source.value(Parameter.FREQUENCY) != (rhythms ? 110 * (i + 1) : 220))
        throw new IllegalStateException("Wrong oscillator frequency");
    }
    if (rhythms) {
      expected.add(
          new Cable(
              one(patch, ModuleKind.MIXER).id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
      if (!patch.sequencers().get(one(patch, ModuleKind.EUCLIDEAN_SEQUENCER).id()).running())
        throw new IllegalStateException("README sequencer is stopped");
    }
    if (!new HashSet<>(expected).equals(new HashSet<>(patch.cables())))
      throw new IllegalStateException("README wiring differs from documented recipe");
    for (var module : patch.modules()) {
      for (var entry : module.kind().defaults().entrySet()) {
        Parameter parameter = entry.getKey();
        double value = entry.getValue();
        if (parameter == Parameter.FREQUENCY) continue;
        if (parameter == Parameter.CUTOFF) value = rhythms ? 5000 : lead ? 2000 : 1000;
        if (parameter == Parameter.RESONANCE) value = 25;
        if (parameter == Parameter.DRONE) value = example.equals("drone") ? 1 : 0;
        if (rhythms && parameter == Parameter.ATTACK) value = .001;
        if (rhythms && parameter == Parameter.RELEASE) value = .080;
        if (module.value(parameter) != value)
          throw new IllegalStateException("README unexpected " + module.kind() + " " + parameter);
      }
    }
    System.out.println(
        "MODULARCRAFT_QA readme "
            + example
            + " recipe PASS modules="
            + modules
            + " cables="
            + cables);
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void keyboard(int tick) {
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.screen instanceof KeyboardScreen screen))
      throw new IllegalStateException("README keyboard panel did not open");
    if (tick == 110) screen.keyPressed(GLFW.GLFW_KEY_A, 0, 0);
    if (tick == 140) screen.keyPressed(GLFW.GLFW_KEY_S, 0, 0);
    if (tick == 170) screen.keyReleased(GLFW.GLFW_KEY_S, 0, 0);
    if (tick == 200) screen.keyReleased(GLFW.GLFW_KEY_A, 0, 0);
    if (tick == 130 || tick == 160 || tick == 190 || tick == 220) {
      var patch = patch();
      var keyboard = patch.keyboards().get(one(patch, ModuleKind.KEYBOARD).id());
      int note = tick == 160 ? 62 : 60;
      boolean held = tick != 220;
      if (keyboard.note() != note
          || keyboard.gate() != held
          || patch.envelopes().get(one(patch, ModuleKind.AMP).id()).gate() != held)
        throw new IllegalStateException("README keyboard pitch/gate/release mismatch at " + tick);
      System.out.println(
          "MODULARCRAFT_QA readme lead keyboard PASS note=" + note + " gate=" + held);
    }
  }
}
