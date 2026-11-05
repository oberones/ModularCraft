package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.MixerEdits;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.dsp.Mixer;
import com.malevolentgods.modularcraft.dsp.MixerMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MixerRuntimeTest {
  @Test
  void gainRampsAndAdoptionAreContinuous() {
    var state = MixerTest.mixer();
    Mixer old = new Mixer(state);
    double[] signals = {1};
    int[] inputs = {0, 0, 0, 0};
    Assertions.assertEquals(1, old.next(signals, inputs));
    Mixer muted = new Mixer(state.withValue(MixerSettings.mute(4), 1));
    muted.adopt(old);
    double last = 1;
    for (int i = 0; i < 240; i++) {
      double value = muted.next(signals, inputs);
      Assertions.assertTrue(Math.abs(value - last) < .003);
      last = value;
    }
    Assertions.assertEquals(.5, last, 1e-12);
    Mixer replacement = new Mixer(state.withValue(MixerSettings.mute(0), 1));
    replacement.adopt(muted);
    Assertions.assertTrue(Math.abs(replacement.next(signals, inputs) - last) < .003);
    for (int i = 0; i < 480; i++) last = replacement.next(signals, inputs);
    Assertions.assertEquals(.75, last, 1e-12);
  }

  @Test
  void meterAttackDecayOverloadAndFreshness() {
    MixerMeter meter = new MixerMeter();
    double[] values = {1.5, .5, 0, 0, 2};
    meter.sample(values);
    meter.publish(100);
    var reading = meter.read(100);
    Assertions.assertNotNull(reading);
    Assertions.assertEquals(1.5, reading.peaks()[0]);
    Assertions.assertTrue(reading.overloads()[0]);
    Assertions.assertFalse(reading.overloads()[1]);
    values[0] = 0;
    values[1] = 0;
    values[4] = 0;
    for (int i = 0; i < 7200; i++) meter.sample(values);
    meter.publish(200);
    Assertions.assertEquals(.75, meter.read(200).peaks()[0], 1e-9);
    for (int i = 0; i < 7200; i++) meter.sample(values);
    meter.publish(300);
    Assertions.assertEquals(0, meter.read(300).peaks()[0], 1e-9);
    Assertions.assertTrue(meter.read(300).overloads()[0]);
    for (int i = 0; i < 33600; i++) meter.sample(values);
    meter.publish(400);
    Assertions.assertFalse(meter.read(400).overloads()[0]);
    Assertions.assertNull(meter.read(500_000_401));
    Assertions.assertNull(new MixerMeter().read(100));
  }

  @Test
  void liveEditsCoalesceThrottleAndWaitForAcknowledgment() {
    var state = MixerTest.mixer();
    MixerEdits edits = new MixerEdits(state, true);
    var level = MixerSettings.level(0);
    edits.offer(level, 40);
    var first = edits.poll(0);
    Assertions.assertEquals(40, first.value());
    edits.offer(level, 30);
    edits.offer(level, 25.125);
    Assertions.assertNull(edits.poll(100_000_000));
    edits.accept(state.withValue(level, 40), true, "ready");
    Assertions.assertEquals(25.125, edits.value(level));
    Assertions.assertNull(edits.poll(99_999_999));
    var last = edits.poll(100_000_000);
    Assertions.assertEquals(25.125, last.value());
    Assertions.assertEquals(1, last.state().revision());
    edits.accept(state.withValue(level, 40).withValue(level, 25.125), true, "ready");
    Assertions.assertNull(edits.poll(200_000_000));
  }

  @Test
  void rejectionCloseAndTimeoutCancelPendingWork() {
    var state = MixerTest.mixer();
    var level = MixerSettings.level(0);
    MixerEdits edits = new MixerEdits(state, true);
    edits.offer(level, 40);
    edits.poll(0);
    edits.offer(level, 20);
    edits.accept(state.withValue(level, 80), true, "stale");
    Assertions.assertEquals(80, edits.value(level));
    Assertions.assertNull(edits.poll(200_000_000));
    edits.offer(level, 30);
    edits.close();
    Assertions.assertNull(edits.poll(300_000_000));
    MixerEdits timeout = new MixerEdits(state, true);
    timeout.offer(level, 40);
    timeout.poll(0);
    timeout.offer(level, 20);
    Assertions.assertNull(timeout.poll(2_000_000_001L));
    Assertions.assertTrue(timeout.timedOut());
    Assertions.assertEquals(100, timeout.value(level));
    timeout.accept(state, false, "permission");
    timeout.offer(level, 90);
    Assertions.assertNull(timeout.poll(3_000_000_000L));
  }

  @Test
  void retiredGraphMetersAreNeverExposed() {
    var engine = new com.malevolentgods.modularcraft.dsp.AudioEngine();
    var graph =
        new com.malevolentgods.modularcraft.dsp.RenderGraph(
            java.util.List.of(MixerTest.patch(MixerTest.mixer(), 4)));
    float[] output = new float[256];
    var listener =
        new com.malevolentgods.modularcraft.dsp.RenderGraph.Listener(
            "minecraft:overworld", 2.5, .5, .5, 0, 1);
    Assertions.assertTrue(engine.submit(0, 1, graph));
    engine.render(0, listener, output);
    Assertions.assertNotNull(engine.meter(MixerTest.MIXER, System.nanoTime()));
    Assertions.assertTrue(
        engine.submit(
            128, 2, new com.malevolentgods.modularcraft.dsp.RenderGraph(java.util.List.of())));
    engine.render(128, listener, output);
    Assertions.assertNull(engine.meter(MixerTest.MIXER, System.nanoTime()));
  }

  @Test
  void meterHistorySurvivesParameterEdits() {
    MixerMeter old = new MixerMeter();
    old.sample(new double[] {2, 0, 0, 0, 0});
    MixerMeter next = new MixerMeter();
    next.adopt(old);
    next.sample(new double[5]);
    next.publish(1);
    Assertions.assertTrue(next.read(1).overloads()[0]);
    Assertions.assertEquals(2 - 2.0 / 14400, next.read(1).peaks()[0], 1e-12);
  }

  @Test
  void liveEditsAcceptNegativeMonotonicClockOrigins() {
    var state = MixerTest.mixer();
    var edits = new MixerEdits(state, true);
    edits.offer(MixerSettings.level(0), 50);
    Assertions.assertNotNull(edits.poll(-1_000_000_000L));
    edits.accept(state.withValue(MixerSettings.level(0), 50), true, "ready");
    edits.offer(MixerSettings.level(0), 25);
    Assertions.assertNull(edits.poll(-900_000_001L));
    Assertions.assertNotNull(edits.poll(-900_000_000L));
  }
}
