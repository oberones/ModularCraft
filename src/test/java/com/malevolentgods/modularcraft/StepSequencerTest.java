package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
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
  void sameSampleResumePlaysNextNoteAndRestIncludingAfterRecovery() {
    var settings =
        StepSequencerSettings.from(
            module().withValue(Parameter.STEP_1, 60).withValue(Parameter.STEP_2, 67));
    var seq = new StepSequencer(settings, 0);
    seq.start(0);
    advance(seq, 0);
    var copy = new StepSequencer(settings, seq.checkpoint(0));
    for (var runtime : List.of(seq, copy)) {
      runtime.stop(0);
      runtime.start(0);
      advance(runtime, 0);
      assertEquals(1, runtime.step());
      assertEquals(67, runtime.note(), "Same-sample resume must play the next note");
      assertTrue(runtime.gate(0));
      assertEquals(4800, runtime.checkpoint(0).gateEnd());
      runtime.stop(0);
      runtime.start(0);
      advance(runtime, 0);
      assertEquals(2, runtime.step());
      assertEquals(67, runtime.note(), "Rest retains pitch");
      assertFalse(runtime.gate(0));
    }
    assertEquals(seq.checkpoint(0), copy.checkpoint(0));
  }

  @Test
  void oneStepResumeRetriggersEvenWhenSampleAndStepAreUnchanged() {
    var settings =
        StepSequencerSettings.from(
            module().withValue(Parameter.SEQUENCE_LENGTH, 1).withValue(Parameter.STEP_1, 72));
    var seq = new StepSequencer(settings, 0);
    seq.start(0);
    advance(seq, 0);
    for (int i = 0; i < 3; i++) {
      seq.stop(0);
      assertFalse(seq.gate(0));
      seq.start(0);
      advance(seq, 0);
      assertEquals(0, seq.step());
      assertTrue(seq.gate(0), "One-step resume must retrigger");
      assertEquals(72, seq.note());
      seq.checkpoint(0);
    }
  }

  @Test
  void rejectedExternalClockDoesNotConsumePendingNoteEdit() {
    var module = module().withValue(Parameter.STEP_1, 60).withValue(Parameter.STEP_2, 62);
    var seq = new StepSequencer(StepSequencerSettings.from(module), 0);
    seq.connect(true, false, false, 0);
    seq.start(0);
    seq.inputs(true, false, 0);
    seq.configure(StepSequencerSettings.from(module.withValue(Parameter.STEP_2, 72)), 100);
    seq.inputs(false, false, 100);
    seq.inputs(true, false, 200);
    assertEquals(1, seq.rejectedClocks());
    assertEquals(0, seq.step());
    assertEquals(60, seq.note());
    assertTrue(seq.pending());
    assertEquals(4800, seq.checkpoint(200).gateEnd());
    seq.inputs(false, false, 300);
    seq.inputs(true, false, 1000);
    assertEquals(72, seq.note());
    assertFalse(seq.gate(1000));
    advance(seq, 1001);
    assertTrue(seq.gate(1001));
  }

  @Test
  void replacementRuntimeIsPrimedWithoutPhantomClockOrResetEdges() {
    var source = module().withValue(Parameter.STEP_1, 60);
    var follower =
        ModuleState.create(UUID.randomUUID(), source.owner(), ModuleKind.STEP_SEQUENCER, 1, 64, 0)
            .withValue(Parameter.STEP_1, 72);
    var a = new StepSequencer(StepSequencerSettings.from(source), 0);
    var b = new StepSequencer(StepSequencerSettings.from(follower), 0);
    var modules = List.of(source, follower);
    var cables =
        List.of(
            new Cable(source.id(), Port.CLOCK_OUT, follower.id(), Port.CLOCK_IN),
            new Cable(source.id(), Port.GATE_OUT, follower.id(), Port.RESET_IN));
    var original = timeline(modules, cables, Map.of(source.id(), a, follower.id(), b));
    original.rewire(null, 0);
    a.start(0);
    b.start(0);
    original.refresh(0);
    var replacement = new StepSequencer(StepSequencerSettings.from(follower), 0);
    var rebuilt = timeline(modules, cables, Map.of(source.id(), a, follower.id(), replacement));
    rebuilt.rewire(original, 0);
    assertTrue(replacement.external(), "Replacement must retain external-clock mode");
    replacement.start(0);
    rebuilt.refresh(0);
    assertEquals(-1, replacement.step(), "Already-high source must not manufacture an edge");
    assertFalse(replacement.gate(0));
    assertTrue(replacement.checkpoint(0).clock().resetHigh());
    var snapshot =
        new PatchSnapshot(
            source.id(),
            1,
            1,
            0,
            modules,
            cables,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(source.id(), a.checkpoint(0), follower.id(), replacement.checkpoint(0)));
    assertEquals(snapshot, StateCodec.decode(StateCodec.encode(snapshot)));
    rebuilt.advanceTo(6000, 1024);
    assertEquals(0, replacement.step());
    assertEquals(72, replacement.note());
  }

  @Test
  void retainedRuntimeKeepsPendingClockEdgeAcrossRebuild() {
    var source = module();
    var follower =
        ModuleState.create(UUID.randomUUID(), source.owner(), ModuleKind.STEP_SEQUENCER, 1, 64, 0)
            .withValue(Parameter.STEP_1, 72);
    var a = new StepSequencer(StepSequencerSettings.from(source), 0);
    var b = new StepSequencer(StepSequencerSettings.from(follower), 0);
    var modules = List.of(source, follower);
    var cables = List.of(new Cable(source.id(), Port.CLOCK_OUT, follower.id(), Port.CLOCK_IN));
    var runtimes = Map.of(source.id(), a, follower.id(), b);
    var original = timeline(modules, cables, runtimes);
    original.rewire(null, 0);
    b.start(0);
    a.start(0);
    advance(a, 0);
    var rebuilt = timeline(modules, cables, runtimes);
    rebuilt.rewire(original, 0);
    rebuilt.refresh(0);
    assertEquals(0, b.step(), "Retained runtime must still consume the pending rising edge");
    assertTrue(b.gate(0));
  }

  private static ControlTimeline timeline(
      List<ModuleState> modules, List<Cable> cables, Map<UUID, StepSequencer> runtimes) {
    return new ControlTimeline(
        modules, cables, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), runtimes, 0);
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
