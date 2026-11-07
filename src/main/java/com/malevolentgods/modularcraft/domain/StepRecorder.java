package com.malevolentgods.modularcraft.domain;

/** Editor cursor independent of playback, with one acknowledged write at a time. */
public final class StepRecorder {
  private int selected;
  private boolean recording, pending;

  public int selected() {
    return selected;
  }

  public boolean recording() {
    return recording;
  }

  public boolean pending() {
    return pending;
  }

  public void record(boolean value) {
    recording = value;
  }

  public void select(int step, int length) {
    if (!pending && step >= 0 && step < length) selected = step;
  }

  public void resize(int length) {
    selected = Math.min(selected, length - 1);
  }

  public boolean submit(int length) {
    if (!recording || pending || selected >= length) return false;
    pending = true;
    return true;
  }

  public void resolve(boolean accepted, int length) {
    if (pending && accepted) {
      if (selected + 1 >= length) recording = false;
      else selected++;
    }
    pending = false;
    resize(length);
  }
}
