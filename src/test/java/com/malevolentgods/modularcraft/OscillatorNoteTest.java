package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.OscillatorNote;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OscillatorNoteTest {
  @Test
  void concertPitchAndEnharmonicNamesUseKeyboardTuning() {
    OscillatorNote note = new OscillatorNote(220);
    Assertions.assertEquals(9, note.semitone());
    Assertions.assertEquals(3, note.octave());
    Assertions.assertEquals(220, note.frequency(), 1e-12);
    note.select(9, 4);
    Assertions.assertEquals(440, note.frequency(), 1e-12);
    note.select(0, 4);
    Assertions.assertEquals(261.6255653005986, note.frequency(), 1e-12);
    note.select(1, 4);
    Assertions.assertEquals(277.1826309768721, note.frequency(), 1e-12);
    Assertions.assertEquals("C#/Db", OscillatorNote.name(1));
    Assertions.assertEquals("A#/Bb", OscillatorNote.name(10));
  }

  @Test
  void nearestSupportedNoteDoesNotQuantizeAcceptedFrequency() {
    OscillatorNote note = new OscillatorNote(432.1);
    Assertions.assertEquals(9, note.semitone());
    Assertions.assertEquals(4, note.octave());
    Assertions.assertFalse(note.dirty());
    note.accept(20, 1);
    Assertions.assertEquals(4, note.semitone());
    Assertions.assertEquals(0, note.octave());
    note.accept(8000, 2);
    Assertions.assertEquals(11, note.semitone());
    Assertions.assertEquals(8, note.octave());
  }

  @Test
  void selectorBoundsAndFrequencyBoundsAreSeparate() {
    OscillatorNote note = new OscillatorNote(220);
    for (int octave = 0; octave <= 8; octave++) {
      for (int semitone = 0; semitone < 12; semitone++) {
        note.select(semitone, octave);
        Assertions.assertEquals(octave != 0 || semitone >= 4, note.valid());
      }
    }
    Assertions.assertThrows(IllegalArgumentException.class, () -> note.select(-1, 4));
    Assertions.assertThrows(IllegalArgumentException.class, () -> note.select(12, 4));
    Assertions.assertThrows(IllegalArgumentException.class, () -> note.select(0, -1));
    Assertions.assertThrows(IllegalArgumentException.class, () -> note.select(0, 9));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new OscillatorNote(Double.NaN));
    note.select(0, 0);
    Assertions.assertThrows(IllegalArgumentException.class, () -> note.submit(1));
  }

  @Test
  void draftSurvivesUpdatesAndRejectionUntilMatchingAcknowledgement() {
    OscillatorNote note = new OscillatorNote(220);
    note.select(0, 4);
    note.accept(432.1, 4);
    Assertions.assertEquals(0, note.semitone());
    double exact = note.submit(4);
    Assertions.assertEquals(261.6255653005986, exact, 1e-12);
    note.accept(432.1, 4); // Rejection or an old view cannot clear the draft.
    Assertions.assertTrue(note.dirty());
    note.accept(exact, 5);
    Assertions.assertFalse(note.dirty());
    note.accept(440, 6);
    Assertions.assertEquals(9, note.semitone());
    Assertions.assertEquals(4, note.octave());
  }

  @Test
  void newerDraftSurvivesAcknowledgementOfEarlierSelection() {
    OscillatorNote note = new OscillatorNote(220);
    note.select(0, 4);
    double submitted = note.submit(1);
    note.select(2, 5);
    note.accept(submitted, 2);
    Assertions.assertTrue(note.dirty());
    Assertions.assertEquals(2, note.semitone());
    Assertions.assertEquals(5, note.octave());
    note.submit(2);
    note.accept(500, 3); // An unrelated accepted edit also must not discard the draft.
    Assertions.assertTrue(note.dirty());
  }

  @Test
  void applyingNearestNoteWaitsForAcceptanceEvenWithoutChangingSelectors() {
    OscillatorNote note = new OscillatorNote(432.123);
    Assertions.assertEquals(440, note.submit(7));
    Assertions.assertTrue(note.dirty());
    note.accept(432.123, 7);
    Assertions.assertTrue(note.dirty());
    note.accept(440, 8);
    Assertions.assertFalse(note.dirty());
  }
}
