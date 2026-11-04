package com.malevolentgods.modularcraft.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Serial lifecycle operations and input polling on one bounded client worker. */
final class MidiWorker implements Executor, AutoCloseable {
  private final ArrayBlockingQueue<Runnable> jobs = new ArrayBlockingQueue<>(4);
  private volatile boolean stopping;

  MidiWorker(Runnable poll) {
    Thread thread = new Thread(() -> run(poll), "ModularCraft MIDI");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public synchronized void execute(Runnable job) {
    if (stopping || !jobs.offer(job))
      throw new RejectedExecutionException("MIDI worker unavailable");
  }

  private void run(Runnable poll) {
    try {
      while (!stopping || !jobs.isEmpty()) {
        Runnable job = jobs.poll(5, TimeUnit.MILLISECONDS);
        if (job != null) job.run();
        if (!stopping) poll.run();
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public synchronized void close() {
    stopping = true;
  }
}
