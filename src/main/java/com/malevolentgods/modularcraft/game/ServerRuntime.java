package com.malevolentgods.modularcraft.game;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.KeyboardPerformance;
import com.malevolentgods.modularcraft.domain.KeyboardSession;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.ControlTimeline;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import com.malevolentgods.modularcraft.network.Messages;
import com.malevolentgods.modularcraft.transport.Budgets;
import com.malevolentgods.modularcraft.transport.EditInbox;
import com.malevolentgods.modularcraft.transport.Transport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * All world access runs on the logical server thread. Network admission only stores bounded
 * requests.
 */
public final class ServerRuntime {
  private static final Map<MinecraftServer, ServerRuntime> INSTANCES = new ConcurrentHashMap<>();

  private record Queued(
      ServerPlayer player, Messages.Control message, Messages.Keyboard keyboard, long received) {
    Queued(ServerPlayer player, Messages.Control message) {
      this(player, message, null, 0);
    }
  }

  private record Selection(UUID module, Port port, long revision) {}

  private record Held(UUID module, long expires) {}

  private final MinecraftServer server;
  private final WorldData data;
  private final WorldState state;
  private final Transport transport = new Transport(System::nanoTime);
  private final Budgets budgets = new Budgets();
  private final Map<UUID, ModuleEntity> loaded = new LinkedHashMap<>();
  private final Map<UUID, Envelope> envelopes = new HashMap<>();
  private final Map<UUID, KeyboardPerformance> keyboards = new HashMap<>();
  private final Map<UUID, KeyboardSession> sessions = new HashMap<>();
  private final Set<UUID> keyboardOverload = new HashSet<>();
  private final Map<UUID, Held> held = new HashMap<>();
  private final Map<UUID, Selection> selections = new HashMap<>();
  private final Map<UUID, UUID> inspecting = new HashMap<>();
  private final Map<UUID, Set<UUID>> known = new HashMap<>();
  private final Map<UUID, Long> lastClock = new HashMap<>();
  private final EditInbox<Queued> queue = new EditInbox<>();
  private final Map<UUID, Long> lastNotice = new HashMap<>();
  private final Set<UUID> throttled = new HashSet<>();
  private final Map<UUID, Sequencer> sequencers = new HashMap<>();
  private final Map<UUID, Boolean> baseGates = new HashMap<>();
  private final Map<UUID, ControlTimeline> timelines = new HashMap<>();
  private final Set<UUID> pendingSuspensions = new HashSet<>();
  private final Map<UUID, Integer> timelineTokens = new HashMap<>();
  private long controlRevision = -1, controlSample;
  private boolean controlsDirty = true;
  private int transitionTokens = 4096;
  private long performanceSample;
  private long sequence, lastBroadcast, tickNanos, maxTickNanos;

  private ServerRuntime(MinecraftServer server) {
    this.server = server;
    data = server.overworld().getDataStorage().computeIfAbsent(WorldData.FACTORY, "modularcraft");
    state = data.state();
  }

  public static ServerRuntime get(MinecraftServer server) {
    return INSTANCES.computeIfAbsent(server, ServerRuntime::new);
  }

