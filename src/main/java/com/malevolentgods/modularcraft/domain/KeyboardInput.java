package com.malevolentgods.modularcraft.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local physical inputs, bounded to twelve computer keys and one mouse pointer. */
public final class KeyboardInput {
  public static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
  public static final int[] BLACK = {1, 3, 6, 8, 10};
  public static final int[] BOUNDARY = {1, 2, 4, 5, 6};
  private final Map<Integer, Integer> held = new LinkedHashMap<>();

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
  }

  public int source() {
    return held.isEmpty() ? -1 : held.keySet().stream().reduce((a, b) -> b).orElse(-1);
  }

  public int note() {
    return held.isEmpty() ? -1 : held.values().stream().reduce((a, b) -> b).orElse(-1);
  }

  public boolean held(int note) {
    return held.containsValue(note);
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
