package com.malevolentgods.modularcraft.transport;

/**
 * Single audio-thread owner, bounded preallocated insertion scheduler. Events prepared elsewhere.
 */
public final class EventQueue<T> {
  public record Event<T>(long sample, long sequence, T value) {}

  private final Object[] events = new Object[1024];
  private int size;

  public boolean offer(long sample, long sequence, T value) {
    return offer(new Event<>(sample, sequence, value));
  }

  public boolean offer(Event<T> event) {
    if (size == events.length || event.sample() < 0 || event.sequence() < 0) return false;
    int index = size;
    while (index > 0) {
      Event<T> previous = get(index - 1);
      if (previous.sample() < event.sample()
          || (previous.sample() == event.sample() && previous.sequence() <= event.sequence()))
        break;
      events[index] = previous;
      index--;
    }
    events[index] = event;
    size++;
    return true;
  }

  @SuppressWarnings("unchecked")
  private Event<T> get(int index) {
    return (Event<T>) events[index];
  }

  public Event<T> peek() {
    return size == 0 ? null : get(0);
  }

  public Event<T> poll(long sample) {
    if (size == 0 || get(0).sample() > sample) return null;
    Event<T> result = get(0);
    System.arraycopy(events, 1, events, 0, --size);
    events[size] = null;
    return result;
  }

  public int size() {
    return size;
  }

  public void clear() {
    java.util.Arrays.fill(events, null);
    size = 0;
  }
}
