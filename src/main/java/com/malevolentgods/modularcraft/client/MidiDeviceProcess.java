package com.malevolentgods.modularcraft.client;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone Java Sound host. Its process lifetime contains native provider threads and caches. */
public final class MidiDeviceProcess {
  private MidiDeviceProcess() {}

  public static void main(String[] args) {
    if (args.length != 5) System.exit(1);
    int result = 1;
    try {
      long parent = Long.parseLong(args[4]);
      Thread watchdog = new Thread(() -> watchParent(parent), "MIDI parent watchdog");
      watchdog.setDaemon(true);
      watchdog.start();
      result =
          perform(
              args[0],
              Integer.parseInt(args[1]),
              Integer.parseInt(args[2]),
              Integer.parseInt(args[3]));
    } catch (IOException | javax.sound.midi.MidiUnavailableException | RuntimeException failure) {
      result = 1;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    // Java Sound can leave non-daemon input threads alive after device.close().
    System.exit(result);
  }

  private static void watchParent(long pid) {
    try {
      ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
      while (parent != null && parent.isAlive()) Thread.sleep(250);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    // Even a provider stuck in open/close must not outlive Minecraft.
    Runtime.getRuntime().halt(0);
  }

  private static int perform(String key, int ordinal, int expected, int channel)
      throws IOException, javax.sound.midi.MidiUnavailableException, InterruptedException {
    if (channel < -1
        || channel > 15
        || key.length() > 256
        || ordinal < 0
        || expected < 1
        || expected > 64
        || ordinal >= expected) return 1;
    List<MidiInput.Port> matches =
        new JavaMidiBackend().ports().stream().filter(port -> port.key().equals(key)).toList();
    if (matches.size() != expected) return 1;
    ArrayBlockingQueue<MidiInput.Event> queue = new ArrayBlockingQueue<>(256);
    AtomicBoolean overflow = new AtomicBoolean();
    try (MidiInput.Connection connection =
        matches
            .get(ordinal)
            .open(
                event -> {
                  if (event.supported()
                      && (channel < 0 || event.channel() == channel)
                      && !queue.offer(event)) overflow.set(true);
                })) {
      if (!connection.isOpen()) return 1;
      System.out.write(1);
      System.out.flush();
      while (System.in.available() == 0) {
        if (overflow.get()) {
          System.out.write(new byte[] {(byte) 0xff, 0, 0, 0});
          System.out.flush();
          return 2;
        }
        MidiInput.Event event = queue.poll(20, TimeUnit.MILLISECONDS);
        for (int count = 0; event != null && count < 64; count++) {
          System.out.write(event.command());
          System.out.write(event.channel());
          System.out.write(event.data1());
          System.out.write(event.data2());
          event = count < 63 ? queue.poll() : null;
        }
        System.out.flush();
      }
    }
    return 0;
  }
}
