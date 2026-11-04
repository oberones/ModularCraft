package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SequencerTest {
  private static ModuleState module() {
    return ModuleState.create(
        UUID.randomUUID(), UUID.randomUUID(), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 64, 0);
  }

  @Test
  void everyPatternHasEvenSpacingAndExactRotation() {
    for (int steps = 1; steps <= 16; steps++) {
      for (int hits = 0; hits <= steps; hits++) {
        var base = new SequencerSettings.Channel(steps, hits, 0, false);
        for (int rotation = 0; rotation < steps; rotation++) {
          var pattern = new SequencerSettings.Channel(steps, hits, rotation, false);
          int count = 0, first = -1, previous = -1, min = 17, max = 0;
          for (int step = 0; step < steps; step++) {
            assertEquals(base.hit(Math.floorMod(step - rotation, steps)), pattern.hit(step));
            if (pattern.hit(step)) {
              count++;
              if (first < 0) first = step;
              if (previous >= 0) {
                min = Math.min(min, step - previous);
                max = Math.max(max, step - previous);
              }
              previous = step;
            }
          }
          assertEquals(hits, count);
          if (hits > 0) {
            min = Math.min(min, steps + first - previous);
            max = Math.max(max, steps + first - previous);
            assertTrue(max - min <= 1);
          }
        }
      }
    }
    var three = new SequencerSettings.Channel(8, 3, 0, false);
    StringBuilder mask = new StringBuilder();
    for (int i = 0; i < 8; i++) mask.append(three.hit(i) ? '1' : '0');
    assertEquals("10010010", mask.toString());
  }

  @Test
  void editsNormalizeDependentControlsButRecordsRejectInvalidState() {
    ModuleState original =
        module().withValue(Parameter.A_HITS, 15).withValue(Parameter.A_ROTATION, 15);
    ModuleState shorter = original.withValue(Parameter.A_STEPS, 4);
    assertEquals(4, shorter.value(Parameter.A_HITS));
    assertEquals(3, shorter.value(Parameter.A_ROTATION));
    assertThrows(IllegalArgumentException.class, () -> shorter.withValue(Parameter.A_HITS, 5));
    assertThrows(IllegalArgumentException.class, () -> Parameter.TEMPO.validate(120.5));
    assertThrows(
        IllegalArgumentException.class, () -> new SequencerSettings.Channel(4, 5, 0, false));
  }

  @Test
  void pulseStopResumeAndResetHaveExactSampleBoundaries() {
    Sequencer seq = new Sequencer(SequencerSettings.from(module()), 0);
    seq.start(0);
    seq.advance(0);
    assertTrue(seq.output(0, 0));
    assertTrue(seq.output(4, 479));
    seq.advance(480);
    assertFalse(seq.output(0, 480));
    assertEquals(6000, seq.nextEvent());
    seq.stop(1000);
    assertFalse(seq.output(4, 1000));
    seq.start(2000);
    seq.advance(2000);
    assertEquals(1, seq.step(0));
    seq.reset(2100);
    assertEquals(-1, seq.step(0));
    seq.advance(8000);
    assertEquals(0, seq.step(0));
    assertTrue(seq.output(0, 8000));
  }

  @Test
  void settingsWaitForClockWhileMuteReleasesImmediately() {
    ModuleState module = module();
    Sequencer seq = new Sequencer(SequencerSettings.from(module), 0);
    seq.start(0);
    seq.advance(0);
    module = module.withValue(Parameter.A_HITS, 16);
    seq.configure(SequencerSettings.from(module), 100);
    assertEquals(4, seq.active().channels().getFirst().hits());
    seq.configure(SequencerSettings.from(module.withValue(Parameter.A_MUTE, 1)), 200);
    assertFalse(seq.output(0, 200));
    seq.advance(480);
    seq.advance(6000);
    assertEquals(16, seq.active().channels().getFirst().hits());
    assertEquals(1, seq.step(0));
  }

  @Test
  void externalClockPrimesHighRejectsFastEdgesAndResetsBeforeClock() {
    Sequencer seq = new Sequencer(SequencerSettings.from(module()), 0);
    seq.connect(true, true, false, 0);
    seq.start(0);
    seq.inputs(true, false, 0);
    assertEquals(-1, seq.step(0));
    seq.inputs(false, false, 100);
    seq.inputs(true, false, 1000);
    assertEquals(0, seq.step(0));
    seq.inputs(false, false, 1100);
    seq.inputs(true, false, 1200);
    assertEquals(0, seq.step(0));
    assertEquals(1, seq.rejectedClocks());
    seq.inputs(false, false, 2000);
    seq.inputs(true, true, 3000);
    assertEquals(0, seq.step(0));
    seq.advance(3480);
    seq.connect(false, false, false, 4000);
    assertEquals(10000, seq.nextEvent());
  }

  @Test
  void integerClockAndCheckpointRemainExactForAnHour() {
    var settings = SequencerSettings.from(module().withValue(Parameter.TEMPO, 137));
    Sequencer seq = new Sequencer(settings, 0);
    seq.start(0);
    long end = 48000L * 3600, clocks = 0;
    while (seq.nextEvent() <= end) {
      long at = seq.nextEvent();
      seq.advance(at);
      if (seq.output(4, at)) {
        assertTrue(Math.abs(at - clocks * 720000.0 / 137) < 1.01);
        clocks++;
      }
    }
    Sequencer restored = new Sequencer(settings, seq.checkpoint(end));
    assertEquals(seq.nextEvent(), restored.nextEvent());
    assertEquals(seq.step(0), restored.step(0));
  }
}
