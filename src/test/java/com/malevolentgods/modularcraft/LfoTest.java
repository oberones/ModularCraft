package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.LfoSettings;
import com.malevolentgods.modularcraft.dsp.Lfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoTest {
  @Test
  void wavesAndPolarityHaveDefinedQuarterCycleValues() {
    double[][] expected = {
      {0, 1, 0, -1}, {-1, 0, 1, 0}, {-1, -.5, 0, .5}, {1, .5, 0, -.5}, {1, 1, -1, -1}
    };
    for (int wave = 0; wave < 5; wave++) {
      Lfo bipolar = new Lfo(new LfoSettings(1, wave, false, 2), 0);
      Lfo unipolar = new Lfo(new LfoSettings(1, wave, true, 2), 0);
      for (int quarter = 0; quarter < 4; quarter++) {
        Assertions.assertEquals(expected[wave][quarter], bipolar.value(12000L * quarter), 1e-10);
        Assertions.assertEquals(
            (expected[wave][quarter] + 1) / 2, unipolar.value(12000L * quarter), 1e-10);
      }
    }
  }

  @Test
  void everyDivisionLocksThenFreezesAndReacquires() {
    for (int division = 0; division < 7; division++) {
      Lfo lfo = new Lfo(new LfoSettings(1, 0, false, division), 0);
      lfo.connect(true, true, 0);
      Assertions.assertTrue(lfo.waiting(0));
      lfo.input(false, 480);
      lfo.input(true, 6000);
      lfo.input(false, 6480);
      lfo.input(true, 12000);
      int pulses = 1 << division;
      Assertions.assertEquals((1.5 / pulses) % 1, lfo.phase(15000), 1e-12);
      Assertions.assertFalse(lfo.waiting(15000));
      Assertions.assertTrue(lfo.waiting(24000));
      Assertions.assertEquals(lfo.phase(24000), lfo.phase(48000), 1e-12);
      lfo.input(false, 48000);
      lfo.input(true, 60000);
      Assertions.assertEquals(0, lfo.phase(60000), 1e-12);
      lfo.connect(false, false, 60000);
      Assertions.assertEquals(.25, lfo.phase(72000), 1e-12);
    }
  }

  @Test
  void synchronizedPhaseTracksAnHourOfFractionalSampleClockIntervals() {
    for (int division = 0; division < 7; division++) {
      Lfo lfo = new Lfo(new LfoSettings(1, 0, false, division), 0);
      lfo.connect(true, false, 0);
      for (long pulse = 0; pulse <= 123L * 4 * 60; pulse++) {
        long at = pulse * 720000 / 123;
        lfo.input(true, at);
        double expected = (double) (pulse % (1 << division)) / (1 << division);
        Assertions.assertEquals(expected, lfo.phase(at), 1e-12);
        lfo.input(false, at + 480);
      }
    }
  }

  @Test
  void analyticPhaseDoesNotDriftAfterAnHour() {
    for (double hz : new double[] {.01, .1, 1, 7, 20}) {
      Lfo lfo = new Lfo(new LfoSettings(hz, 0, false, 2), 0);
      Assertions.assertEquals(.25, lfo.phase(172800000L + Math.round(12000 / hz)), hz / 48000);
    }
  }
}
