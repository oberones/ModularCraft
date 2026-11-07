package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SequencerTimelineTest {
  private final UUID owner = new UUID(0, 1);
  private final ModuleState source =
      ModuleState.create(new UUID(1, 1), owner, ModuleKind.EUCLIDEAN_SEQUENCER, 0, 64, 0);
  private final ModuleState follower =
      ModuleState.create(new UUID(1, 2), owner, ModuleKind.EUCLIDEAN_SEQUENCER, 1, 64, 0);
  private final ModuleState envelope =
      ModuleState.create(new UUID(1, 3), owner, ModuleKind.ENVELOPE, 2, 64, 0);

  @Test
  void transportStopReleasesDownstreamGateAndResumeKeepsNextStep() {
    ModuleState controlled =
        source.withValue(Parameter.REDSTONE_MODE, 1).withValue(Parameter.A_HITS, 16);
    Sequencer seq = new Sequencer(SequencerSettings.from(controlled), 0);
    Envelope env = new Envelope();
    var timeline =
        new ControlTimeline(
            List.of(controlled, envelope),
            List.of(new Cable(source.id(), Port.TRIGGER_A, envelope.id(), Port.GATE_IN)),
            Map.of(source.id(), seq),
            Map.of(envelope.id(), env),
            Map.of(),
            Map.of(),
            0);
    seq.start(0);
    timeline.refresh(0);
    timeline.advanceTo(239, 1024);
    Assertions.assertTrue(env.checkpoint(239).gate());
    seq.stop(240);
    timeline.refresh(240);
    Assertions.assertFalse(env.checkpoint(240).gate());
    Assertions.assertEquals(Envelope.Stage.RELEASE, env.stage(240));
    Assertions.assertFalse(seq.output(4, 240));
    Assertions.assertEquals(1, seq.checkpoint(240).channels().getFirst().next());
    seq.start(1000);
    timeline.refresh(1000);
    Assertions.assertEquals(1, seq.step(0));
    Assertions.assertTrue(env.checkpoint(1000).gate());
    seq.reset(1100);
    timeline.refresh(1100);
    Assertions.assertFalse(env.checkpoint(1100).gate());
    timeline.advanceTo(7000, 1024);
    Assertions.assertEquals(0, seq.step(0));
    Assertions.assertTrue(env.checkpoint(7000).gate());
  }

  @Test
  void independentOutputsAndClockChainDriveEnvelopesBetweenTicks() {
    Sequencer a = new Sequencer(SequencerSettings.from(source), 0);
    Sequencer b = new Sequencer(SequencerSettings.from(follower), 0);
    Envelope env = new Envelope();
    var timeline =
        new ControlTimeline(
            List.of(source, follower, envelope),
            List.of(
                new Cable(source.id(), Port.CLOCK_OUT, follower.id(), Port.CLOCK_IN),
                new Cable(follower.id(), Port.TRIGGER_B, envelope.id(), Port.GATE_IN)),
            Map.of(source.id(), a, follower.id(), b),
            Map.of(envelope.id(), env),
            Map.of(),
            Map.of(),
            0);
    timeline.rewire(null, 0);
    a.start(0);
    b.start(0);
    timeline.refresh(0);
    Assertions.assertEquals(0, b.step(0));
    Assertions.assertEquals(1, env.value(480), 1e-9);
    timeline.advanceTo(2400, 1024);
    Assertions.assertEquals(Envelope.Stage.RELEASE, env.stage(2400));
    Assertions.assertFalse(env.checkpoint(2400).gate());
    timeline.advanceTo(42000, 1024);
    Assertions.assertEquals(7, b.step(0));
    Assertions.assertFalse(b.output(0, 42000) == b.output(1, 42000));
    Assertions.assertTrue(env.checkpoint(42000).gate());
  }

  @Test
  void boundedAdvanceStopsRatherThanReplayingAnUnboundedBacklog() {
    Sequencer seq =
        new Sequencer(SequencerSettings.from(source.withValue(Parameter.A_HITS, 16)), 0);
    var timeline =
        new ControlTimeline(
            List.of(source), List.of(), Map.of(source.id(), seq), Map.of(), Map.of(), Map.of(), 0);
    seq.start(0);
    timeline.refresh(0);
    timeline.advanceTo(48000L * 3600, 20);
    Assertions.assertTrue(timeline.overloaded());
    Assertions.assertFalse(seq.running());
    Assertions.assertFalse(seq.output(4, 48000L * 3600));
  }

  @Test
  void recoveryWithPendingEditsMatchesUninterruptedEnvelopeAndClock() {
    ModuleState edited = source.withValue(Parameter.TEMPO, 137).withValue(Parameter.A_STEPS, 7);
    Sequencer seq = new Sequencer(SequencerSettings.from(source), 0);
    Envelope env = new Envelope();
    var links = List.of(new Cable(source.id(), Port.TRIGGER_A, envelope.id(), Port.GATE_IN));
    var server =
        new ControlTimeline(
            List.of(source, envelope),
            links,
            Map.of(source.id(), seq),
            Map.of(envelope.id(), env),
            Map.of(),
            Map.of(),
            0);
    seq.start(0);
    server.refresh(0);
    server.advanceTo(12345, 1024);
    seq.configure(SequencerSettings.from(edited), 12345);
    Sequencer copy = new Sequencer(SequencerSettings.from(edited), seq.checkpoint(12345));
    Envelope copyEnv = new Envelope(env.checkpoint(12345));
    var client =
        new ControlTimeline(
            List.of(edited, envelope),
            links,
            Map.of(source.id(), copy),
            Map.of(envelope.id(), copyEnv),
            Map.of(),
            Map.of(),
            12345);
    for (long sample = 12346; sample < 300000; sample += 137) {
      server.advanceTo(sample, 1024);
      client.advanceTo(sample, 1024);
      Assertions.assertEquals(seq.checkpoint(sample), copy.checkpoint(sample));
      Assertions.assertEquals(env.value(sample), copyEnv.value(sample), 1e-12);
    }
  }

  @Test
  void manualGateKeepsPriorityOverPulseRetriggers() {
    Sequencer seq =
        new Sequencer(SequencerSettings.from(source.withValue(Parameter.A_HITS, 16)), 0);
    Envelope env = new Envelope();
    env.gate(true, 0);
    var timeline =
        new ControlTimeline(
            List.of(source, envelope),
            List.of(new Cable(source.id(), Port.TRIGGER_A, envelope.id(), Port.GATE_IN)),
            Map.of(source.id(), seq),
            Map.of(envelope.id(), env),
            Map.of(envelope.id(), true),
            Map.of(),
            0);
    seq.start(0);
    timeline.refresh(0);
    timeline.advanceTo(100000, 1024);
    Assertions.assertEquals(Envelope.Stage.SUSTAIN, env.stage(100000));
    Assertions.assertEquals(.7, env.value(100000), 1e-12);
    timeline.base(envelope.id(), false);
    timeline.refresh(100001);
    Assertions.assertEquals(Envelope.Stage.RELEASE, env.stage(100001));
  }
}
