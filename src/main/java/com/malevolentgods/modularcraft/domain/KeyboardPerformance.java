package com.malevolentgods.modularcraft.domain;

import java.util.UUID;

/** Server-thread-owned exclusive session lease. Clock values are monotonic nanoseconds. */
public final class KeyboardPerformance {
  private UUID holder;
  private long expires;
  private int semitone;
  private boolean absolute;
  private KeyboardState state;

  public KeyboardPerformance(int octave) {
    state = new KeyboardState(KeyboardState.note(octave, 0), false);
  }

  public KeyboardState state() {
    return state;
  }

  public UUID holder() {
    return holder;
  }

  public void play(UUID session, int key, int octave, long now) {
    play(session, key, false, octave, now);
  }

  /** Absolute MIDI pitches ignore the panel octave; relative GUI keys track it. */
  public void play(UUID session, int key, boolean absolute, int octave, long now) {
    if (holder != null && !holder.equals(session))
      throw new IllegalArgumentException("keyboard_busy");
    state = new KeyboardState(absolute ? key : KeyboardState.note(octave, key), true);
    this.absolute = absolute;
    semitone = key;
    holder = session;
    expires = now + 750_000_000L;
  }

  public void renew(UUID session, long now) {
    if (session.equals(holder)) expires = now + 750_000_000L;
  }

  public boolean release(UUID session) {
    if (!session.equals(holder)) return false;
    holder = null;
    state = new KeyboardState(state.note(), false);
    return true;
  }

  public boolean expire(long now) {
    return holder != null && now >= expires && release(holder);
  }

  public boolean transpose(int octave) {
    Parameter.OCTAVE.validate(octave);
    if (holder == null || absolute) return false;
    state = new KeyboardState(KeyboardState.note(octave, semitone), true);
    return true;
  }
}
