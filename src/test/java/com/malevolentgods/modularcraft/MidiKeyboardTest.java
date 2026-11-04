package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.KeyboardInput;
import com.malevolentgods.modularcraft.domain.KeyboardPerformance;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MidiKeyboardTest {
  @Test
  void midiAndGuiSharePriorityButNotOctaveOrPedal() {
    KeyboardInput input = new KeyboardInput();
    input.press(0, 0);
    Assertions.assertTrue(input.midi(0x90, 0, 69, 100));
    Assertions.assertEquals(69, input.note());
    Assertions.assertTrue(input.absolute());
    input.midi(0xb0, 0, 64, 64);
    Assertions.assertFalse(input.midi(0x80, 0, 69, 0));
    input.press(1, 2);
    Assertions.assertFalse(input.absolute());
    input.release(1);
    Assertions.assertEquals(69, input.note());
    Assertions.assertTrue(input.midi(0xb0, 0, 64, 63));
    Assertions.assertEquals(0, input.note());
    Assertions.assertFalse(input.absolute());
    input.release(0);
    Assertions.assertEquals(-1, input.note());
  }

  @Test
  void repeatedNotesRetriggerAndChannelsReleaseIndependently() {
    KeyboardInput input = new KeyboardInput();
    Assertions.assertTrue(input.midi(0x90, 0, 127, 1));
    Assertions.assertTrue(input.midi(0x90, 0, 127, 127));
    input.midi(0x90, 15, 0, 100);
    Assertions.assertFalse(input.midi(0x80, 0, 127, 0));
    Assertions.assertEquals(0, input.note());
    Assertions.assertTrue(input.midi(0x90, 15, 0, 0));
    Assertions.assertEquals(-1, input.note());
  }

  @Test
  void channelControllersPreserveOtherInputsAndResetSustain() {
    KeyboardInput input = new KeyboardInput();
    input.press(12, 3);
    input.midi(0x90, 1, 60, 100);
    input.midi(0xb0, 1, 64, 127);
    input.midi(0xb0, 1, 123, 0);
    Assertions.assertEquals(60, input.note());
    input.midi(0x90, 2, 70, 100);
    Assertions.assertTrue(input.midi(0xb0, 2, 120, 0));
    Assertions.assertEquals(60, input.note());
    Assertions.assertTrue(input.midi(0xb0, 1, 121, 0));
    Assertions.assertEquals(3, input.note());
    input.midi(0x90, 1, 61, 100);
    Assertions.assertFalse(input.midi(0xb0, 1, 121, 0));
    Assertions.assertEquals(61, input.note());
    Assertions.assertTrue(input.clearMidi());
    Assertions.assertEquals(3, input.note());
    input.midi(0x90, 1, 61, 100);
    input.midi(0x80, 1, 61, 0);
    Assertions.assertEquals(3, input.note());
  }

  @Test
  void fullMidiRangeIsTransientAndAbsolutePerformanceIgnoresTranspose() {
    KeyboardPerformance performance = new KeyboardPerformance(4);
    UUID session = UUID.randomUUID();
    for (int note : new int[] {0, 23, 69, 108, 127}) {
      performance.play(session, note, true, 4, 0);
      Assertions.assertEquals(new KeyboardState(note, true), performance.state());
      Assertions.assertFalse(performance.transpose(7));
      Assertions.assertEquals(note, performance.state().note());
    }
    performance.play(session, 9, false, 4, 0);
    Assertions.assertTrue(performance.transpose(5));
    Assertions.assertEquals(81, performance.state().note());
    Assertions.assertThrows(IllegalArgumentException.class, () -> new KeyboardState(-1, true));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new KeyboardState(128, true));
  }

  @Test
  void malformedMidiIsRejectedAndUnsupportedMessagesDoNotChangeHolds() {
    KeyboardInput input = new KeyboardInput();
    input.press(0, 0);
    Assertions.assertFalse(input.midi(0xe0, 0, 0, 127));
    Assertions.assertFalse(input.midi(0xb0, 0, 1, 127));
    Assertions.assertThrows(IllegalArgumentException.class, () -> input.midi(0x90, 16, 60, 1));
    Assertions.assertThrows(IllegalArgumentException.class, () -> input.midi(0x90, 0, 128, 1));
    Assertions.assertThrows(IllegalArgumentException.class, () -> input.midi(0x90, 0, 60, -1));
    Assertions.assertEquals(0, input.note());
  }
}
