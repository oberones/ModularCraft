package com.malevolentgods.modularcraft.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sound.midi.MidiUnavailableException;

/** One worker-owned helper process at a time; no native input handles live in Minecraft. */
final class MidiProcessBackend implements MidiInput.Backend {
  @FunctionalInterface
  interface Launcher {
    Process start(String key, int ordinal, int matches, int channel) throws IOException;
  }

  private final MidiInput.Backend discovery;
  private final Launcher launcher;
  private Process active;
  private Path helperDirectory;
  private boolean helperReady;

  MidiProcessBackend() {
    discovery = new JavaMidiBackend();
    launcher = this::launch;
  }

  MidiProcessBackend(MidiInput.Backend discovery, Launcher launcher) {
    this.discovery = discovery;
    this.launcher = launcher;
  }

  @Override
  public List<MidiInput.Port> ports() throws MidiUnavailableException {
    List<MidiInput.Port> found = discovery.ports().stream().limit(64).toList();
    List<MidiInput.Port> result = new ArrayList<>();
    for (MidiInput.Port port : found) {
      int ordinal = (int) result.stream().filter(p -> p.key().equals(port.key())).count();
      int matches = (int) found.stream().filter(p -> p.key().equals(port.key())).count();
      result.add(new Port(port, ordinal, matches, this));
    }
    return List.copyOf(result);
  }

  private record Port(MidiInput.Port source, int ordinal, int matches, MidiProcessBackend owner)
      implements MidiInput.Port {
    @Override
    public String key() {
      return source.key();
    }

    @Override
    public String name() {
      return source.name();
    }

    @Override
    public MidiInput.Connection open(Consumer<MidiInput.Event> sink)
        throws MidiUnavailableException {
      return open(-1, sink);
    }

    @Override
    public MidiInput.Connection open(int channel, Consumer<MidiInput.Event> sink)
        throws MidiUnavailableException {
      return owner.open(key(), ordinal, matches, channel, sink);
    }
  }

  // Process streams remain owned until terminate(), including partial-open failures.
  @SuppressWarnings("PMD.CloseResource")
  private MidiInput.Connection open(
      String key, int ordinal, int matches, int channel, Consumer<MidiInput.Event> sink)
      throws MidiUnavailableException {
    try {
      if (active != null) terminate(active);
      active = launcher.start(key, ordinal, matches, channel);
      InputStream input = active.getInputStream();
      long deadline = System.nanoTime() + 1_500_000_000L;
      while (input.available() == 0 && active.isAlive() && System.nanoTime() < deadline)
        Thread.sleep(5);
      if (input.available() == 0 || input.read() != 1)
        throw new IOException("MIDI helper did not become ready");
      return new Handle(active, sink);
    } catch (IOException | InterruptedException | RuntimeException failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      if (active != null) terminate(active);
      MidiUnavailableException unavailable =
          new MidiUnavailableException("MIDI helper unavailable");
      unavailable.initCause(failure);
      throw unavailable;
    }
  }

  private static final class Handle implements MidiInput.Connection {
    private final Process process;
    private final Consumer<MidiInput.Event> sink;
    private final byte[] frame = new byte[4];
    private int used;

    Handle(Process process, Consumer<MidiInput.Event> sink) {
      this.process = process;
      this.sink = sink;
    }

    @Override
    public boolean isOpen() {
      return process.isAlive();
    }

    @Override
    // Borrow the process stream; closing after a poll would destroy the live connection.
    @SuppressWarnings("PMD.CloseResource")
    public boolean poll() {
      try {
        InputStream input = process.getInputStream();
        for (int count = 0; count < 64; ) {
          if (input.available() == 0) break;
          int value = input.read();
          if (value < 0) break;
          frame[used++] = (byte) value;
          if (used != 4) continue;
          used = 0;
          if ((frame[0] & 0xff) == 0xff) return false;
          MidiInput.Event event =
              new MidiInput.Event(
                  frame[0] & 0xff, frame[1] & 0xff, frame[2] & 0xff, frame[3] & 0xff);
          if (!event.supported()) throw new IOException("Invalid MIDI helper event");
          sink.accept(event);
          count++;
        }
        if (!process.isAlive()) {
          if (process.exitValue() == 2) return false;
          throw new IOException("MIDI helper exited");
        }
        return true;
      } catch (IOException failure) {
        throw new IllegalStateException("MIDI helper read failed", failure);
      }
    }

    @Override
    public void close() {
      terminate(process);
    }
  }

  // Close every process stream, preserving failures; ownership ends only after process exit.
  @SuppressWarnings("PMD.CloseResource")
  private static void terminate(Process process) {
    boolean interrupted = false;
    if (process.isAlive()) {
      try {
        process.getOutputStream().write(0);
        process.getOutputStream().flush();
        if (!process.waitFor(1250, TimeUnit.MILLISECONDS)) process.destroyForcibly();
      } catch (IOException failure) {
        process.destroyForcibly();
      } catch (InterruptedException failure) {
        process.destroyForcibly();
        interrupted = true;
      }
      try {
        if (process.isAlive()) process.waitFor(250, TimeUnit.MILLISECONDS);
      } catch (InterruptedException failure) {
        interrupted = true;
      }
    }
    if (interrupted) Thread.currentThread().interrupt();
    if (process.isAlive()) throw new IllegalStateException("MIDI helper still alive");
    IOException failure = null;
    for (Closeable stream :
        List.of(process.getOutputStream(), process.getInputStream(), process.getErrorStream())) {
      try {
        stream.close();
      } catch (IOException closeFailure) {
        if (failure == null) failure = closeFailure;
        else failure.addSuppressed(closeFailure);
      }
    }
    if (failure != null)
      throw new IllegalStateException("MIDI helper stream cleanup failed", failure);
  }

  private Process launch(String key, int ordinal, int matches, int channel) throws IOException {
    if (!helperReady) {
      if (helperDirectory == null) {
        helperDirectory = Files.createTempDirectory("modularcraft-midi-");
        helperDirectory.toFile().deleteOnExit();
      }
      Path directory = helperDirectory;
      // Extract only our standalone classes, independent of NeoForge's module/classpath layout.
      String[] classes = {
        "MidiDeviceProcess", "JavaMidiBackend", "JavaMidiBackend$Port", "JavaMidiBackend$Port$1",
        "JavaMidiBackend$Handle", "MidiInput", "MidiInput$Backend", "MidiInput$Port",
        "MidiInput$Connection", "MidiInput$Event"
      };
      Path packagePath = directory.resolve("com/malevolentgods/modularcraft/client");
      Files.createDirectories(packagePath);
      Path parent = directory;
      for (String part : List.of("com", "malevolentgods", "modularcraft", "client")) {
        parent = parent.resolve(part);
        parent.toFile().deleteOnExit();
      }
      for (String name : classes) {
        Path destination = packagePath.resolve(name + ".class");
        try (InputStream source = MidiProcessBackend.class.getResourceAsStream(name + ".class")) {
          if (source == null) throw new IOException("Missing MIDI helper class " + name);
          Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        destination.toFile().deleteOnExit();
      }
      helperReady = true;
    }
    return new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xms16m",
            "-Xmx32m",
            "-XX:MaxMetaspaceSize=64m",
            "-Djava.awt.headless=true",
            "-cp",
            helperDirectory.toString(),
            MidiDeviceProcess.class.getName(),
            key,
            Integer.toString(ordinal),
            Integer.toString(matches),
            Integer.toString(channel),
            Long.toString(ProcessHandle.current().pid()))
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
  }
}
