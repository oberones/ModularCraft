package com.malevolentgods.modularcraft.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.sound.midi.MidiUnavailableException;

/** Client-owned MIDI lifecycle. Provider operations belong exclusively to its worker. */
public final class MidiInput implements AutoCloseable {
  public record Event(int command, int channel, int data1, int data2) {
    public boolean supported() {
      return channel >= 0
          && channel < 16
          && data1 >= 0
          && data1 < 128
          && data2 >= 0
          && data2 < 128
          && (command == 0x80
              || command == 0x90
              || command == 0xb0 && (data1 == 64 || data1 == 120 || data1 == 121 || data1 == 123));
    }
  }

  public interface Connection extends AutoCloseable {
    boolean isOpen();

    @Override
    void close();
  }

  public interface Port {
    String key();

    String name();

    Connection open(Consumer<Event> receiver) throws MidiUnavailableException;
  }

  @FunctionalInterface
  public interface Backend {
    List<Port> ports() throws MidiUnavailableException;
  }

  private record Input(long generation, Event event) {}

  private record Request(
      long generation,
      String key,
      Port choice,
      int channel,
      boolean enabled,
      boolean scan,
      List<Port> catalog) {}

  private record Result(long generation, List<Port> ports, String status) {}

  private final Backend backend;
  private final Executor worker;
  private final LongSupplier clock;
  private final ArrayBlockingQueue<Input> events = new ArrayBlockingQueue<>(256);
  private final ArrayBlockingQueue<Result> results = new ArrayBlockingQueue<>(4);
  private final AtomicLong overflow = new AtomicLong(-1);
  private volatile long generation;
  private String key = "", status = "midi_off", reset;
  private Port choice;
  private int channel = -1;
  private boolean enabled, needsWork, scan, busy, timedOut, faulted, closed;
  private long started, nextCheck;
  private List<Port> catalog = List.of();
  // The worker exclusively owns the following native resources; no game/world references.
  private Connection connection;
  private Port opened;
  private long workerGeneration = -1;

