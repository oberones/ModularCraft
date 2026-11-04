package com.malevolentgods.modularcraft.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Game-thread input priority: 13 GUI sources and 16 channels of 128 MIDI notes. */
public final class KeyboardInput {
  public static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
  public static final int[] BLACK = {1, 3, 6, 8, 10};
  public static final int[] BOUNDARY = {1, 2, 4, 5, 6};
  private final Map<Integer, Integer> held = new LinkedHashMap<>();
  private final boolean[] down = new boolean[2048];
  private final boolean[] sustain = new boolean[16];

  /** Returns whether the active source changed or a MIDI note onset must retrigger. */
  public boolean midi(int command, int channel, int data1, int data2) {
    if (channel < 0 || channel > 15 || data1 < 0 || data1 > 127 || data2 < 0 || data2 > 127)
      throw new IllegalArgumentException("midi_message");
    int before = source();
    int slot = channel * 128 + data1;
    int source = 13 + slot;
    if (command == 0x90 && data2 > 0) {
      down[slot] = true;
      held.remove(source);
      held.put(source, data1);
      return true;
    }
    if (command == 0x80 || command == 0x90) {
      down[slot] = false;
      if (!sustain[channel]) held.remove(source);
    } else if (command == 0xb0) {
      if (data1 == 64) sustain[channel] = data2 >= 64;
      else if (data1 == 121 || data1 == 120) sustain[channel] = false;
      else if (data1 != 123) return false;
      for (int note = 0; note < 128; note++) {
        int index = channel * 128 + note;
        if (data1 == 120 || data1 == 123) down[index] = false;
        if (!down[index] && !sustain[channel]) held.remove(13 + index);
      }
    }
    return before != source();
  }

  public boolean absolute() {
    return source() >= 13;
  }

  public boolean clearMidi() {
    int before = source();
    held.keySet().removeIf(source -> source >= 13);
    Arrays.fill(down, false);
    Arrays.fill(sustain, false);
    return before != source();
  }

  public boolean press(int source, int note) {
    if (source < 0 || source > 12 || note < 0 || note > 11)
      throw new IllegalArgumentException("keyboard_input");
    if (held.containsKey(source) && held.get(source) == note) return false;
    held.remove(source);
    held.put(source, note);
    return true;
  }

  public boolean release(int source) {
    return held.remove(source) != null;
  }

  public void clear() {
    held.clear();
    Arrays.fill(down, false);
    Arrays.fill(sustain, false);
  }

  public int source() {
    return held.isEmpty() ? -1 : held.keySet().stream().reduce((a, b) -> b).orElse(-1);
  }

  public int note() {
    return held.isEmpty() ? -1 : held.values().stream().reduce((a, b) -> b).orElse(-1);
  }

  public boolean held(int note) {
    return held.entrySet().stream().anyMatch(e -> e.getKey() < 13 && e.getValue() == note);
  }

  /** Coordinates relative to piano bounds; black keys own their overlapping upper rectangles. */
  public static int hit(double x, double y, int width, int height) {
    if (x < 0 || y < 0 || x >= width || y >= height) return -1;
    double white = width / 7.0;
    if (y < height * .62)
      for (int i = 0; i < BLACK.length; i++)
        if (x >= (BOUNDARY[i] - .3) * white && x < (BOUNDARY[i] + .3) * white) return BLACK[i];
    return WHITE[Math.min(6, (int) (x / white))];
  }
}