  public static void register() {
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> get(event.getServer()).tick());
    NeoForge.EVENT_BUS.addListener(
        (ServerStoppedEvent event) -> INSTANCES.remove(event.getServer()));
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerLoggedOutEvent event) -> {
          if (event.getEntity() instanceof ServerPlayer p) get(p.getServer()).leave(p);
        });
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerChangedDimensionEvent event) -> {
          if (event.getEntity() instanceof ServerPlayer p) get(p.getServer()).leave(p);
        });
    NeoForge.EVENT_BUS.addListener(
        (BlockEvent.BreakEvent event) -> {
          if (event.getLevel().getBlockEntity(event.getPos()) instanceof ModuleEntity module
              && !module.editable(event.getPlayer())) {
            event.setCanceled(true);
            event
                .getPlayer()
                .displayClientMessage(
                    Component.translatable("modularcraft.error.permission"), true);
          }
        });
  }

  public static void pause(MinecraftServer server, boolean paused) {
    ServerRuntime runtime = INSTANCES.get(server);
    if (runtime != null) runtime.transport.paused(paused);
  }

  public static void enqueue(ServerPlayer player, Messages.Control message) {
    ServerRuntime runtime = INSTANCES.get(player.getServer());
    if (runtime == null) return;
    UUID id = player.getUUID();
    boolean cleanup =
        message.action() == Messages.Action.CLOSE
            || (message.action() == Messages.Action.GATE && message.value() == 0);
    String coalesce =
        message.action() == Messages.Action.PARAMETER
            ? message.id() + ":" + message.parameter()
            : "";
    if (!runtime.queue.offer(
        id,
        coalesce,
        message.action() == Messages.Action.GATE && message.value() == 1,
        cleanup,
        new Queued(player, message))) {
      synchronized (runtime.throttled) {
        if (runtime.throttled.size() < 256) runtime.throttled.add(id);
      }
    }
  }

  public static void enqueue(ServerPlayer player, Messages.Keyboard message) {
    ServerRuntime runtime = INSTANCES.get(player.getServer());
    if (runtime == null) return;
    // Releases stay in order; emergency cleanup alone uses the priority lane.
    if (!runtime.queue.offer(
        player.getUUID(),
        "",
        false,
        message.cleanup(),
        new Queued(player, null, message, System.nanoTime()))) {
      synchronized (runtime.keyboardOverload) {
        if (runtime.keyboardOverload.size() < 256) runtime.keyboardOverload.add(player.getUUID());
      }
    }
  }

  public boolean canPlace(ServerPlayer player) {
    if (!data.writable()) {
      error(player, "quarantined");
      return false;
    }
    if (state.modules().size() >= 256) {
      error(player, "world_capacity");
      return false;
    }
    if (state.modules().stream().filter(m -> m.owner().equals(player.getUUID())).count() >= 64) {
      error(player, "player_modules");
      return false;
    }
    try {
      data.reserveRecord(4096);
    } catch (IllegalArgumentException capacity) {
      error(player, key(capacity));
      return false;
    }
    return true;
  }

  public void attach(ModuleEntity entity) {
    ModuleState m = entity.state();
    if (!data.writable()
        || data.blocked(m.id())
        || !m.dimension().equals(entity.getLevel().dimension().location().toString())) {
      entity.quarantine();
      return;
    }
    try {
      if (state.contains(m.id())) {
        ModuleState indexed = state.get(m.id());
        if (!indexed.owner().equals(m.owner())
            || indexed.kind() != m.kind()
            || indexed.x() != m.x()
            || indexed.y() != m.y()
            || indexed.z() != m.z()
            || !indexed.dimension().equals(m.dimension()))
          throw new IllegalArgumentException("identity_conflict");
        // The global index and block entity are both durable. Newer accepted semantic revision
        // wins.
        if (m.revision() > indexed.revision()) state.reconcile(m);
        state.loaded(m.id(), true);
        entity.accepted(state.get(m.id()));
      } else {
        for (ModuleState old : state.modules())
          if (old.dimension().equals(m.dimension())
              && old.x() == m.x()
              && old.y() == m.y()
              && old.z() == m.z()) remove(old.id());
        state.place(m.runtime(true, false));
        entity.accepted(state.get(m.id()));
      }
      loaded.put(m.id(), entity);
      data.setDirty();
    } catch (IllegalArgumentException invalid) {
      entity.quarantine();
    }
  }

  public void unload(UUID id) {
    if (!state.contains(id)) return;
    Set<UUID> affected = state.component(id);
    loaded.remove(id);
    state.loaded(id, false);
    clearPerformance(affected);
    changed(affected, transport.sample());
  }

  public void remove(UUID id) {
    if (!state.contains(id)) return;
    Set<UUID> affected = state.component(id);
    clearPerformance(affected);
    state.remove(id);
    updateEnvelopes(transport.sample() + 1920);
    loaded.remove(id);
    envelopes.remove(id);
    held.entrySet().removeIf(e -> e.getValue().module.equals(id));
    selections.entrySet().removeIf(e -> e.getValue().module.equals(id));
    changed(affected, transport.sample());
  }

  public void inspect(ServerPlayer player, ModuleEntity module, boolean open) {
    if (!module.valid()) {
      error(player, "quarantined");
      return;
    }
    if (!module.eligible(player)) {
      error(player, "range");
      return;
    }
    if (open) {
      release(player.getUUID());
      inspecting.put(player.getUUID(), module.state().id());
      sessions.put(player.getUUID(), new KeyboardSession(module.state().id()));
    }
    sendView(player, module, open, status(module.state()));
  }

  private String status(ModuleState m) {
    if (state.component(m.id()).stream().anyMatch(id -> !state.get(id).loaded()))
      return "patch_unloaded";
    if (m.kind() == ModuleKind.SPEAKER) {
      if (!m.enabled()) return "disabled";
      if (state.cables().stream()
          .noneMatch(c -> c.destination().equals(m.id()) && c.input() == Port.AUDIO_IN))
        return "missing_audio";
      for (Cable c : state.cables())
        if (c.destination().equals(m.id()) && c.input() == Port.GAIN_IN) {
          Envelope e = envelopes.get(c.source());
          if (e == null || e.value(transport.sample()) == 0) return "released";
        }
    }
    if (m.kind().isOscillator()
        && state.cables().stream()
            .anyMatch(c -> c.destination().equals(m.id()) && c.input() == Port.PITCH_IN))
      return m.kind() == ModuleKind.SQUARE_OSCILLATOR ? "pulse_pitch_connected" : "pitch_connected";
    return "ready";
  }

  private void sendView(ServerPlayer player, ModuleEntity module, boolean open, String status) {
    byte[] bytes =
        StateCodec.encode(
            snapshot(
                state.component(module.state().id()), module.state().id(), transport.sample()));
    if (budgets.bytes(player.getUUID(), System.nanoTime(), bytes.length + 160))
      PacketDistributor.sendToPlayer(
          player,
          new Messages.View(
              bytes,
              module.editable(player),
              open,
              status,
              sessions
                  .computeIfAbsent(
                      player.getUUID(), ignored -> new KeyboardSession(module.state().id()))
                  .id()));
  }

  public void cancel(ServerPlayer player) {
    selections.remove(player.getUUID());
    error(player, "cancelled");
  }

  public void patch(ServerPlayer player, ModuleEntity module, Port port, boolean remove) {
    if (!module.editable(player)) {
      error(player, module.valid() ? "permission" : "quarantined");
      return;
    }
    if (!budgets.edit(player.getUUID(), System.nanoTime(), false)) {
      error(player, "throttled");
      return;
    }
    if (port == null) {
      cancel(player);
      return;
    }
    ModuleState target = module.state();
    try {
      if (remove) {
        // Removing an input is unambiguous; outputs are never bulk-deleted accidentally.
        if (port.output) throw new IllegalArgumentException("remove_at_input");
        Cable cable =
            state.cables().stream()
                .filter(c -> c.destination().equals(target.id()) && c.input() == port)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing_cable"));
        ModuleEntity source = required(player, cable.source());
        Set<UUID> affected = state.component(target.id());
        state.disconnect(
            player.getUUID(),
            player.hasPermissions(2),
            cable,
            source.state().revision(),
            target.revision());
        updateEnvelopes(transport.sample() + 1920);
        changed(affected, transport.sample() + 1920);
        error(player, port == Port.GAIN_IN ? "gain_disconnected" : "removed");
        return;
      }
      Selection selection = selections.get(player.getUUID());
      if (port.output) {
        selections.put(player.getUUID(), new Selection(target.id(), port, target.revision()));
        player.displayClientMessage(
            Component.translatable(
                "modularcraft.selected",
                Component.translatable(
                    "port.modularcraft." + port.name().toLowerCase(java.util.Locale.ROOT)),
                target.x(),
                target.y(),
                target.z()),
            true);
        return;
      }
      if (selection == null) throw new IllegalArgumentException("select_output");
      ModuleEntity source = required(player, selection.module);
      Set<UUID> affected = new HashSet<>(state.component(source.state().id()));
      affected.addAll(state.component(target.id()));
      data.reserveRecord(512);
      state.connect(
          player.getUUID(),
          player.hasPermissions(2),
          new Cable(selection.module, selection.port, target.id(), port),
          selection.revision,
          target.revision());
      selections.remove(player.getUUID());
      updateEnvelopes(transport.sample() + 1920);
      changed(affected, transport.sample() + 1920);
      error(player, "connected");
    } catch (IllegalArgumentException rejected) {
      error(player, key(rejected));
      inspect(player, module, false);
    }
  }

  private ModuleEntity required(ServerPlayer player, UUID id) {
    ModuleEntity module = loaded.get(id);
    if (module == null || !module.valid()) throw new IllegalArgumentException("unloaded");
    if (!module.editable(player)) throw new IllegalArgumentException("permission");
    return module;
  }

  private void control(ServerPlayer player, Messages.Control request) {
    if (player.hasDisconnected()) return;
    if (request.action() == Messages.Action.CLOSE
        || (request.action() == Messages.Action.GATE && request.value() == 0)) {
      release(player.getUUID());
      if (request.action() == Messages.Action.CLOSE) inspecting.remove(player.getUUID());
      return;
    }
    if (!budgets.edit(player.getUUID(), System.nanoTime(), false)) {
      error(player, "throttled");
      return;
    }
    if (request.action() == Messages.Action.REFRESH) {
      broadcast(transport.sample(), null, player);
      return;
    }
    ModuleEntity module = null;
    try {
      if (!data.writable()) throw new IllegalArgumentException("quarantined");
      module = required(player, request.id());
      ModuleState before = module.state();
      if (request.action() == Messages.Action.INSPECT) {
        inspect(player, module, true);
        return;
      }
      state.authorize(player.getUUID(), player.hasPermissions(2), request.id(), request.revision());
      long sample = Math.max(transport.sample() + 1920, Math.max(performanceSample, controlSample));
      Set<UUID> affected = state.component(before.id());
      switch (request.action()) {
        case PARAMETER ->
            state.edit(
                player.getUUID(),
                player.hasPermissions(2),
                before.id(),
                request.revision(),
                request.parameter(),
                request.value());
        case ENABLE -> {
          if (request.value() != 0 && request.value() != 1)
            throw new IllegalArgumentException("range");
          boolean wasActive = active(affected);
          state.enable(
              player.getUUID(),
              player.hasPermissions(2),
              before.id(),
              request.revision(),
              request.value() == 1);
          if (!wasActive && active(affected)) clearVoices(affected);
        }
        case SEQUENCER_START, SEQUENCER_STOP, SEQUENCER_RESET -> {
          if (before.kind() != ModuleKind.EUCLIDEAN_SEQUENCER || request.value() != 0)
            throw new IllegalArgumentException("action");
          for (UUID id : affected)
            if (!state.get(id).loaded()) throw new IllegalArgumentException("patch_unloaded");
          updateEnvelopes(Math.max(0, sample - 1));
          state.touch(player.getUUID(), player.hasPermissions(2), before.id(), request.revision());
          Sequencer sequencer = sequencers.get(before.id());
          if (request.action() == Messages.Action.SEQUENCER_START) sequencer.start(sample);
          else if (request.action() == Messages.Action.SEQUENCER_STOP) sequencer.stop(sample);
          else sequencer.reset(sample);
        }
        case GATE -> {
          if (before.kind() != ModuleKind.ENVELOPE
              || request.value() != 1
              || !before.id().equals(inspecting.get(player.getUUID())))
            throw new IllegalArgumentException("gate_eligibility");
          held.put(player.getUUID(), new Held(before.id(), System.nanoTime() + 750_000_000L));
        }
        default -> throw new IllegalArgumentException("action");
      }
      updateEnvelopes(sample);
      if (request.action() == Messages.Action.PARAMETER
          && request.parameter() == Parameter.OCTAVE) {
        KeyboardPerformance keyboard = keyboards.get(before.id());
        if (keyboard != null
            && keyboard.transpose((int) state.get(before.id()).value(Parameter.OCTAVE)))
          retrigger(before.id(), sample);
      }
      changed(affected, sample);
      for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
        UUID target = inspecting.get(listener.getUUID());
        ModuleEntity inspected = target == null ? null : loaded.get(target);
        if (inspected != null && inspected.eligible(listener))
          sendView(listener, inspected, false, status(inspected.state()));
      }
    } catch (IllegalArgumentException rejected) {
      error(player, key(rejected));
      if (module != null && module.valid()) sendView(player, module, false, key(rejected));
    }
  }

  private void keyboard(ServerPlayer player, Messages.Keyboard request, long received) {
    KeyboardSession session = sessions.get(player.getUUID());
    if (player.hasDisconnected()
        || session == null
        || !session.id().equals(request.session())
        || !session.module().equals(request.id())
        || !session.accept(request.sequence())) return;
    long sample = Math.max(transport.sample() + 1920, performanceSample);
    if (request.cleanup() || request.note() == -1) {
      KeyboardPerformance keyboard = keyboards.get(session.module());
      if (keyboard != null && session.id().equals(keyboard.holder())) {
        sample = session.schedule(sample, received);
        performanceSample = sample;
      }
      releaseKeyboard(player.getUUID(), sample);
      if (request.cleanup()) sessions.remove(player.getUUID());
      return;
    }
    ModuleEntity module = null;
    try {
      if (!data.writable()) throw new IllegalArgumentException("quarantined");
      module = required(player, request.id());
      if (module.state().kind() != ModuleKind.KEYBOARD
          || !request.id().equals(inspecting.get(player.getUUID())))
        throw new IllegalArgumentException("gate_eligibility");
      state.authorize(player.getUUID(), player.hasPermissions(2), request.id(), request.revision());
      if (!budgets.edit(player.getUUID(), System.nanoTime(), false))
        throw new IllegalArgumentException("throttled");
      KeyboardPerformance keyboard =
          keyboards.computeIfAbsent(
              request.id(),
              ignored ->
                  new KeyboardPerformance((int) state.get(request.id()).value(Parameter.OCTAVE)));
      if (request.renew()) {
        keyboard.renew(session.id(), System.nanoTime());
        return;
      }
      keyboard.play(
          session.id(),
          request.note(),
          request.absolute(),
          (int) module.state().value(Parameter.OCTAVE),
          System.nanoTime());
      sample = session.schedule(sample, received);
      performanceSample = sample;
      updateEnvelopes(sample);
      retrigger(request.id(), sample);
      changed(state.component(request.id()), sample);
      sendView(player, module, false, status(module.state()));
    } catch (IllegalArgumentException rejected) {
      releaseKeyboard(player.getUUID(), sample);
      error(player, key(rejected));
      if (module != null && module.valid()) sendView(player, module, false, key(rejected));
    }
  }

  private void releaseKeyboard(UUID actor, long sample) {
    KeyboardSession session = sessions.get(actor);
    if (session == null) return;
    KeyboardPerformance keyboard = keyboards.get(session.module());
    if (keyboard != null && keyboard.release(session.id()) && state.contains(session.module())) {
      updateEnvelopes(sample);
      changed(state.component(session.module()), sample);
      ServerPlayer player = server.getPlayerList().getPlayer(actor);
      ModuleEntity module = loaded.get(session.module());
      if (player != null && module != null && module.eligible(player))
        sendView(player, module, false, status(module.state()));
    }
  }

  private void retrigger(UUID keyboard, long requestedSample) {
    long sample = Math.max(requestedSample, Math.max(performanceSample, controlSample));
    for (Cable cable : state.cables()) {
      if (!cable.source().equals(keyboard) || cable.input() != Port.GATE_IN) continue;
      UUID id = cable.destination();
      ModuleEntity entity = loaded.get(id);
      boolean manual = held.values().stream().anyMatch(h -> h.module.equals(id));
      boolean redstone =
          entity != null && entity.getLevel().hasNeighborSignal(entity.getBlockPos());
      Envelope envelope = envelopes.get(id);
      if (envelope != null && !manual && !redstone) envelope.retrigger(sample);
    }
  }

  private boolean active(Set<UUID> ids) {
    boolean speaker = false;
    for (UUID id : ids) {
      ModuleState m = state.get(id);
      if (!m.loaded()) return false;
      speaker |= m.enabled();
    }
    return speaker;
  }

  private void advanceControls(long sample) {
    for (var entry : timelines.entrySet()) {
      int allowed =
          Math.max(
              0, Math.min(transitionTokens, timelineTokens.getOrDefault(entry.getKey(), 1024)));
      int used = entry.getValue().advanceTo(sample, allowed);
      transitionTokens = Math.max(0, transitionTokens - used);
      timelineTokens.compute(
          entry.getKey(),
          (id, remaining) -> Math.max(0, (remaining == null ? 1024 : remaining) - used));
      if (entry.getValue().overloaded() && state.contains(entry.getKey())) {
        state.suspend(state.component(entry.getKey()));
        pendingSuspensions.addAll(state.component(entry.getKey()));
        controlsDirty = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
          error(player, "sequencer_overload");
      }
    }
  }

  private void updateEnvelopes(long requestedSample) {
    long sample = Math.max(requestedSample, Math.max(performanceSample, controlSample));
    advanceControls(Math.max(controlSample, sample - 1));
    boolean rebuild = controlsDirty || controlRevision != state.revision();
    Set<UUID> retained = new HashSet<>();
    for (Set<UUID> ids : state.components()) {
      UUID root = ids.stream().min(UUID::compareTo).orElseThrow();
      retained.add(root);
      boolean audible = active(ids);
      boolean allLoaded = ids.stream().allMatch(id -> state.get(id).loaded());
      for (UUID id : ids) {
        ModuleState m = state.get(id);
        if (m.kind() == ModuleKind.EUCLIDEAN_SEQUENCER) {
          Sequencer seq = sequencers.get(id);
          if (seq == null) {
            seq = new Sequencer(SequencerSettings.from(m), sample);
            sequencers.put(id, seq);
            rebuild = true;
          } else if (rebuild) seq.configure(SequencerSettings.from(m), sample);
          if (!allLoaded) seq.stop(sample);
        }
        if (m.kind() != ModuleKind.ENVELOPE) continue;
        if (!audible) {
          if (envelopes.remove(id) != null) rebuild = true;
          baseGates.remove(id);
          continue;
        }
        if (!envelopes.containsKey(id)) {
          envelopes.put(id, new Envelope());
          rebuild = true;
        }
        Envelope envelope = envelopes.get(id);
        envelope.configure(
            m.value(Parameter.ATTACK),
            m.value(Parameter.DECAY),
            m.value(Parameter.SUSTAIN) / 100,
            m.value(Parameter.RELEASE),
            sample);
        ModuleEntity entity = loaded.get(id);
        boolean redstone =
            entity != null && entity.getLevel().hasNeighborSignal(entity.getBlockPos());
        boolean manual = held.values().stream().anyMatch(h -> h.module.equals(id));
        baseGates.put(id, manual || redstone);
      }
      ControlTimeline timeline = timelines.get(root);
      if (rebuild || timeline == null) {
        List<ModuleState> modules = ids.stream().map(state::get).toList();
        List<Cable> cables = state.cables().stream().filter(c -> ids.contains(c.source())).toList();
        Map<UUID, KeyboardState> notes = new HashMap<>();
        for (UUID id : ids) if (keyboards.containsKey(id)) notes.put(id, keyboards.get(id).state());
        ControlTimeline next =
            new ControlTimeline(modules, cables, sequencers, envelopes, baseGates, notes, sample);
        next.rewire(timeline, sample);
        timeline = next;
        timelines.put(root, timeline);
      }
      for (UUID id : ids) {
        timeline.base(id, baseGates.getOrDefault(id, false));
        timeline.keyboard(id, keyboards.containsKey(id) && keyboards.get(id).state().gate());
      }
      int allowed =
          Math.max(0, Math.min(transitionTokens, timelineTokens.getOrDefault(root, 1024)));
      int used = timeline.refresh(sample);
      if (used > allowed) {
        timeline.suspend(sample);
        state.suspend(ids);
        pendingSuspensions.addAll(ids);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
          error(player, "sequencer_overload");
      }
      transitionTokens = Math.max(0, transitionTokens - used);
      timelineTokens.compute(
          root, (id, remaining) -> Math.max(0, (remaining == null ? 1024 : remaining) - used));
    }
    timelines.keySet().retainAll(retained);
    timelineTokens.keySet().retainAll(retained);
    controlRevision = state.revision();
    controlsDirty = false;
    controlSample = sample;
  }

  private void clearVoices(Set<UUID> ids) {
    ids.forEach(envelopes::remove);
    ids.forEach(baseGates::remove);
    ids.forEach(keyboards::remove);
    held.entrySet().removeIf(e -> ids.contains(e.getValue().module));
    controlsDirty = true;
  }

  private void clearPerformance(Set<UUID> ids) {
    clearVoices(ids);
    ids.forEach(sequencers::remove);
    timelines.entrySet().removeIf(entry -> ids.contains(entry.getKey()));
  }

  private void release(UUID player) {
    releaseKeyboard(player, transport.sample() + 1920);
    Held previous = held.remove(player);
    if (previous != null && state.contains(previous.module)) {
      long sample = transport.sample() + 1920;
      updateEnvelopes(sample);
      changed(state.component(previous.module), sample);
    }
  }

  private void leave(ServerPlayer player) {
    release(player.getUUID());
    selections.remove(player.getUUID());
    inspecting.remove(player.getUUID());
    sessions.remove(player.getUUID());
    known.remove(player.getUUID());
    lastClock.remove(player.getUUID());
    budgets.forget(player.getUUID());
    lastNotice.remove(player.getUUID());
  }

  private PatchSnapshot snapshot(Set<UUID> ids, UUID id, long sample) {
    sample = Math.max(sample, Math.max(controlSample, performanceSample));
    if (controlsDirty || controlRevision != state.revision()) updateEnvelopes(sample);
    advanceControls(sample);
    controlSample = sample;
    List<ModuleState> modules = state.modules().stream().filter(m -> ids.contains(m.id())).toList();
    List<Cable> cables = state.cables().stream().filter(c -> ids.contains(c.source())).toList();
    Map<UUID, Envelope.Checkpoint> checkpoints = new HashMap<>();
    for (UUID member : ids)
      if (envelopes.containsKey(member))
        checkpoints.put(member, envelopes.get(member).checkpoint(sample));
    Map<UUID, KeyboardState> notes = new HashMap<>();
    for (ModuleState module : modules)
      if (module.kind() == ModuleKind.KEYBOARD)
        notes.put(
            module.id(),
            keyboards.containsKey(module.id())
                ? keyboards.get(module.id()).state()
                : new KeyboardState(
                    KeyboardState.note((int) module.value(Parameter.OCTAVE), 0), false));
    Map<UUID, Sequencer.Checkpoint> rhythms = new HashMap<>();
    Map<UUID, Boolean> bases = new HashMap<>();
    for (UUID member : ids) {
      if (sequencers.containsKey(member))
        rhythms.put(member, sequencers.get(member).checkpoint(sample));
      if (checkpoints.containsKey(member)) bases.put(member, baseGates.getOrDefault(member, false));
    }
    return new PatchSnapshot(
        id,
        state.revision(),
        sequence,
        sample,
        modules,
        cables,
        checkpoints,
        notes,
        rhythms,
        bases);
  }

  private void changed(Set<UUID> affected, long sample) {
    sequence++;
    data.setDirty();
    for (UUID id : affected) {
      ModuleEntity entity = loaded.get(id);
      if (entity != null && state.contains(id)) entity.accepted(state.get(id));
    }
    broadcast(sample, affected, null);
  }

  private void broadcast(long requestedSample, Set<UUID> affected, ServerPlayer only) {
    long sample = Math.max(requestedSample, Math.max(performanceSample, controlSample));
    List<Set<UUID>> components = state.components();
    Map<UUID, byte[]> encoded = new HashMap<>();
    for (ServerPlayer player : only == null ? server.getPlayerList().getPlayers() : List.of(only)) {
      Set<UUID> previous = known.getOrDefault(player.getUUID(), Set.of());
      List<UUID> roster = new ArrayList<>();
      boolean overflow = false;
      for (Set<UUID> ids : components) {
        boolean near =
            ids.stream()
                .map(state::get)
                .anyMatch(
                    m ->
                        m.dimension().equals(player.level().dimension().location().toString())
                            && player.distanceToSqr(m.x() + .5, m.y() + .5, m.z() + .5) < 64 * 64);
        if (!near) continue;
        UUID id = ids.stream().min(UUID::compareTo).orElseThrow();
        roster.add(id);
        if (affected == null
            || !previous.contains(id)
            || ids.stream().anyMatch(affected::contains)) {
          byte[] bytes =
              encoded.computeIfAbsent(id, ignored -> StateCodec.encode(snapshot(ids, id, sample)));
          if (!budgets.bytes(player.getUUID(), System.nanoTime(), bytes.length + 32)) {
            overflow = true;
            break;
          }
          PacketDistributor.sendToPlayer(player, new Messages.Patch(bytes));
        }
      }
      if (overflow) {
        known.remove(player.getUUID());
        // With no remaining bandwidth, the client's one-second roster timeout silences it.
        if (budgets.recovery(player.getUUID(), System.nanoTime()))
          PacketDistributor.sendToPlayer(
              player,
              new Messages.Roster(
                  sequence, sample, player.level().dimension().location().toString(), List.of()));
      } else if (budgets.bytes(player.getUUID(), System.nanoTime(), roster.size() * 16 + 160)) {
        known.put(player.getUUID(), Set.copyOf(roster));
        PacketDistributor.sendToPlayer(
            player,
            new Messages.Roster(
                sequence,
                sample,
                player.level().dimension().location().toString(),
                List.copyOf(roster)));
      }
    }
  }

  public void clock(ServerPlayer player, Messages.Clock message) {
    long now = System.nanoTime();
    if (now - lastClock.getOrDefault(player.getUUID(), 0L) < 500_000_000L) return;
    lastClock.put(player.getUUID(), now);
    if (budgets.bytes(player.getUUID(), now, 64))
      PacketDistributor.sendToPlayer(
          player, new Messages.Clock(message.clientNanos(), transport.sample()));
  }

  private void tick() {
    transitionTokens = 4096;
    timelineTokens.clear();
    long start = System.nanoTime();
    while (System.nanoTime() - start < 1_500_000L) {
      Queued request = queue.poll();
      if (request == null) break;
      if (request.keyboard != null) keyboard(request.player, request.keyboard, request.received);
      else control(request.player, request.message);
    }
    for (UUID actor : new ArrayList<>(held.keySet())) {
      Held h = held.get(actor);
      ServerPlayer player = server.getPlayerList().getPlayer(actor);
      ModuleEntity module = loaded.get(h.module);
      if (player == null || module == null || !module.editable(player) || h.expires < start)
        release(actor);
    }
    long sample = transport.sample() + 1920;
    for (var entry : sessions.entrySet()) {
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      ModuleEntity module = loaded.get(entry.getValue().module());
      KeyboardPerformance keyboard = keyboards.get(entry.getValue().module());
      if (keyboard == null) continue;
      if (player == null || module == null || !module.editable(player))
        releaseKeyboard(entry.getKey(), sample);
      else if (keyboard.expire(start)) {
        updateEnvelopes(sample);
        changed(state.component(entry.getValue().module()), sample);
      }
    }
    synchronized (keyboardOverload) {
      for (UUID actor : keyboardOverload) {
        releaseKeyboard(actor, sample);
        sessions.remove(actor);
        ServerPlayer player = server.getPlayerList().getPlayer(actor);
        if (player != null) error(player, "keyboard_overload");
      }
      keyboardOverload.clear();
    }
    Map<UUID, Boolean> gates = new HashMap<>(baseGates);
    updateEnvelopes(sample);
    Set<UUID> changedGates = new HashSet<>();
    baseGates.forEach(
        (id, gate) -> {
          if (gate != gates.getOrDefault(id, false)) changedGates.add(id);
        });
    if (!pendingSuspensions.isEmpty()) {
      changedGates.addAll(pendingSuspensions);
      pendingSuspensions.clear();
    }
    if (!changedGates.isEmpty()) changed(changedGates, sample);
    if (start - lastBroadcast >= 500_000_000L) {
      lastBroadcast = start;
      broadcast(sample, null, null);
      synchronized (throttled) {
        for (UUID id : throttled) {
          ServerPlayer p = server.getPlayerList().getPlayer(id);
          if (p != null) error(p, "throttled");
        }
        throttled.clear();
      }
    }
    tickNanos = System.nanoTime() - start;
    maxTickNanos = Math.max(maxTickNanos, tickNanos);
  }

  public long lastTickNanos() {
    return tickNanos;
  }

  public long maxTickNanos() {
    return maxTickNanos;
  }

  /** Server-side inspection token for integration fixtures; never grants edit authority. */
  public UUID keyboardSession(ServerPlayer player) {
    KeyboardSession session = sessions.get(player.getUUID());
    return session == null ? null : session.id();
  }

  /** Current immutable checkpoint for diagnostics and host integration tests. */
  public PatchSnapshot currentPatch(UUID id) {
    return snapshot(state.component(id), id, transport.sample());
  }

  public WorldState state() {
    return state;
  }

  private static String key(IllegalArgumentException error) {
    String message = error.getMessage();
    return message == null ? "invalid" : message.split(":", 2)[0];
  }

  private void error(ServerPlayer player, String key) {
    long now = System.nanoTime();
    if (now - lastNotice.getOrDefault(player.getUUID(), 0L) < 100_000_000L
        || !budgets.bytes(player.getUUID(), now, 256)) return;
    lastNotice.put(player.getUUID(), now);
    player.displayClientMessage(Component.translatable("modularcraft.error." + key), true);
  }
}