  public MidiInput() {
    this(
        new JavaMidiBackend(),
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4),
            runnable -> {
              Thread thread = new Thread(runnable, "ModularCraft MIDI");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()),
        System::nanoTime);
  }

  /** Injectable provider, serial executor and clock; thread pools are shut down on close. */
  public MidiInput(Backend backend, Executor worker, LongSupplier clock) {
    this.backend = backend;
    this.worker = worker;
    this.clock = clock;
  }

  /** Game thread only. channel is -1 for All, otherwise 0..15. Null choice restores by key. */
  public void target(String key, Port choice, int channel, boolean enabled) {
    if (channel < -1 || channel > 15 || key == null || key.length() > 256)
      throw new IllegalArgumentException("midi_selection");
    if (closed
        || this.key.equals(key)
            && Objects.equals(this.choice, choice)
            && this.channel == channel
            && this.enabled == enabled) return;
    this.key = key;
    this.choice = choice;
    this.channel = channel;
    this.enabled = enabled;
    restart(false);
  }

  public void refresh() {
    if (!closed) restart(true);
  }

  private void restart(boolean discover) {
    generation++;
    events.clear();
    overflow.set(-1);
    reset = null;
    faulted = false;
    needsWork = true;
    scan = discover || enabled;
    status = key.isEmpty() ? "midi_off" : enabled ? "midi_connecting" : "midi_paused";
  }

  public void tick() {
    if (closed) return;
    if (overflow.getAndSet(-1) == generation) fail("keyboard_overload");
    Result result;
    while ((result = results.poll()) != null) {
      busy = false;
      if (result.generation != generation || faulted) continue;
      catalog = result.ports;
      status = result.status;
      nextCheck = clock.getAsLong() + 1_000_000_000L;
      if (!status.equals("midi_ready") && enabled && !key.isEmpty()) fail(status);
    }
    if (busy && !timedOut && clock.getAsLong() - started >= 2_000_000_000L) {
      timedOut = true;
      fail("midi_timeout");
    }
    if (busy
        || !(needsWork || enabled && !key.isEmpty() && !faulted && clock.getAsLong() >= nextCheck))
      return;
    Request request =
        new Request(
            generation,
            key,
            choice,
            channel,
            enabled && !faulted,
            scan || enabled && !faulted,
            catalog);
    needsWork = false;
    scan = false;
    busy = true;
    timedOut = false;
    started = clock.getAsLong();
    try {
      worker.execute(() -> reconcile(request));
    } catch (RejectedExecutionException rejected) {
      busy = false;
      fail("midi_unavailable");
      needsWork = false;
    }
  }

  private void fail(String reason) {
    generation++;
    events.clear();
    reset = reason;
    status = reason;
    faulted = true;
    needsWork = true;
    scan = false;
  }

  private void receive(long epoch, int filter, Event event) {
    if (epoch != generation
        || overflow.get() == epoch
        || !event.supported()
        || filter >= 0 && filter != event.channel) return;
    if (!events.offer(new Input(epoch, event))) overflow.accumulateAndGet(epoch, Math::max);
  }

  private void reconcile(Request request) {
    List<Port> ports = request.catalog;
    String message = request.key.isEmpty() ? "midi_off" : "midi_paused";
    try {
      if ((workerGeneration != request.generation || !request.enabled) && !closeConnection())
        throw new IllegalStateException("MIDI provider failed to close");
      if (request.scan && generation == request.generation)
        ports = backend.ports().stream().limit(64).toList();
      if (request.enabled && !request.key.isEmpty() && generation == request.generation) {
        if (connection != null) {
          message = connection.isOpen() && ports.contains(opened) ? "midi_ready" : "midi_missing";
          if (!message.equals("midi_ready")) closeConnection();
        } else {
          List<Port> matches = ports.stream().filter(p -> p.key().equals(request.key)).toList();
          Port selected = request.choice;
          if (selected != null && !ports.contains(selected)) message = "midi_missing";
          else if (selected == null && matches.size() != 1)
            message = matches.isEmpty() ? "midi_missing" : "midi_ambiguous";
          else {
            opened = selected == null ? matches.getFirst() : selected;
            workerGeneration = request.generation;
            connection = opened.open(event -> receive(request.generation, request.channel, event));
            message = "midi_ready";
          }
        }
      }
    } catch (MidiUnavailableException | RuntimeException failure) {
      closeConnection();
      message = "midi_unavailable";
    } finally {
      if (generation != request.generation && !closeConnection()) message = "midi_unavailable";
    }
    // At most one operation is outstanding, so this bounded mailbox cannot fill.
    results.add(new Result(request.generation, ports, message));
  }

  // Retain a failed handle so no replacement opens before its close succeeds.
  @SuppressWarnings("PMD.CloseResource")
  private boolean closeConnection() {
    Connection old = connection;
    try {
      if (old != null) old.close();
      connection = null;
      opened = null;
      return true;
    } catch (RuntimeException failure) {
      return false;
    }
  }

  /** Game thread only; one tick consumes at most 64 ordered, current-generation events. */
  public void drain(Consumer<Event> consumer) {
    if (closed || faulted || overflow.get() == generation) return;
    for (int count = 0; count < 64; count++) {
      Input input = events.poll();
      if (input == null) break;
      if (input.generation == generation) consumer.accept(input.event);
    }
  }

  public List<Port> ports() {
    return catalog;
  }

  public String status() {
    return status;
  }

  public String takeReset() {
    String value = reset;
    reset = null;
    return value;
  }

  @Override
  // ExecutorService.close would block the game thread; shutdown drains our queued close instead.
  @SuppressWarnings("PMD.CloseResource")
  public void close() {
    if (closed) return;
    closed = true;
    generation++;
    events.clear();
    worker.execute(this::closeConnection);
    if (worker instanceof ThreadPoolExecutor executor) executor.shutdown();
  }
}
