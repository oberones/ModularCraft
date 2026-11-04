package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.SequencerSettings;
import java.util.List;

/** Single-owner 48 kHz clock. Advance only at nextEvent; configuration is prepared off audio. */
public final class Sequencer {
  public static final int PULSE = 480, MIN_CLOCK = 960;

  public record ChannelState(int next, int step, long end) {
    public ChannelState {
      if (next < 0 || next > 15 || step < -1 || step > 15 || end < 0)
        throw new IllegalArgumentException("sequencer_checkpoint");
    }
  }

  public record Checkpoint(
      long sample,
      SequencerSettings active,
      boolean running,
      boolean external,
      boolean clockHigh,
      boolean resetHigh,
      long nextClock,
      int remainder,
      long lastClock,
      long clockEnd,
      int rejectedClocks,
      List<ChannelState> channels) {
    public Checkpoint {
      channels = List.copyOf(channels);
      if (sample < 0
          || sample > Long.MAX_VALUE - 1000000
          || active == null
          || channels.size() != 4
          || remainder < 0
          || remainder >= active.tempo()
          || lastClock < -MIN_CLOCK
          || lastClock > sample
          || clockEnd < 0
          || clockEnd > sample + PULSE
          || rejectedClocks < 0
          || (running && !external
              ? nextClock < sample || nextClock > sample + 24000
              : nextClock != Long.MAX_VALUE))
        throw new IllegalArgumentException("sequencer_checkpoint");
      for (int c = 0; c < 4; c++) {
        ChannelState channel = channels.get(c);
        if (channel.next() >= active.channels().get(c).steps()
            || channel.step() >= active.channels().get(c).steps()
            || channel.end() > sample + PULSE
            || (!running && channel.end() > sample))
          throw new IllegalArgumentException("sequencer_checkpoint");
      }
      if (!running && clockEnd > sample) throw new IllegalArgumentException("sequencer_checkpoint");
    }
  }

  private SequencerSettings active, requested;
  private boolean running, external, clockHigh, resetHigh;
  private long nextClock = Long.MAX_VALUE, lastClock = -MIN_CLOCK, clockEnd;
  private int remainder, rejectedClocks;
  private long transitions;
  private final int[] next = new int[4], step = {-1, -1, -1, -1};
  private final long[] ends = new long[4];

  public Sequencer(SequencerSettings settings, long sample) {
    active = settings;
    requested = settings;
    if (sample < 0) throw new IllegalArgumentException("negative_sample");
  }

  public Sequencer(SequencerSettings settings, Checkpoint state) {
    this(settings, state.sample());
    active = state.active();
    running = state.running();
    external = state.external();
    clockHigh = state.clockHigh();
    resetHigh = state.resetHigh();
    nextClock = state.nextClock();
    remainder = state.remainder();
    lastClock = state.lastClock();
    clockEnd = state.clockEnd();
    rejectedClocks = state.rejectedClocks();
    for (int c = 0; c < 4; c++) {
      next[c] = state.channels().get(c).next();
      step[c] = state.channels().get(c).step();
      ends[c] = state.channels().get(c).end();
    }
  }

  public void configure(SequencerSettings settings, long sample) {
    requested = settings;
    if (!running) apply();
    for (int c = 0; c < 4; c++) if (requested.channels().get(c).muted()) ends[c] = 0;
  }

  private void apply() {
    if (active.tempo() != requested.tempo()) remainder = 0;
    active = requested;
    for (int c = 0; c < 4; c++) {
      int size = active.channels().get(c).steps();
      next[c] %= size;
      if (step[c] >= 0) step[c] %= size;
    }
  }

  public void start(long sample) {
    if (running) return;
    running = true;
    nextClock = external ? Long.MAX_VALUE : sample;
  }

  public void stop(long sample) {
    running = false;
    nextClock = Long.MAX_VALUE;
    clear();
    apply();
  }

  public void reset(long sample) {
    clear();
    for (int c = 0; c < 4; c++) {
      next[c] = 0;
      step[c] = -1;
    }
  }

  private void clear() {
    if (clockEnd != 0) transitions++;
    for (long end : ends) if (end != 0) transitions++;
    clockEnd = 0;
    java.util.Arrays.fill(ends, 0);
  }

  /** Prime changed cables without manufacturing edges from an already-high source. */
  public void connect(boolean connected, boolean clock, boolean reset, long sample) {
    if (external != connected) {
      external = connected;
      nextClock = running && !external ? Math.addExact(sample, interval()) : Long.MAX_VALUE;
    }
    clockHigh = clock;
    resetHigh = reset;
  }

  public void inputs(boolean clock, boolean reset, long sample) {
    if (reset && !resetHigh) reset(sample);
    if (external && running && clock && !clockHigh) {
      if (sample - lastClock >= MIN_CLOCK) tick(sample);
      else if (rejectedClocks < Integer.MAX_VALUE) rejectedClocks++;
    }
    clockHigh = clock;
    resetHigh = reset;
  }

  private long interval() {
    long interval = 720000 / active.tempo();
    remainder += 720000 % active.tempo();
    if (remainder >= active.tempo()) {
      remainder -= active.tempo();
      interval++;
    }
    return interval;
  }

  private void tick(long sample) {
    apply();
    transitions += 5;
    clockEnd = Math.addExact(sample, PULSE);
    lastClock = sample;
    for (int c = 0; c < 4; c++) {
      var channel = active.channels().get(c);
      step[c] = next[c];
      next[c] = (next[c] + 1) % channel.steps();
      ends[c] = !channel.muted() && channel.hit(step[c]) ? clockEnd : 0;
    }
    if (!external) nextClock = Math.addExact(sample, interval());
  }

  public void advance(long sample) {
    if (clockEnd != 0 && clockEnd <= sample) {
      clockEnd = 0;
      transitions++;
    }
    for (int c = 0; c < 4; c++)
      if (ends[c] != 0 && ends[c] <= sample) {
        ends[c] = 0;
        transitions++;
      }
    if (running && !external && nextClock == sample) tick(sample);
  }

  public long nextEvent() {
    long result = nextClock;
    if (clockEnd != 0) result = Math.min(result, clockEnd);
    for (long end : ends) if (end != 0) result = Math.min(result, end);
    return result;
  }

  public boolean output(int channel, long sample) {
    return channel == 4
        ? clockEnd > sample
        : !requested.channels().get(channel).muted() && ends[channel] > sample;
  }

  public long transitions() {
    return transitions;
  }

  public int step(int channel) {
    return step[channel];
  }

  public boolean running() {
    return running;
  }

  public boolean external() {
    return external;
  }

  public int rejectedClocks() {
    return rejectedClocks;
  }

  public SequencerSettings active() {
    return active;
  }

  public boolean pending() {
    return !active.equals(requested);
  }

  public Checkpoint checkpoint(long sample) {
    ChannelState[] channels = new ChannelState[4];
    for (int c = 0; c < 4; c++)
      channels[c] = new ChannelState(next[c], step[c], ends[c] > sample ? ends[c] : 0);
    return new Checkpoint(
        sample,
        active,
        running,
        external,
        clockHigh,
        resetHigh,
        nextClock,
        remainder,
        lastClock,
        clockEnd > sample ? clockEnd : 0,
        rejectedClocks,
        List.of(channels));
  }
}
