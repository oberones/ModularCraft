package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.StepSequencerSettings;

/** Single-owner melodic source. Prepared settings and checkpoints allocate only off audio. */
public final class StepSequencer {
  public record Checkpoint(
      Sequencer.Checkpoint clock,
      StepSequencerSettings active,
      int note,
      long gateStart,
      long gateEnd) {
    public Checkpoint {
      if (clock == null
          || active == null
          || !clock.active().equals(active.clockSettings())
          || note < 0
          || note > 127
          || gateStart < 0
          || gateEnd < 0
          || gateStart > clock.sample() + 1
          || gateEnd > clock.sample() + 96000
          || (gateEnd != 0 && (gateEnd <= clock.sample() || gateStart >= gateEnd))
          || (gateEnd == 0 && gateStart != 0)
          || (gateEnd != 0
              && (clock.channels().getFirst().step() < 0
                  || active.notes().get(clock.channels().getFirst().step()) != note
                  || gateEnd != clock.lastClock() + active.gateMillis() * 48L
                  || (gateStart != 0 && gateStart != clock.lastClock() + 1)))
          || (!clock.running() && (gateStart != 0 || gateEnd != 0)))
        throw new IllegalArgumentException("step_checkpoint");
    }
  }

  private final Sequencer clock;
  private StepSequencerSettings active, requested;
  private int note = 60;
  private long gateStart, gateEnd, transitions;

  public StepSequencer(StepSequencerSettings settings, long sample) {
    active = requested = settings;
    clock = new Sequencer(settings.clockSettings(), sample);
  }

  public StepSequencer(StepSequencerSettings settings, Checkpoint state) {
    requested = settings;
    active = state.active();
    clock = new Sequencer(settings.clockSettings(), state.clock());
    note = state.note();
    gateStart = state.gateStart();
    gateEnd = state.gateEnd();
  }

  public void configure(StepSequencerSettings settings, long sample) {
    requested = settings;
    clock.configure(settings.clockSettings(), sample);
    if (!running()) active = requested;
  }

  public void start(long sample) {
    clock.start(sample);
  }

  public void stop(long sample) {
    clock.stop(sample);
    clear();
    active = requested;
  }

  public void reset(long sample) {
    clock.reset(sample);
    clear();
  }

  private void clear() {
    if (gateEnd != 0) transitions++;
    gateStart = gateEnd = 0;
  }

  public void connect(boolean connected, boolean high, boolean reset, long sample) {
    clock.connect(connected, high, reset, sample);
  }

  public void inputs(boolean high, boolean reset, long sample) {
    long before = clock.clockEvents();
    // A reset clears an active note even when no clock edge accompanies it.
    int oldStep = clock.step(0);
    clock.inputs(high, reset, sample);
    if (oldStep != -1 && clock.step(0) == -1) clear();
    if (clock.clockEvents() != before) tick(sample);
  }

  private void tick(long sample) {
    boolean overlap = gateEnd >= sample && gateEnd != 0;
    active = requested;
    int value = active.notes().get(clock.step(0));
    clear();
    if (value >= 0) {
      note = value;
      gateStart = sample + (overlap ? 1 : 0);
      gateEnd = sample + active.gateMillis() * 48L;
      transitions++;
    }
  }

  public void advance(long sample) {
    long before = clock.clockEvents();
    clock.advance(sample);
    if (clock.clockEvents() != before) tick(sample);
    else if (gateEnd != 0 && gateEnd <= sample) clear();
    if (gateEnd != 0 && gateStart <= sample && gateStart != 0) {
      transitions++;
      gateStart = 0;
    }
  }

  public long nextEvent() {
    long next = clock.nextEvent();
    if (gateEnd != 0) {
      next = Math.min(next, gateEnd);
      // Deferred retrigger starts exactly one sample after the clock.
      if (gateStart > clock.lastClock()) next = Math.min(next, gateStart);
    }
    return next;
  }

  public boolean gate(long sample) {
    return gateEnd > sample && gateStart <= sample;
  }

  public boolean clock(long sample) {
    return clock.output(4, sample);
  }

  public int note() {
    return note;
  }

  public int step() {
    return clock.step(0);
  }

  public boolean running() {
    return clock.running();
  }

  public boolean external() {
    return clock.external();
  }

  public int rejectedClocks() {
    return clock.rejectedClocks();
  }

  public long transitions() {
    return clock.transitions() + transitions;
  }

  public StepSequencerSettings active() {
    return active;
  }

  public boolean pending() {
    return !active.equals(requested);
  }

  public Checkpoint checkpoint(long sample) {
    return new Checkpoint(
        clock.checkpoint(sample),
        active,
        note,
        gateEnd > sample ? gateStart : 0,
        gateEnd > sample ? gateEnd : 0);
  }
}
