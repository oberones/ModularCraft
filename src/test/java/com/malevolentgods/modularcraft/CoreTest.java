package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Filter;
import com.malevolentgods.modularcraft.dsp.Oscillator;
import com.malevolentgods.modularcraft.dsp.Output;
import com.malevolentgods.modularcraft.dsp.Smoother;
import com.malevolentgods.modularcraft.transport.Transport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CoreTest {
  @Test
  void nonFiniteAndRangeRejected() {
    for (Parameter p : Parameter.values()) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> p.validate(Double.NaN));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> p.validate(Double.POSITIVE_INFINITY));
      Assertions.assertThrows(IllegalArgumentException.class, () -> p.validate(p.min - 1));
      Assertions.assertThrows(IllegalArgumentException.class, () -> p.validate(p.max + 1));
      Assertions.assertEquals(p.initial, p.validate(p.initial));
    }
  }

  @Test
  void typedPorts() {
    Assertions.assertTrue(Port.AUDIO_OUT.connects(Port.AUDIO_IN));
    Assertions.assertTrue(Port.ENVELOPE_OUT.connects(Port.GAIN_IN));
    Assertions.assertFalse(Port.AUDIO_OUT.connects(Port.GAIN_IN));
    Assertions.assertFalse(Port.AUDIO_IN.connects(Port.AUDIO_OUT));
  }

  @Test
  void oscillatorTracksPitchForSixtySeconds() {
    for (double hz : new double[] {55, 220, 880, 3520}) {
      Oscillator oscillator = new Oscillator();
      int crossings = 0;
      double last = 0, peak = 0;
      for (int i = 0; i < 48000 * 60; i++) {
        double v = oscillator.next(hz, .5);
        if (last < 0 && v >= 0) crossings++;
        peak = Math.max(peak, Math.abs(v));
        last = v;
      }
      double measured = crossings / 60.0;
      Assertions.assertTrue(
          Math.abs(1200 * Math.log(measured / hz) / Math.log(2)) < 5, "pitch " + hz);
      Assertions.assertTrue(peak <= .5 && peak > .1);
    }
  }

  @Test
  void envelopeLinearAndSampleExact() {
    Envelope e = new Envelope();
    e.configure(1, 1, .3, 1, 0);
    e.gate(true, 0);
    for (int i = 1; i <= 4; i++) Assertions.assertEquals(i * .25, e.value(i * 12000L), 1e-12);
    for (int i = 1; i <= 4; i++)
      Assertions.assertEquals(1 - i * .175, e.value(48000 + i * 12000L), 1e-12);
    Assertions.assertEquals(Envelope.Stage.SUSTAIN, e.stage(96000));
    e.gate(false, 96000);
    for (int i = 1; i <= 4; i++)
      Assertions.assertEquals(.3 * (1 - i * .25), e.value(96000 + i * 12000L), 1e-12);
    Assertions.assertEquals(Envelope.Stage.IDLE, e.stage(144000));
  }

  @Test
  void retriggerAndInterruptedStagesHaveFullDuration() {
    Envelope e = new Envelope();
    e.configure(1, 1, .5, 1, 0);
    e.gate(true, 0);
    e.gate(false, 24000);
    Assertions.assertEquals(.5, e.value(24000), 1e-12);
    Assertions.assertEquals(.375, e.value(36000), 1e-12);
    e.gate(true, 36000);
    Assertions.assertEquals(.375, e.value(36000), 1e-12);
    Assertions.assertEquals(.53125, e.value(48000), 1e-12);
    Assertions.assertEquals(1, e.value(84000), 1e-12);
  }

  @Test
  void stageEditsApplyOnNextEntryAndCheckpointSeeks() {
    Envelope e = new Envelope();
    e.configure(1, 1, .7, 1, 0);
    e.gate(true, 0);
    e.configure(.01, .2, .7, .3, 24000);
    Assertions.assertEquals(.75, e.value(36000), 1e-12);
    Envelope late = new Envelope(e.checkpoint(36000));
    Assertions.assertEquals(e.value(50000), late.value(50000), 1e-12);
    Assertions.assertEquals(.7, late.value(96000), 1e-12);
  }

  @Test
  void repeatedEffectiveGateDoesNotRetrigger() {
    Envelope e = new Envelope();
    e.gate(true, 0);
    e.gate(true, 240);
    Assertions.assertEquals(1, e.value(480), 1e-12);
  }

  @Test
  void modulationAndResonanceExact() {
    Assertions.assertEquals(10250, Filter.cutoff(500, 1, .5));
    Assertions.assertEquals(500, Filter.cutoff(500, 1, 0));
    Assertions.assertEquals(.707, Filter.q(0), 1e-12);
    Assertions.assertEquals(10, Filter.q(95), 1e-12);
  }

  @Test
  void filterResonanceAndAttenuation() {
    double low = energy(4000, 500, 0), high = energy(4000, 8000, 0);
    Assertions.assertTrue(10 * Math.log10(low / high) < -12);
    Assertions.assertTrue(energy(1000, 1000, 75) > energy(1000, 1000, 0) * 2);
  }

  private static double energy(double hz, double cutoff, double resonance) {
    Filter f = new Filter();
    double e = 0;
    for (int i = 0; i < 96000; i++) {
      double v = f.next(Math.sin(2 * Math.PI * hz * i / 48000), cutoff, resonance);
      if (i > 48000) e += v * v;
    }
    return e;
  }

  @Test
  void ordinaryTransitionsAreTenMilliseconds() {
    Smoother s = new Smoother(0);
    s.target(1);
    double last = 0;
    for (int i = 0; i < 480; i++) {
      double v = s.next();
      Assertions.assertTrue(Math.abs(v - last) < .02);
      last = v;
    }
    Assertions.assertEquals(1, last, 1e-12);
  }

  @Test
  void outputIsFiniteProtectedAndDcBlocked() {
    Output o = new Output();
    double sum = 0;
    for (int i = 0; i < 48000; i++) {
      double v = o.next(.5);
      if (i > 24000) sum += v;
    }
    Assertions.assertTrue(Math.abs(sum / 24000) < .001);
    for (double v : new double[] {100, -100, Double.NaN, Double.POSITIVE_INFINITY})
      Assertions.assertTrue(Double.isFinite(o.next(v)) && Math.abs(o.next(v)) <= 1);
  }

  @Test
  void exactDistanceCurve() {
    Assertions.assertEquals(1, Output.distance(2));
    Assertions.assertEquals(.8, Output.distance(8), 1e-12);
    Assertions.assertEquals(16.0 / 30, Output.distance(16), 1e-12);
    Assertions.assertEquals(8.0 / 30, Output.distance(24), 1e-12);
    Assertions.assertEquals(0, Output.distance(32));
    Assertions.assertEquals(0, Output.distance(100));
  }

  @Test
  void transportHourAndPause() {
    AtomicLong clock = new AtomicLong(100);
    Transport t = new Transport(clock::get);
    clock.addAndGet(3_600_000_000_000L);
    Assertions.assertEquals(172800000, t.sample());
    t.paused(true);
    clock.addAndGet(2_000_000_000L);
    Assertions.assertEquals(172800000, t.sample());
    t.paused(false);
    clock.addAndGet(1_000_000_000L);
    Assertions.assertEquals(172848000, t.sample());
  }

  @Test
  void graphOrderAndCycles() {
    UUID owner = new UUID(0, 1);
    ModuleState a = module(2, owner, ModuleKind.FILTER, 0),
        b = module(1, owner, ModuleKind.FILTER, 2);
    Cable ab = new Cable(a.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN);
    Cable ba = new Cable(b.id(), Port.AUDIO_OUT, a.id(), Port.AUDIO_IN);
    Assertions.assertEquals(List.of(a, b), Graph.compile(List.of(b, a), List.of(ab)));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Graph.compile(List.of(a, b), List.of(ab, ba)));
  }

  @Test
  void authorityStaleAndUnloadPreserveComposition() {
    WorldState w = new WorldState();
    UUID owner = new UUID(0, 1), stranger = new UUID(0, 2);
    ModuleState a = module(1, owner, ModuleKind.OSCILLATOR, 0),
        b = module(2, owner, ModuleKind.SPEAKER, 2);
    w.place(a);
    w.place(b);
    Cable cable = new Cable(a.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN);
    w.connect(owner, false, cable, 0, 0);
    Assertions.assertEquals(List.of(cable), w.cables());
    long revision = w.get(a.id()).revision();
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> w.edit(stranger, false, a.id(), revision, Parameter.FREQUENCY, 440));
    w.edit(owner, false, a.id(), revision, Parameter.FREQUENCY, 440);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> w.edit(owner, false, a.id(), revision, Parameter.FREQUENCY, 880));
    w.enable(owner, false, b.id(), w.get(b.id()).revision(), true);
    w.loaded(a.id(), false);
    Assertions.assertFalse(w.get(b.id()).enabled());
    Assertions.assertEquals(List.of(cable), w.cables());
    w.remove(a.id());
    Assertions.assertTrue(w.cables().isEmpty());
  }

  @Test
  void occupiedInputAndDifferentOwnersAreRejectedAtomically() {
    WorldState w = new WorldState();
    UUID owner = new UUID(0, 1);
    ModuleState a = module(1, owner, ModuleKind.OSCILLATOR, 0),
        b = module(2, owner, ModuleKind.SPEAKER, 2);
    ModuleState c = module(3, new UUID(0, 2), ModuleKind.OSCILLATOR, 4);
    w.place(a);
    w.place(b);
    w.place(c);
    Cable cable = new Cable(a.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN);
    w.connect(owner, false, cable, 0, 0);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            w.connect(
                owner,
                true,
                new Cable(c.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN),
                0,
                w.get(b.id()).revision()));
    Assertions.assertEquals(List.of(cable), w.cables());
  }

  static ModuleState module(int id, UUID owner, ModuleKind kind, int x) {
    return ModuleState.create(new UUID(1, id), owner, kind, x, 0, 0);
  }
}
