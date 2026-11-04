package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.client.mixin.ChannelAccess;
import com.malevolentgods.modularcraft.client.mixin.SoundEngineAccess;
import com.malevolentgods.modularcraft.dsp.AudioEngine;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.transport.ClockSync;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL10;

/**
 * Owns one Minecraft library channel and twelve reusable 128-frame stereo buffers (32 ms). All
 * OpenAL calls and DSP run on Minecraft's sound executor. The timer submits at most one job. Master
 * volume is applied once by Minecraft's existing AL listener; Records/local are mixed in DSP.
 */
public final class AudioOutput {
  private static volatile AudioOutput active;
  private static final java.util.concurrent.atomic.AtomicInteger CHANNELS =
      new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger TIMERS =
      new java.util.concurrent.atomic.AtomicInteger();

  public static int channels() {
    return CHANNELS.get();
  }

  public static int timers() {
    return TIMERS.get();
  }

  private final SoundEngine owner;
  private final SoundEngineAccess access;
  private final AudioEngine engine;
  private final ClockSync clock;
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ModularCraft audio wakeup");
            t.setDaemon(true);
            return t;
          });
  private final AtomicBoolean pending = new AtomicBoolean();
  private final ByteBuffer pcm = ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder());
  private final float[] samples = new float[256];
  private final int[] buffers = new int[12];
  private final Runnable pumpTask = this::pump;
  private ScheduledFuture<?> wake;
  private Channel channel;
  private int source;
  private long cursor;
  private volatile double peak;
  private volatile int maxQueued;
  private volatile RenderGraph.Listener listener = new RenderGraph.Listener("", 0, 0, 0, 0, 0);
  private volatile boolean closed, failed;
  private volatile long underruns, maxBlockNanos, blocks;
  private final long[] timings = new long[4096];

  public AudioOutput(SoundEngine owner, AudioEngine engine, ClockSync clock) {
    TIMERS.incrementAndGet();
    this.owner = owner;
    access = (SoundEngineAccess) owner;
    this.engine = engine;
    this.clock = clock;
  }

  public void start() {
    active = this;
    access
        .modularcraft$executor()
        .execute(
            () -> {
              if (closed || !access.modularcraft$loaded()) {
                failed = true;
                return;
              }
              channel = access.modularcraft$library().acquireChannel(Library.Pool.STREAMING);
              if (channel == null) {
                failed = true;
                return;
              }
              CHANNELS.incrementAndGet();
              source = ((ChannelAccess) channel).modularcraft$source();
              channel.disableAttenuation();
              channel.setRelative(true);
              channel.setVolume(1);
              AL10.alGenBuffers(buffers);
              cursor = clock.sample(System.nanoTime());
              for (int buffer : buffers) fill(buffer);
              channel.play();
              if (AL10.alGetError() != AL10.AL_NO_ERROR) failed = true;
            });
    wake =
        timer.scheduleAtFixedRate(
            () -> {
              if (!closed && pending.compareAndSet(false, true))
                access.modularcraft$executor().execute(pumpTask);
            },
            2,
            2,
            TimeUnit.MILLISECONDS);
  }

  public void listener(RenderGraph.Listener value) {
    listener = value;
  }

  private void fill(int buffer) {
    long started = System.nanoTime();
    engine.render(cursor, listener, samples);
    cursor += 128;
    for (float value : samples) peak = Math.max(peak, Math.abs(value));
    pcm.clear();
    for (float sample : samples)
      pcm.putShort((short) Math.round(Math.clamp(sample, -1, 1) * 32767));
    pcm.flip();
    AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, pcm, 48000);
    AL10.alSourceQueueBuffers(source, buffer);
    long elapsed = System.nanoTime() - started;
    timings[(int) (blocks % timings.length)] = elapsed;
    maxBlockNanos = Math.max(maxBlockNanos, elapsed);
    blocks++;
    maxQueued = Math.max(maxQueued, AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED));
  }

  private void pump() {
    try {
      if (closed || channel == null) return;
      if (engine.failed() || !access.modularcraft$loaded()) {
        failed = true;
        closeOnAudioThread();
        return;
      }
      int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
      if (processed > buffers.length) {
        failed = true;
        closeOnAudioThread();
        return;
      }
      if (!channel.playing()) {
        underruns++;
        cursor = clock.sample(System.nanoTime());
      }
      for (int i = 0; i < processed; i++) fill(AL10.alSourceUnqueueBuffers(source));
      if (!channel.playing()) channel.play();
      if (AL10.alGetError() != AL10.AL_NO_ERROR) {
        failed = true;
        closeOnAudioThread();
      }
    } finally {
      pending.set(false);
    }
  }

  public String stats() {
    long[] sorted = java.util.Arrays.copyOf(timings, (int) Math.min(blocks, timings.length));
    java.util.Arrays.sort(sorted);
    return "p95BlockNs="
        + (sorted.length == 0 ? 0 : sorted[(int) (sorted.length * .95)])
        + " blocks="
        + blocks
        + " underruns="
        + underruns
        + " maxBlockNs="
        + maxBlockNanos
        + " peak="
        + peak
        + " maxQueued="
        + maxQueued
        + " failed="
        + failed();
  }

  public boolean closed() {
    return closed;
  }

  public boolean failed() {
    return failed || engine.failed();
  }

  public long underruns() {
    return underruns;
  }

  public long maxBlockNanos() {
    return maxBlockNanos;
  }

  public long blocks() {
    return blocks;
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (wake != null) wake.cancel(false);
    timer.shutdownNow();
    TIMERS.decrementAndGet();
    // Only a bounded native resource teardown; no file/network I/O or graph preparation.
    access.modularcraft$executor().executeBlocking(this::closeOnAudioThread);
    if (active == this) active = null;
  }

  private void closeOnAudioThread() {
    if (channel == null) return;
    channel.stop();
    int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
    for (int i = 0; i < queued; i++) AL10.alSourceUnqueueBuffers(source);
    AL10.alDeleteBuffers(buffers);
    access.modularcraft$library().releaseChannel(channel);
    channel = null;
    CHANNELS.decrementAndGet();
  }

  public static void release(SoundEngine engine) {
    AudioOutput output = active;
    if (output != null && output.owner == engine) output.close();
  }
}
