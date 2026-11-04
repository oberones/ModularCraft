package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.client.mixin.SoundEngineAccess;
import com.malevolentgods.modularcraft.client.mixin.SoundManagerAccess;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Scene;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.dsp.AudioEngine;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.network.Messages;
import com.malevolentgods.modularcraft.transport.ClockSync;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = ModularCraft.ID, dist = Dist.CLIENT)
public final class ClientRuntime {
  public static final KeyMapping MUTE =
      new KeyMapping(
          "key.modularcraft.mute",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_M,
          "key.categories.modularcraft");
  public static final KeyMapping SETTINGS =
      new KeyMapping(
          "key.modularcraft.settings",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_K,
          "key.categories.modularcraft");
  public static final LocalSettings PREFERENCES = new LocalSettings();
  private static final Map<UUID, PatchSnapshot> patches = new HashMap<>();
  private static final Map<UUID, PatchSnapshot> staging = new HashMap<>();
  private static ThreadPoolExecutor preparation;
  private static AudioEngine engine;
  private static AudioOutput output;
  private static ClockSync clock = new ClockSync();
  private static String dimension = "";
  private static long lastPing, lastRoster, lastSequence, lastRecovery;
  private static boolean paused;
  private static volatile String diagnostic = "ready";

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public ClientRuntime(IEventBus bus) {
    bus.addListener(
        (RegisterKeyMappingsEvent event) -> {
          event.register(MUTE);
          event.register(SETTINGS);
        });
    bus.addListener(
        (RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    manager -> Minecraft.getInstance().execute(() -> recover("reloading"))));
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clear());
    NeoForge.EVENT_BUS.addListener(
        (ScreenEvent.Init.Post event) -> {
          if (event.getScreen() instanceof TitleScreen || event.getScreen() instanceof PauseScreen)
            event.addListener(
                Button.builder(
                        Component.translatable("modularcraft.local_audio"),
                        button ->
                            Minecraft.getInstance()
                                .setScreen(new AudioSettingsScreen(event.getScreen())))
                    .bounds(6, 6, 132, 20)
                    .build());
        });
    NeoForge.EVENT_BUS.addListener(
        (ScreenEvent.KeyPressed.Pre event) -> {
          if (MUTE.matches(event.getKeyCode(), event.getScanCode())) {
            toggleMute();
            event.setCanceled(true);
          } else if (SETTINGS.matches(event.getKeyCode(), event.getScanCode())) {
            Minecraft.getInstance().setScreen(new AudioSettingsScreen(event.getScreen()));
            event.setCanceled(true);
          }
        });
    NeoForge.EVENT_BUS.addListener(CableRenderer::render);
    NeoForge.EVENT_BUS.addListener(CableRenderer::hud);
    Messages.patchReceiver = ClientRuntime::receive;
    Messages.rosterReceiver = ClientRuntime::roster;
    Messages.clockReceiver =
        message -> clock.observe(message.clientNanos(), System.nanoTime(), message.sample());
    Messages.viewReceiver =
        message -> {
          try {
            PatchSnapshot snapshot = StateCodec.decode(message.bytes());
            ModuleState module =
                snapshot.modules().stream()
                    .filter(m -> m.id().equals(snapshot.id()))
                    .findFirst()
                    .orElseThrow();
            Minecraft mc = Minecraft.getInstance();
            if (module.kind() == ModuleKind.KEYBOARD) {
              KeyboardState note =
                  snapshot
                      .keyboards()
                      .getOrDefault(
                          module.id(),
                          new KeyboardState(
                              KeyboardState.note((int) module.value(Parameter.OCTAVE), 0), false));
              if (message.open())
                mc.setScreen(
                    new KeyboardScreen(
                        module, note, message.editable(), message.status(), message.session()));
              else if (mc.screen instanceof KeyboardScreen screen
                  && screen.identity().equals(module.id())
                  && screen.session().equals(message.session()))
                screen.accept(module, note, message.editable(), message.status());
            } else if (message.open())
              mc.setScreen(new ModuleScreen(module, message.editable(), message.status()));
            else if (mc.screen instanceof ModuleScreen screen
                && screen.identity().equals(module.id()))
              screen.accept(module, message.editable(), message.status());
          } catch (IllegalArgumentException invalid) {
            recover("invalid_snapshot");
          }
        };
  }

  public static List<PatchSnapshot> patches() {
    return List.copyOf(patches.values());
  }

  public static String audioStats() {
    return output == null ? "no output: " + diagnostic() : output.stats();
  }

  public static String diagnostic() {
    return PREFERENCES.muted ? "muted" : diagnostic;
  }

  public static void toggleMute() {
    PREFERENCES.muted = !PREFERENCES.muted;
    PREFERENCES.save();
    recover(PREFERENCES.muted ? "muted" : "resync");
  }

  public static void send(
      ModuleState state, Messages.Action action, Parameter parameter, double value) {
    if (Minecraft.getInstance().getConnection() != null)
      PacketDistributor.sendToServer(
          new Messages.Control(state.id(), state.revision(), action, parameter, value));
  }

  private static void receive(Messages.Patch message) {
    try {
      PatchSnapshot patch = StateCodec.decode(message.bytes());
      if (staging.size() >= 256 && !staging.containsKey(patch.id()))
        throw new IllegalArgumentException("snapshot_count");
      PatchSnapshot previous = staging.getOrDefault(patch.id(), patches.get(patch.id()));
      if (previous == null || patch.sequence() >= previous.sequence())
        staging.put(patch.id(), patch);
    } catch (IllegalArgumentException invalid) {
      recover("invalid_snapshot");
    }
  }

  // Borrowed client singleton; never close the host.
  @SuppressWarnings("PMD.CloseResource")
  private static void roster(Messages.Roster roster) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null
        || !mc.level.dimension().location().toString().equals(roster.dimension())
        || roster.sequence() < lastSequence) return;
    Map<UUID, PatchSnapshot> next = new HashMap<>();
    for (UUID id : roster.patches()) {
      PatchSnapshot patch = staging.getOrDefault(id, patches.get(id));
      if (patch == null) {
        recover("revision_gap");
        return;
      }
      next.put(id, patch);
    }
    try {
      Scene.validate(next.values(), roster.dimension());
    } catch (IllegalArgumentException invalid) {
      staging.clear();
      recover("invalid_snapshot");
      return;
    }
    lastSequence = roster.sequence();
    lastRoster = System.nanoTime();
    patches.clear();
    patches.putAll(next);
    if (Minecraft.getInstance().screen instanceof KeyboardScreen screen)
      for (PatchSnapshot patch : patches.values()) {
        KeyboardState note = patch.keyboards().get(screen.identity());
        if (note != null) screen.performance(note);
      }
    staging.clear();
    if (!clock.ready()) clock.observe(lastRoster, lastRoster, Math.max(0, roster.sample() - 1920));
    prepare(roster.sample(), roster.sequence());
  }

  private static void prepare(long sample, long sequence) {
    if (engine == null || preparation == null || PREFERENCES.muted || paused) return;
    List<PatchSnapshot> active = new ArrayList<>();
    for (PatchSnapshot patch : patches.values())
      if (patch.modules().stream().allMatch(ModuleState::loaded)
          && patch.modules().stream().anyMatch(ModuleState::enabled)) active.add(patch);
    AudioEngine destination = engine;
    List<PatchSnapshot> immutable = List.copyOf(active);
    try {
      preparation.execute(
          () -> {
            try {
              if (!destination.submit(sample, sequence, new RenderGraph(immutable)))
                diagnostic = "overload";
            } catch (IllegalArgumentException invalid) {
              diagnostic = "invalid_snapshot";
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException overload) {
      diagnostic = "overload";
    }
  }

  private static void createEngine() {
    engine = new AudioEngine();
    preparation =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8),
            r -> {
              Thread t = new Thread(r, "ModularCraft graph preparation");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
    prepare(clock.sample(System.nanoTime()), lastSequence);
  }

  private static void stopAudio() {
    if (output != null) {
      output.close();
      output = null;
    }
    engine = null;
    if (preparation != null) {
      preparation.shutdownNow();
      preparation = null;
    }
  }

  public static void recover(String reason) {
    stopAudio();
    diagnostic = reason;
    lastRecovery = 0;
  }

  private static void clear() {
    CableRenderer.clear();
    stopAudio();
    patches.clear();
    staging.clear();
    clock = new ClockSync();
    dimension = "";
    lastSequence = 0;
    lastRoster = 0;
    lastPing = 0;
    diagnostic = "ready";
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  private static void tick() {
    Minecraft mc = Minecraft.getInstance();
    while (MUTE.consumeClick()) toggleMute();
    while (SETTINGS.consumeClick()) mc.setScreen(new AudioSettingsScreen(mc.screen));
    if (mc.level == null || mc.player == null) {
      if (!dimension.isEmpty()) clear();
      return;
    }
    String current = mc.level.dimension().location().toString();
    if (!current.equals(dimension)) {
      clear();
      dimension = current;
    }
    long now = System.nanoTime();
    if (mc.isPaused()) {
      if (!paused) {
        paused = true;
        stopAudio();
      }
      return;
    }
    if (paused) {
      paused = false;
      clock = new ClockSync();
      lastPing = 0;
      recover("resync");
    }
    if (now - lastPing >= 1_000_000_000L) {
      lastPing = now;
      PacketDistributor.sendToServer(new Messages.Clock(now, 0));
    }
    if (engine != null
        && (engine.failed() || output != null && (output.failed() || output.closed())))
      recover("device_recovery");
    if (diagnostic.equals("overload") || diagnostic.equals("invalid_snapshot")) recover(diagnostic);
    if (lastRoster != 0 && now - lastRoster > 1_000_000_000L) {
      stopAudio();
      diagnostic = "resync";
    }
    if (!PREFERENCES.muted && engine == null && clock.ready()) {
      createEngine();
      var sound = ((SoundManagerAccess) mc.getSoundManager()).modularcraft$engine();
      if (((SoundEngineAccess) sound).modularcraft$loaded()) {
        output = new AudioOutput(sound, engine, clock);
        output.start();
        diagnostic = "ready";
      } else {
        stopAudio();
        diagnostic = "device_recovery";
      }
    }
    if (output != null)
      output.listener(
          new RenderGraph.Listener(
              current,
              mc.player.getEyePosition().x,
              mc.player.getEyePosition().y,
              mc.player.getEyePosition().z,
              mc.player.getYRot(),
              PREFERENCES.volume * mc.options.getSoundSourceVolume(SoundSource.RECORDS)));
    if (now - lastRecovery >= 500_000_000L && (lastRoster == 0 || !diagnostic.equals("ready"))) {
      lastRecovery = now;
      PacketDistributor.sendToServer(
          new Messages.Control(new UUID(0, 0), 0, Messages.Action.REFRESH, Parameter.LEVEL, 0));
    }
  }
}
