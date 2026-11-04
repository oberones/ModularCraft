package com.malevolentgods.modularcraft.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sound.midi.MidiUnavailableException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MidiProcessBackendTest {
  private static final class CachedPort implements MidiInput.Port {
    int opens;

    @Override
    public String key() {
      return "device";
    }

    @Override
    public String name() {
      return "Keyboard";
    }

    @Override
    public MidiInput.Connection open(Consumer<MidiInput.Event> receiver) {
      if (++opens == 1) receiver.accept(new MidiInput.Event(0x90, 0, 69, 100));
      // Models Java Sound reporting open while its cached reader no longer delivers notes.
      return new MidiInput.Connection() {
        @Override
        public boolean isOpen() {
          return true;
        }

        @Override
        public void close() {}
      };
    }
  }

  private static final class Child extends Process {
    boolean alive = true;
    boolean stuckClose;
    boolean stuckKill;
    boolean killed;
    final InputStream input;

    Child(byte... data) {
      input = new ByteArrayInputStream(data);
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream() {
        @Override
        public void write(int value) {
          if (!stuckClose) alive = false;
        }
      };
    }

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return exitValue();
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return !alive;
    }

    @Override
    public int exitValue() {
      if (alive) throw new IllegalThreadStateException("alive");
      return 0;
    }

    @Override
    public boolean isAlive() {
      return alive;
    }

    @Override
    public void destroy() {
      if (!stuckClose) alive = false;
    }

    @Override
    public Process destroyForcibly() {
      killed = true;
      if (!stuckKill) alive = false;
      return this;
    }
  }

  @Test
  void aHelperThatHasNotExitedBlocksReplacement() throws MidiUnavailableException {
    Child child = new Child((byte) 1);
    child.stuckClose = true;
    child.stuckKill = true;
    int[] launches = {0};
    MidiProcessBackend backend =
        new MidiProcessBackend(
            () -> List.of(new CachedPort()),
            (key, ordinal, matches, channel) -> {
              launches[0]++;
              return child;
            });
    try (MidiInput.Connection connection = backend.ports().getFirst().open(event -> {})) {
      Assertions.assertThrows(IllegalStateException.class, connection::close);
      Assertions.assertThrows(
          IllegalStateException.class, () -> backend.ports().getFirst().open(event -> {}));
      Assertions.assertEquals(1, launches[0]);
      child.stuckKill = false;
    }
    Assertions.assertFalse(child.isAlive());
  }

  @Test
  void blockedNativeCloseIsTerminatedBeforeReopening() throws MidiUnavailableException {
    CachedPort port = new CachedPort();
    List<Child> children = new ArrayList<>();
    MidiProcessBackend backend =
        new MidiProcessBackend(
            () -> List.of(port),
            (key, ordinal, matches, channel) -> {
              Assertions.assertTrue(children.stream().noneMatch(Process::isAlive));
              Child child = new Child((byte) 1);
              child.stuckClose = true;
              children.add(child);
              return child;
            });
    for (int cycle = 0; cycle < 20; cycle++) {
      try (MidiInput.Connection connection = backend.ports().getFirst().open(event -> {})) {
        Assertions.assertTrue(connection.isOpen());
      }
      Assertions.assertTrue(children.getLast().killed);
      Assertions.assertFalse(children.getLast().isAlive());
    }
  }

  @Test
  void eventPumpIsBoundedAndPreservesPitchEndpoints() throws MidiUnavailableException {
    ByteArrayOutputStream wire = new ByteArrayOutputStream();
    wire.write(1);
    for (int i = 0; i < 65; i++)
      wire.writeBytes(new byte[] {(byte) 0x90, 15, (byte) (i % 2 * 127), 100});
    MidiProcessBackend backend =
        new MidiProcessBackend(
            () -> List.of(new CachedPort()),
            (key, ordinal, matches, channel) -> new Child(wire.toByteArray()));
    List<MidiInput.Event> events = new ArrayList<>();
    try (MidiInput.Connection connection = backend.ports().getFirst().open(events::add)) {
      Assertions.assertTrue(events.isEmpty());
      Assertions.assertTrue(connection.poll());
      Assertions.assertEquals(64, events.size());
      Assertions.assertEquals(0, events.getFirst().data1());
      Assertions.assertEquals(127, events.getLast().data1());
      Assertions.assertTrue(connection.poll());
      Assertions.assertEquals(65, events.size());
    }
  }

  @Test
  void helperOverflowAndMalformedMessagesFailClosed() throws MidiUnavailableException {
    for (byte command : new byte[] {(byte) 0xff, (byte) 0xe0}) {
      Child child = new Child((byte) 1, command, (byte) 0, (byte) 0, (byte) 0);
      MidiProcessBackend backend =
          new MidiProcessBackend(
              () -> List.of(new CachedPort()), (key, ordinal, matches, channel) -> child);
      try (MidiInput.Connection connection =
          backend.ports().getFirst().open(event -> Assertions.fail())) {
        if (command == (byte) 0xff) Assertions.assertFalse(connection.poll());
        else Assertions.assertThrows(IllegalStateException.class, connection::poll);
      }
      Assertions.assertFalse(child.isAlive());
    }
  }

  @Test
  void failedHandshakeClosesTheHelper() throws MidiUnavailableException {
    Child child = new Child((byte) 0);
    MidiProcessBackend backend =
        new MidiProcessBackend(
            () -> List.of(new CachedPort()), (key, ordinal, matches, channel) -> child);
    Assertions.assertThrows(
        MidiUnavailableException.class,
        () -> backend.ports().getFirst().open(event -> Assertions.fail()));
    Assertions.assertFalse(child.isAlive());
  }

  @Test
  void reconnectDeliversFreshNotesEvenWhenProviderCachesABrokenReader()
      throws MidiUnavailableException {
    CachedPort cached = new CachedPort();
    List<Child> children = new ArrayList<>();
    MidiProcessBackend backend =
        new MidiProcessBackend(
            () -> List.of(cached),
            (key, ordinal, matches, channel) -> {
              Assertions.assertTrue(children.stream().noneMatch(Process::isAlive));
              Child child = new Child((byte) 1, (byte) 0x90, (byte) 0, (byte) 69, (byte) 100);
              children.add(child);
              return child;
            });
    List<MidiInput.Event> notes = new ArrayList<>();
    for (int cycle = 0; cycle < 20; cycle++) {
      try (MidiInput.Connection connection = backend.ports().getFirst().open(notes::add)) {
        Assertions.assertTrue(connection.isOpen());
        Assertions.assertTrue(connection.poll());
      }
      Assertions.assertEquals(cycle + 1, notes.size());
      Assertions.assertTrue(children.stream().noneMatch(Process::isAlive));
    }
  }
}
