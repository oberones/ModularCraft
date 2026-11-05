package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.LfoSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Lfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoRecoveryTest {
  @Test
  void checkpointRecoveryMatchesMidStageAndSustainRamps() {
    UUID owner = new UUID(0, 1), source = new UUID(0, 2), target = new UUID(0, 3);
    ModuleState lfoModule =
        ModuleState.create(source, owner, ModuleKind.LFO, 0, 0, 0)
            .withValue(Parameter.LFO_FREQUENCY, 7);
    ModuleState envelopeModule =
        ModuleState.create(target, owner, ModuleKind.ENVELOPE, 1, 0, 0)
            .withValue(Parameter.ATTACK_DEPTH, 60)
            .withValue(Parameter.DECAY_DEPTH, -30)
            .withValue(Parameter.SUSTAIN_DEPTH, 40)
            .withValue(Parameter.RELEASE_DEPTH, 75);
    List<ModuleState> modules = List.of(lfoModule, envelopeModule);
    List<Cable> cables =
        List.of(
            new Cable(source, Port.CONTROL_OUT, target, Port.ATTACK_IN),
            new Cable(source, Port.CONTROL_OUT, target, Port.DECAY_IN),
            new Cable(source, Port.CONTROL_OUT, target, Port.SUSTAIN_IN),
            new Cable(source, Port.CONTROL_OUT, target, Port.RELEASE_IN));
    for (long recovery : new long[] {200, 1000, 12345, 32001}) {
      Lfo lfo = new Lfo(LfoSettings.from(lfoModule), 0);
      Envelope envelope = new Envelope();
      ControlTimeline server =
          new ControlTimeline(
              modules,
              cables,
              Map.of(),
              Map.of(target, envelope),
              Map.of(target, true),
              Map.of(),
              Map.of(source, lfo),
              0);
      server.refresh(0);
      server.advanceTo(recovery, 1024);
      PatchSnapshot patch =
          new PatchSnapshot(
              source,
              0,
              1,
              recovery,
              modules,
              cables,
              Map.of(target, envelope.checkpoint(recovery)),
              Map.of(),
              Map.of(),
              Map.of(target, true),
              Map.of(source, lfo.checkpoint(recovery)));
      patch = StateCodec.decode(StateCodec.encode(patch));
      Lfo copyLfo = new Lfo(LfoSettings.from(lfoModule), patch.lfos().get(source));
      Envelope copyEnvelope = new Envelope(patch.envelopes().get(target));
      ControlTimeline client =
          new ControlTimeline(
              modules,
              cables,
              Map.of(),
              Map.of(target, copyEnvelope),
              patch.baseGates(),
              Map.of(),
              Map.of(source, copyLfo),
              recovery);
      for (long sample = recovery; sample < 60000; sample += 137) {
        server.advanceTo(sample, 1024);
        client.advanceTo(sample, 1024);
        Assertions.assertEquals(envelope.value(sample), copyEnvelope.value(sample), 1e-12);
        Assertions.assertEquals(lfo.value(sample), copyLfo.value(sample), 1e-12);
      }
      server.base(target, false);
      client.base(target, false);
      server.refresh(60000);
      client.refresh(60000);
      Assertions.assertEquals(envelope.checkpoint(60000), copyEnvelope.checkpoint(60000));
      server.retrigger(target, 60100);
      client.retrigger(target, 60100);
      Assertions.assertEquals(envelope.checkpoint(60100), copyEnvelope.checkpoint(60100));
    }
  }

  @Test
  void liveFrequencyAndShapeChangesPreservePhaseAndCheckpointTransition() {
    Lfo lfo = new Lfo(new LfoSettings(1, 0, false, 2), 0);
    double phase = lfo.phase(10000), value = lfo.value(10000);
    LfoSettings next = new LfoSettings(20, 4, true, 2);
    lfo.configure(next, 10000);
    Assertions.assertEquals(phase, lfo.phase(10000));
    Assertions.assertEquals(value, lfo.value(10000));
    Lfo copy = new Lfo(next, lfo.checkpoint(10200));
    for (long sample = 10200; sample < 20000; sample++)
      Assertions.assertEquals(lfo.value(sample), copy.value(sample), 1e-12);
  }

  @Test
  void fastEdgesAreIgnoredAndTempoChangesAnchorToPulseCount() {
    Lfo lfo = new Lfo(new LfoSettings(1, 0, false, 2), 0);
    lfo.connect(true, false, 0);
    lfo.input(true, 0);
    lfo.input(false, 100);
    lfo.input(true, 200);
    Assertions.assertTrue(lfo.waiting(200));
    lfo.input(false, 480);
    lfo.input(true, 6000);
    Assertions.assertEquals(.25, lfo.phase(6000));
    lfo.input(false, 6480);
    lfo.input(true, 9000);
    Assertions.assertEquals(.5, lfo.phase(9000));
    Assertions.assertEquals(.625, lfo.phase(10500));
  }

  @Test
  void releasingUnconnectedClockDoesNotResetPhaseOnOrdinaryRebuild() {
    UUID id = new UUID(0, 2);
    ModuleState module = ModuleState.create(id, new UUID(0, 1), ModuleKind.LFO, 0, 0, 0);
    Lfo lfo = new Lfo(LfoSettings.from(module), 0);
    var old =
        new ControlTimeline(
            List.of(module), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(id, lfo), 0);
    old.rewire(null, 0);
    double expected = lfo.phase(12345);
    var next =
        new ControlTimeline(
            List.of(module.withValue(Parameter.LFO_WAVE, 1)),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(id, lfo),
            12345);
    next.rewire(old, 12345);
    Assertions.assertEquals(expected, lfo.phase(12345));
  }

  @Test
  void mergingComponentsKeepsExistingClockLock() {
    UUID owner = new UUID(0, 1), id = new UUID(0, 2);
    ModuleState lfoModule = ModuleState.create(id, owner, ModuleKind.LFO, 0, 0, 0);
    ModuleState key = ModuleState.create(new UUID(0, 3), owner, ModuleKind.KEYBOARD, 1, 0, 0);
    List<ModuleState> modules = List.of(lfoModule, key);
    List<Cable> cables = List.of(new Cable(key.id(), Port.GATE_OUT, id, Port.CLOCK_IN));
    Lfo lfo = new Lfo(LfoSettings.from(lfoModule), 0);
    var before =
        new ControlTimeline(
            modules, cables, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(id, lfo), 0);
    before.rewire(null, 0);
    lfo.input(true, 0);
    lfo.input(false, 480);
    lfo.input(true, 6000);
    var checkpoint = lfo.checkpoint(6500);
    var unrelated =
        new ControlTimeline(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), 6500);
    var after =
        new ControlTimeline(
            modules, cables, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(id, lfo), 6500);
    after.rewireAll(List.of(unrelated, before), 6500);
    Assertions.assertEquals(checkpoint, lfo.checkpoint(6500));
  }

  @Test
  void hostileCheckpointNumbersAndSettingsAreRejected() {
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 1}) {
      Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> new Lfo.Checkpoint(0, 0, invalid, 0, 0, false, false, -1, 0, 0, -480, 0));
    }
    Assertions.assertThrows(IllegalArgumentException.class, () -> new LfoSettings(0, 0, false, 2));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new LfoSettings(1, 5, false, 2));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new LfoSettings(1, 0, false, 7));
  }
}
