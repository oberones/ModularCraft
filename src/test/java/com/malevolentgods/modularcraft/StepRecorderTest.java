package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.StepRecorder;
import org.junit.jupiter.api.Test;

class StepRecorderTest {
  @Test
  void advancesOnlyAfterAcceptanceAndFinishesAtLength() {
    StepRecorder recorder = new StepRecorder();
    recorder.record(true);
    assertTrue(recorder.submit(1));
    assertFalse(recorder.submit(1));
    recorder.resolve(false, 1);
    assertEquals(0, recorder.selected());
    assertTrue(recorder.recording());
    assertTrue(recorder.submit(1));
    recorder.resolve(true, 1);
    assertEquals(0, recorder.selected());
    assertFalse(recorder.recording());
  }

  @Test
  void selectionIsIndependentAndHiddenStepsAreClamped() {
    StepRecorder recorder = new StepRecorder();
    recorder.select(15, 16);
    recorder.record(true);
    recorder.resize(4);
    assertEquals(3, recorder.selected());
    assertTrue(recorder.submit(4));
    recorder.select(0, 4);
    assertEquals(3, recorder.selected());
    recorder.resolve(true, 4);
    assertFalse(recorder.recording());
  }
}
