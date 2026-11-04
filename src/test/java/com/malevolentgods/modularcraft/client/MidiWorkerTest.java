package com.malevolentgods.modularcraft.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MidiWorkerTest {
  @Test
  void idleWorkerReceivesInputWithoutWaitingForAnotherClientTick() throws InterruptedException {
    CountDownLatch polled = new CountDownLatch(1);
    try (MidiWorker worker = new MidiWorker(polled::countDown)) {
      Assertions.assertTrue(polled.await(1, TimeUnit.SECONDS));
      worker.execute(() -> {});
    }
  }

  @Test
  void fourPendingOperationsAreBoundedAndCloseDrainsThem() throws InterruptedException {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch drained = new CountDownLatch(4);
    MidiWorker worker = new MidiWorker(() -> {});
    try {
      worker.execute(
          () -> {
            running.countDown();
            try {
              release.await();
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          });
      Assertions.assertTrue(running.await(1, TimeUnit.SECONDS));
      for (int i = 0; i < 4; i++) worker.execute(drained::countDown);
      Assertions.assertThrows(RejectedExecutionException.class, () -> worker.execute(() -> {}));
      worker.close();
      Assertions.assertThrows(RejectedExecutionException.class, () -> worker.execute(() -> {}));
    } finally {
      release.countDown();
      worker.close();
    }
    Assertions.assertTrue(drained.await(1, TimeUnit.SECONDS));
  }
}
