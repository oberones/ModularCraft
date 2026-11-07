package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.dsp.StepSequencer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepSequencerTest {
  @Test
  void separateMelodicModuleHasFivePorts() {
    assertEquals(5, ModuleKind.valueOf("STEP_SEQUENCER").ports.size());
  }

  static ModuleState module() {
    return ModuleState.create(
        UUID.randomUUID(), UUID.randomUUID(), ModuleKind.STEP_SEQUENCER, 0, 64, 0);
  }

  static void advance(StepSequencer seq, long target) {
    int count = 0;
    while (seq.nextEvent() <= target) {
      assertTrue(count++ < 10000, "Bounded advance");
      seq.advance(seq.nextEvent());
    }
  }

  @Test
  void notesRestsAndOverlapRetriggerAtExactSamples() {
    var m =
        module()
            .withValue(Parameter.STEP_1, 60)
            .withValue(Parameter.STEP_2, 64)
            .withValue(Parameter.GATE_TIME, 2000);
    var seq = new StepSequencer(StepSequencerSettings.from(m), 0);
    seq.start(0);
    advance(seq, 0);
    assertEquals(60, seq.note());
    assertTrue(seq.gate(0));
    advance(seq, 6000);
    assertEquals(64, seq.note());
    assertFalse(seq.gate(6000));
    advance(seq, 6001);
    assertTrue(seq.gate(6001));
    advance(seq, 12000);
    assertFalse(seq.gate(12000));
    assertEquals(64, seq.note());
    assertTrue(seq.clock(12000));
  }

  @Test
  void oneStepRepeatsAndLengthChangesPreserveHiddenNotes() {
    var m =
        module()
            .withValue(Parameter.STEP_16, 99)
            .withValue(Parameter.STEP_1, 60)
            .withValue(Parameter.SEQUENCE_LENGTH, 1);
    assertEquals(99, m.value(Parameter.STEP_16));
    assertEquals(m, StateCodec.moduleBytes(StateCodec.moduleBytes(m)).runtime(true, false));
    var seq = new StepSequencer(StepSequencerSettings.from(m), 0);
    seq.start(0);
    advance(seq, 4800);
    assertFalse(seq.gate(4800));
    advance(seq, 6000);
    assertTrue(seq.gate(6000));
    assertEquals(0, seq.step());
    seq.stop(6100);
    assertFalse(seq.gate(6100));
    seq.start(7000);
    advance(seq, 7000);
    assertEquals(0, seq.step());
    seq.reset(7100);
    assertEquals(-1, seq.step());
    assertFalse(seq.gate(7100));
  }

  @Test
  void liveSettingsAndCheckpointRecoveryAgree() {
    var m = module().withValue(Parameter.STEP_1, 60).withValue(Parameter.STEP_2, 67);
    var seq = new StepSequencer(StepSequencerSettings.from(m), 0);
    seq.start(0);
    advance(seq, 100);
    var changed = m.withValue(Parameter.STEP_2, 72).withValue(Parameter.TEMPO, 60);
    seq.configure(StepSequencerSettings.from(changed), 100);
    assertTrue(seq.pending());
    var snapshot =
        new PatchSnapshot(
            m.id(),
            1,
            1,
            100,
            List.of(changed),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(m.id(), seq.checkpoint(100)));
    var decoded = StateCodec.decode(StateCodec.encode(snapshot));
    assertEquals(snapshot, decoded);
    var copy = new StepSequencer(StepSequencerSettings.from(changed), decoded.steps().get(m.id()));
    advance(seq, 6000);
    advance(copy, 6000);
    assertEquals(72, seq.note());
    assertFalse(seq.pending());
    assertEquals(seq.checkpoint(6000), copy.checkpoint(6000));
  }

  @Test
  void externalResetClockAndRewiringAreDeterministic() {
    var m = module().withValue(Parameter.STEP_1, 60).withValue(Parameter.STEP_2, 62);
    var seq = new StepSequencer(StepSequencerSettings.from(m), 0);
    seq.connect(true, true, false, 0);
    seq.start(0);
    seq.inputs(true, false, 0);
    assertEquals(-1, seq.step());
    seq.inputs(false, false, 100);
    seq.inputs(true, false, 1000);
    assertEquals(0, seq.step());
    seq.inputs(false, false, 1100);
    seq.inputs(true, false, 1200);
    assertEquals(1, seq.rejectedClocks());
    seq.inputs(false, false, 1500);
    seq.inputs(true, true, 2000);
    assertEquals(0, seq.step());
    seq.connect(false, false, false, 2100);
    advance(seq, 8100);
    assertEquals(1, seq.step());
  }

  @Test
  void invalidValuesAndMalformedSnapshotsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> module().withValue(Parameter.STEP_1, 60.5));
    assertThrows(IllegalArgumentException.class, () -> module().withValue(Parameter.GATE_TIME, 0));
    assertThrows(
        IllegalArgumentException.class, () -> module().withValue(Parameter.SEQUENCE_LENGTH, 17));
    assertThrows(IllegalArgumentException.class, () -> module().withValue(Parameter.STEP_1, 128));
  }

  @Test
  void malformedGateCheckpointCannotSoundAnUnplayedStep() {
    var settings = StepSequencerSettings.from(module());
    var seq = new StepSequencer(settings, 0);
    seq.start(0);
    var clock = seq.checkpoint(0).clock();
    assertThrows(
        IllegalArgumentException.class,
        () -> new StepSequencer.Checkpoint(clock, settings, 60, 0, 480));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StepSequencer.Checkpoint(clock, settings, 60, 1, 0));
  }
}
