package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
import com.malevolentgods.modularcraft.dsp.Envelope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoModulationTest {
  private final UUID owner = new UUID(0, 1);
  private final ModuleState source =
      ModuleState.create(new UUID(0, 2), owner, ModuleKind.LFO, 0, 64, 0)
          .withValue(Parameter.LFO_WAVE, 4)
          .withValue(Parameter.LFO_POLARITY, 1);
  private final ModuleState destination =
      ModuleState.create(new UUID(0, 3), owner, ModuleKind.ENVELOPE, 1, 64, 0);

  private ControlTimeline timeline(ModuleState envelope, Envelope runtime, Port port) {
    return new ControlTimeline(
        List.of(source, envelope),
        List.of(new Cable(source.id(), Port.CONTROL_OUT, envelope.id(), port)),
        Map.of(),
        Map.of(envelope.id(), runtime),
        Map.of(envelope.id(), true),
        Map.of(),
        0);
  }

  @Test
  void attackCapturesScaledDurationAndZeroDepthPreservesLegacy() {
    for (double depth : new double[] {0, 25, -25, 100, -100}) {
      Envelope runtime = new Envelope();
      ControlTimeline timeline =
          timeline(destination.withValue(Parameter.ATTACK_DEPTH, depth), runtime, Port.ATTACK_IN);
      timeline.refresh(0);
      long duration = Math.round(Math.clamp(.01 * Math.pow(2, 4 * depth / 100), .001, 10) * 48000);
      Assertions.assertEquals(duration, runtime.checkpoint(0).duration());
      timeline.advanceTo(duration / 2, 1024);
      Assertions.assertEquals(.5, runtime.value(duration / 2), 1e-8);
    }
  }

  @Test
  void decayCapturesCvAtItsOwnBoundary() {
    Envelope runtime = new Envelope();
    ControlTimeline timeline =
        timeline(destination.withValue(Parameter.DECAY_DEPTH, 25), runtime, Port.DECAY_IN);
    timeline.refresh(0);
    timeline.advanceTo(480, 1024);
    Assertions.assertEquals(Envelope.Stage.DECAY, runtime.stage(480));
    Assertions.assertEquals(19200, runtime.checkpoint(480).duration());
  }

  @Test
  void sustainTracksSignedCvAndRemainsContinuous() {
    Envelope runtime = new Envelope();
    ControlTimeline timeline =
        timeline(destination.withValue(Parameter.SUSTAIN_DEPTH, -50), runtime, Port.SUSTAIN_IN);
    timeline.refresh(0);
    timeline.advanceTo(12000, 1024);
    Assertions.assertEquals(.2, runtime.value(12000), 1e-8);
    timeline.advanceTo(24000, 1024);
    Assertions.assertEquals(.2, runtime.value(24000), 1e-8);
    timeline.advanceTo(24480, 1024);
    Assertions.assertEquals(.7, runtime.value(24480), 1e-8);
  }
}
