package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.client.MidiInput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.sound.midi.MidiUnavailableException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MidiInputTest {
  private static final class Worker implements Executor {
    final ArrayDeque<Runnable> jobs = new ArrayDeque<>();

    @Override
    public void execute(Runnable job) {
      jobs.add(job);
    }

    void run() {
      while (!jobs.isEmpty()) jobs.remove().run();
    }
  }

  private static final class Port implements MidiInput.Port {
    Consumer<MidiInput.Event> receiver;
    int opens, closes;
    int selectedChannel = -2;
    boolean fail, failClose, blockClose, overflow;
    Runnable duringPoll = () -> {};
    Runnable duringOpen = () -> {};

    @Override
    public String key() {
      return "test-device";
    }

    @Override
    public String name() {
      return "Test keyboard";
    }

    @Override
    public MidiInput.Connection open(int channel, Consumer<MidiInput.Event> sink)
        throws MidiUnavailableException {
      selectedChannel = channel;
      return open(sink);
    }

    @Override
    public MidiInput.Connection open(Consumer<MidiInput.Event> sink)
        throws MidiUnavailableException {
      if (fail) throw new MidiUnavailableException("busy");
      opens++;
      receiver = sink;
      duringOpen.run();
      return new MidiInput.Connection() {
        private boolean open = true;

        @Override
        public boolean isOpen() {
          return open;
        }

        @Override
        public boolean poll() {
          duringPoll.run();
          return !overflow;
        }

        @Override
        public void close() {
          if (open) {
            if (blockClose) throw new IllegalStateException("device still open");
            closes++;
            open = false;
            if (failClose) throw new IllegalStateException("provider close failed");
          }
        }
      };
    }

    void note(int channel, int note) {
      receiver.accept(new MidiInput.Event(0x90, channel, note, 100));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Worker worker = new Worker();
    final AtomicLong clock = new AtomicLong();
    final Port port = new Port();
    final List<MidiInput.Port> catalog = new ArrayList<>(List.of(port));
    final AtomicLong scans = new AtomicLong();
    final MidiInput midi =
        new MidiInput(
            () -> {
              scans.incrementAndGet();
              return List.copyOf(catalog);
            },
            worker,
            clock::get);

    void settle() {
      for (int i = 0; i < 8; i++) {
        midi.tick();
        if (worker.jobs.isEmpty()) return;
        worker.run();
      }
      throw new AssertionError("MIDI worker failed to settle");
    }

    void connect(int channel) {
      midi.target(port.key(), null, channel, true);
      settle();
    }

    @Override
    public void close() {
      midi.close();
      worker.run();
    }
  }

  @Test
  void workerPumpsNotesEveryTickWithoutRescanningDevicesEveryTick() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      f.port.duringPoll = () -> f.port.note(0, 69);
      List<MidiInput.Event> notes = new ArrayList<>();
      for (int tick = 1; tick <= 20; tick++) {
        f.clock.set(tick * 50_000_000L);
        f.settle();
        f.midi.drain(notes::add);
        Assertions.assertEquals(tick, notes.size());
        Assertions.assertEquals(tick == 20 ? 2 : 1, f.scans.get());
      }
      f.port.overflow = true;
      f.clock.addAndGet(50_000_000L);
      f.settle();
      Assertions.assertEquals("keyboard_overload", f.midi.takeReset());
      Assertions.assertEquals(1, f.port.closes);
      notes.clear();
      f.midi.drain(notes::add);
      Assertions.assertTrue(notes.isEmpty());
    }
  }

  @Test
  void selectionUsesExactUniqueDescriptorAndOnlySelectedChannel() {
    try (Fixture f = new Fixture()) {
      f.connect(3);
      Assertions.assertEquals(3, f.port.selectedChannel);
      Assertions.assertEquals("midi_ready", f.midi.status());
      f.port.note(2, 60);
      f.port.note(3, 69);
      f.port.receiver.accept(new MidiInput.Event(0xe0, 3, 0, 127));
      f.port.receiver.accept(new MidiInput.Event(0x90, 16, 60, 100));
      List<MidiInput.Event> received = new ArrayList<>();
      f.midi.drain(received::add);
      Assertions.assertEquals(List.of(new MidiInput.Event(0x90, 3, 69, 100)), received);
      f.midi.target("unknown", null, -1, true);
      f.settle();
      Assertions.assertEquals("midi_missing", f.midi.status());
      Assertions.assertEquals(1, f.port.closes);
      f.catalog.add(new Port());
      f.connect(-1);
      Assertions.assertEquals("midi_ambiguous", f.midi.status());
      Assertions.assertEquals(1, f.port.opens);
      f.midi.target(f.port.key(), f.port, -1, true);
      f.settle();
      Assertions.assertEquals("midi_ready", f.midi.status());
    }
  }

  @Test
  void callbackAndQueueAreRevokedAcrossTwentyFocusAndCloseCycles() {
    try (Fixture f = new Fixture()) {
      for (int cycle = 0; cycle < 20; cycle++) {
        f.connect(-1);
        Consumer<MidiInput.Event> old = f.port.receiver;
        f.port.note(0, 60);
        f.midi.target(f.port.key(), null, -1, false);
        f.settle();
        f.connect(-1);
        old.accept(new MidiInput.Event(0x90, 0, 70, 100));
        List<MidiInput.Event> received = new ArrayList<>();
        f.midi.drain(received::add);
        Assertions.assertTrue(received.isEmpty());
        f.midi.target("", null, -1, false);
        f.settle();
        Assertions.assertEquals(f.port.opens, f.port.closes);
      }
    }
  }

  @Test
  void overflowStopsInputAndDoesNotSilentlyDropRelease() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      for (int i = 0; i < 256; i++) f.port.note(0, i % 128);
      List<MidiInput.Event> received = new ArrayList<>();
      f.midi.drain(received::add);
      Assertions.assertEquals(64, received.size());
      for (int i = 0; i < 65; i++) f.port.note(0, i);
      f.midi.tick();
      Assertions.assertEquals("keyboard_overload", f.midi.takeReset());
      received.clear();
      f.midi.drain(received::add);
      Assertions.assertTrue(received.isEmpty());
      f.settle();
      Assertions.assertEquals(f.port.opens, f.port.closes);
    }
  }

  @Test
  void unplugAndFailedOpenReleaseAndRequireExplicitRetry() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      f.catalog.clear();
      f.clock.set(1_000_000_000L);
      f.settle();
      Assertions.assertEquals("midi_missing", f.midi.takeReset());
      Assertions.assertEquals(1, f.port.closes);
      f.catalog.add(f.port);
      f.clock.set(5_000_000_000L);
      f.settle();
      Assertions.assertEquals(1, f.port.opens);
      f.port.fail = true;
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals("midi_unavailable", f.midi.status());
      f.port.fail = false;
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals("midi_ready", f.midi.status());
    }
  }

  @Test
  void lateOpenIsClosedAndTimeoutDoesNotCreateReplacementWorkers() {
    try (Fixture f = new Fixture()) {
      f.port.duringOpen =
          () -> {
            f.clock.set(2_000_000_001L);
            f.midi.tick();
            Assertions.assertEquals("midi_timeout", f.midi.takeReset());
            Assertions.assertTrue(f.worker.jobs.isEmpty());
          };
      f.connect(-1);
      Assertions.assertEquals("midi_timeout", f.midi.status());
      Assertions.assertEquals(1, f.port.closes);
      f.clock.set(10_000_000_000L);
      f.settle();
      Assertions.assertEquals(1, f.port.opens);
    }
  }

  @Test
  void stalledWorkerKeepsTimeoutAcrossRefreshFocusAndSelectionChanges() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      Consumer<MidiInput.Event> old = f.port.receiver;
      f.midi.refresh();
      f.midi.tick();
      // Leave this operation pending to model a native provider that never returns.
      f.clock.set(2_000_000_001L);
      f.midi.tick();
      Assertions.assertEquals("midi_timeout", f.midi.takeReset());
      for (int attempt = 0; attempt < 20; attempt++) {
        f.midi.target(f.port.key(), null, attempt % 16, false);
        f.midi.target(f.port.key(), null, attempt % 16, true);
        f.midi.refresh();
        f.midi.tick();
        Assertions.assertEquals("midi_timeout", f.midi.status());
        Assertions.assertEquals(1, f.worker.jobs.size());
      }
      old.accept(new MidiInput.Event(0x90, 0, 69, 100));
      List<MidiInput.Event> received = new ArrayList<>();
      f.midi.drain(received::add);
      Assertions.assertTrue(received.isEmpty());
      // A late return cleans up, but must not reopen until the player retries.
      f.settle();
      Assertions.assertEquals("midi_timeout", f.midi.status());
      Assertions.assertEquals(1, f.port.opens);
      Assertions.assertEquals(1, f.port.closes);
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals("midi_ready", f.midi.status());
      Assertions.assertEquals(2, f.port.opens);
    }
  }

  @Test
  void providerCloseFailureDoesNotStrandTheController() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      f.port.failClose = true;
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals("midi_unavailable", f.midi.status());
      f.port.failClose = false;
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals("midi_ready", f.midi.status());
    }
  }

  @Test
  void lateResourceCloseFailureStillCompletesTheOperation() {
    try (Fixture f = new Fixture()) {
      f.port.failClose = true;
      f.port.duringOpen = () -> f.midi.target("", null, -1, false);
      f.connect(-1);
      Assertions.assertEquals("midi_off", f.midi.status());
      Assertions.assertEquals(1, f.port.closes);
    }
  }

  @Test
  void failedCloseRetainsOwnershipBeforeAnyReplacementCanOpen() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      f.port.blockClose = true;
      for (int attempt = 0; attempt < 3; attempt++) {
        f.midi.refresh();
        f.settle();
        Assertions.assertEquals(1, f.port.opens);
        Assertions.assertEquals(0, f.port.closes);
      }
      f.port.blockClose = false;
      f.midi.refresh();
      f.settle();
      Assertions.assertEquals(2, f.port.opens);
      Assertions.assertEquals(1, f.port.closes);
    }
  }

  @Test
  void unsupportedFloodDoesNotConsumeQueueAndSelectionChangesCoalesce() {
    try (Fixture f = new Fixture()) {
      f.connect(-1);
      for (int i = 0; i < 1024; i++) f.port.receiver.accept(new MidiInput.Event(0xb0, 0, 1, 127));
      f.midi.tick();
      Assertions.assertNull(f.midi.takeReset());
      f.midi.refresh();
      f.midi.tick();
      for (int i = 0; i < 128; i++) {
        f.midi.target(f.port.key(), null, i % 16, true);
        f.midi.tick();
      }
      Assertions.assertEquals(1, f.worker.jobs.size());
      f.settle();
      Assertions.assertEquals(2, f.port.opens);
      Assertions.assertEquals(1, f.port.closes);
    }
  }
}
