package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.transport.EventQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Eight prepared handoffs, 1,024 scheduled events. One preparation producer, one audio consumer.
 * Graph replacement preserves surviving identities and crossfades topology changes for 480 samples.
 * Gate and parameter-only snapshots do not crossfade away envelope curves.
 */
public final class AudioEngine {
  private final AtomicReferenceArray<EventQueue.Event<RenderGraph>> incoming =
      new AtomicReferenceArray<>(8);
  private int write, read;
  private final EventQueue<RenderGraph> scheduled = new EventQueue<>();
  private final float[] currentBuffer = new float[256], oldBuffer = new float[256];
  private final Output left = new Output(), right = new Output();
  private volatile RenderGraph graph = new RenderGraph(List.of());
  private RenderGraph old;
  private int fade;
  private long lastSequence = -1;
  private volatile boolean failed;

  public boolean submit(long sample, long sequence, RenderGraph prepared) {
    if (failed || incoming.get(write) != null) {
      failed = true;
      return false;
    }
    incoming.set(write, new EventQueue.Event<>(sample, sequence, prepared));
    write = (write + 1) % 8;
    return true;
  }

  public void render(long sample, RenderGraph.Listener listener, float[] output) {
    java.util.Arrays.fill(output, 0);
    if (failed) return;
    EventQueue.Event<RenderGraph> next;
    while ((next = incoming.get(read)) != null) {
      incoming.set(read, null);
      read = (read + 1) % 8;
      if (!scheduled.offer(next)) {
        failed = true;
        return;
      }
    }
    int frame = 0;
    while (frame < 128) {
      while ((next = scheduled.poll(sample + frame)) != null) {
        if (next.sequence() < lastSequence) continue;
        lastSequence = next.sequence();
        RenderGraph prepared = next.value();
        prepared.adopt(graph);
        if (!prepared.sameTopology(graph)) {
          old = graph;
          fade = 480;
        }
        graph = prepared;
      }
      EventQueue.Event<RenderGraph> pending = scheduled.peek();
      int length =
          pending == null
              ? 128 - frame
              : (int) Math.min(128 - frame, Math.max(1, pending.sample() - sample - frame));
      graph.process(sample + frame, listener, currentBuffer, length);
      if (fade > 0 && old != null) old.process(sample + frame, listener, oldBuffer, length);
      for (int i = 0; i < length; i++) {
        double alpha = fade > 0 ? 1 - fade / 480.0 : 1;
        double l = currentBuffer[i * 2], r = currentBuffer[i * 2 + 1];
        if (fade > 0 && old != null) {
          l = l * alpha + oldBuffer[i * 2] * (1 - alpha);
          r = r * alpha + oldBuffer[i * 2 + 1] * (1 - alpha);
          if (--fade == 0) old = null;
        }
        output[(frame + i) * 2] = (float) left.next(l);
        output[(frame + i) * 2 + 1] = (float) right.next(r);
      }
      frame += length;
    }
  }

  public MixerMeter.Reading meter(java.util.UUID id, long now) {
    RenderGraph current = graph;
    MixerMeter.Reading reading = current.meter(id, now);
    return failed || graph != current ? null : reading;
  }

  public boolean failed() {
    return failed;
  }
}
