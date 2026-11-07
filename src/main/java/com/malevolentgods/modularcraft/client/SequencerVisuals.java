package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.LfoSettings;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
import com.malevolentgods.modularcraft.dsp.Lfo;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import com.malevolentgods.modularcraft.dsp.StepSequencer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client/render-thread projection. No audio ownership or packets; snapshots bound its lifetime. */
public final class SequencerVisuals {
  public static final int[] COLORS = {0xff65d7e5, 0xffffc36a, 0xff91c79a, 0xffd69bbd};
  private static final Map<UUID, Projection> PATCHES = new HashMap<>();
  private static final Map<UUID, StepSequencer> STEPS = new HashMap<>();
  private static final Map<UUID, Lfo> LFOS = new HashMap<>();
  private static final Map<UUID, Sequencer> MODULES = new HashMap<>();
  private static final double[][] X = new double[17][16], Y = new double[17][16];

  static {
    for (int steps = 1; steps <= 16; steps++)
      for (int i = 0; i < steps; i++) {
        double angle = -Math.PI / 2 + i * 2 * Math.PI / steps;
        X[steps][i] = Math.cos(angle);
        Y[steps][i] = Math.sin(angle);
      }
  }

  public static double x(int steps, int step) {
    return X[steps][step];
  }

  public static double y(int steps, int step) {
    return Y[steps][step];
  }

  private static long advanced = -1;

  private static final class Projection {
    final Map<UUID, Sequencer> sequencers = new HashMap<>();
    final Map<UUID, StepSequencer> steps = new HashMap<>();
    final Map<UUID, Lfo> lfos = new HashMap<>();
    final ControlTimeline timeline;

    Projection(PatchSnapshot patch) {
      for (ModuleState m : patch.modules())
        if (patch.steps().containsKey(m.id()))
          steps.put(
              m.id(), new StepSequencer(StepSequencerSettings.from(m), patch.steps().get(m.id())));
      for (ModuleState module : patch.modules())
        if (patch.sequencers().containsKey(module.id()))
          sequencers.put(
              module.id(),
              new Sequencer(SequencerSettings.from(module), patch.sequencers().get(module.id())));
      for (ModuleState module : patch.modules())
        if (patch.lfos().containsKey(module.id()))
          lfos.put(module.id(), new Lfo(LfoSettings.from(module), patch.lfos().get(module.id())));
      timeline =
          new ControlTimeline(
              patch.modules(),
              patch.cables(),
              sequencers,
              Map.of(),
              Map.of(),
              patch.keyboards(),
              lfos,
              steps,
              patch.sample());
    }
  }

  private SequencerVisuals() {}

  public static void accept(Collection<PatchSnapshot> patches) {
    clear();
    for (PatchSnapshot patch : patches) {
      if (patch.sequencers().isEmpty() && patch.lfos().isEmpty() && patch.steps().isEmpty())
        continue;
      Projection projection = new Projection(patch);
      PATCHES.put(patch.id(), projection);
      MODULES.putAll(projection.sequencers);
      LFOS.putAll(projection.lfos);
      STEPS.putAll(projection.steps);
    }
  }

  public static void advance(long sample) {
    if (sample == advanced) return;
    for (Projection projection : PATCHES.values()) projection.timeline.advanceTo(sample, 1024);
    advanced = sample;
  }

  public static Sequencer get(UUID id) {
    return MODULES.get(id);
  }

  public static StepSequencer steps(UUID id) {
    return STEPS.get(id);
  }

  public static Lfo lfo(UUID id) {
    return LFOS.get(id);
  }

  public static void clear() {
    STEPS.clear();
    LFOS.clear();
    PATCHES.clear();
    MODULES.clear();
    advanced = -1;
  }
}
