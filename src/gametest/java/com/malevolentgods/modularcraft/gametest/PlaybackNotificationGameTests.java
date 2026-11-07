package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.malevolentgods.modularcraft.network.Messages;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Capture server packets synchronously, before a periodic full broadcast can mask missed updates.
 */
@GameTestHolder(ModularCraft.ID)
@PrefixGameTestTemplate(false)
public final class PlaybackNotificationGameTests {
  private PlaybackNotificationGameTests() {}

  // Minecraft owns the borrowed server and the recording connection has no open channel.
  @SuppressWarnings("PMD.CloseResource")
  @GameTest(
      template = "empty",
      templateNamespace = ModularCraft.ID,
      batch = "playback_notifications")
  public static void admissionAndSuspensionRefreshSnapshotsAndInspectors(GameTestHelper h)
      throws ReflectiveOperationException {
    var server = h.getLevel().getServer();
    var runtime = ServerRuntime.get(server);
    var state = runtime.state();
    List<CustomPacketPayload> packets = new ArrayList<>();
    var player =
        new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "PlaybackObserver"));
    player.connection =
        new ServerGamePacketListenerImpl(
            server,
            new Connection(PacketFlow.SERVERBOUND),
            player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
          @Override
          public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom)
              packets.add(custom.payload());
          }
        };
    // The player is online only during this synchronous test; no login or network channel is
    // needed.
    List<ServerPlayer> players = players(server.getPlayerList());
    List<ModuleState> previous = state.modules().stream().filter(ModuleState::loaded).toList();
    List<ModuleEntity> speakers = new ArrayList<>();
    players.add(player);
    try {
      for (ModuleState m : previous) state.loaded(m.id(), false);
      for (int i = 0; i < 5; i++) {
        BlockPos pos = h.absolutePos(new BlockPos(i + 1, 1, 1));
        h.getLevel()
            .setBlock(
                pos, ModularCraft.MODULES.get(ModuleKind.SPEAKER).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) h.getLevel().getBlockEntity(pos);
        entity.initialize(player.getUUID());
        speakers.add(entity);
        state.refreshPlayback();
      }
      ModuleEntity waiting = speakers.get(4), active = speakers.getFirst();
      player.setPos(waiting.getBlockPos().getCenter());
      runtime.inspect(player, waiting, true);
      h.assertTrue(
          view(packets).status().equals("speaker_waiting"), "Panel starts capacity-waiting");
      control(
          runtime,
          player,
          new Messages.Control(
              waiting.state().id(), 0, Messages.Action.REFRESH, Parameter.VOLUME, 0));
      packets.clear();
      var m = state.get(active.state().id());
      control(
          runtime,
          player,
          new Messages.Control(m.id(), m.revision(), Messages.Action.ENABLE, Parameter.VOLUME, 0));
      UUID admitted = waiting.state().id();
      h.assertTrue(
          packets.stream()
              .filter(Messages.Patch.class::isInstance)
              .map(Messages.Patch.class::cast)
              .map(p -> StateCodec.decode(p.bytes()))
              .flatMap(p -> p.modules().stream())
              .anyMatch(s -> s.id().equals(admitted) && s.enabled()),
          "Known waiting patch gets immediate admission snapshot");
      h.assertTrue(!view(packets).status().equals("speaker_waiting"), "Panel leaves waiting state");
      packets.clear();
      // Automatic load admission, without any edit or inspect request.
      runtime.unload(active.state().id());
      runtime.unload(admitted);
      runtime.attach(waiting);
      tick(runtime);
      h.assertTrue(
          view(packets).status().equals("missing_audio"),
          "Automatic admission refreshes panel status");
      assertCurrentView(h, runtime, admitted, view(packets));
      packets.clear();
      // Exercise the same pending-change path used by the timeline overload guard.
      state.suspend(state.component(admitted));
      pending(runtime).add(admitted);
      tick(runtime);
      Messages.View suspended = view(packets);
      h.assertTrue(
          suspended.status().equals("speaker_suspended"), "Overload refreshes panel status");
      assertCurrentView(h, runtime, admitted, suspended);
      var displayed =
          StateCodec.decode(suspended.bytes()).modules().stream()
              .filter(s -> s.id().equals(admitted))
              .findFirst()
              .orElseThrow();
      control(
          runtime,
          player,
          new Messages.Control(
              admitted, displayed.revision(), Messages.Action.ENABLE, Parameter.VOLUME, 1));
      h.assertTrue(
          state.get(admitted).enabled(),
          "Re-enable accepts the revision delivered to the open panel");
    } finally {
      players.remove(player);
      for (ModuleEntity speaker : speakers)
        h.getLevel().setBlock(speaker.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
      for (ModuleState m : previous) state.loaded(m.id(), true);
      state.refreshPlayback();
    }
    h.succeed();
  }

  private static Messages.View view(List<CustomPacketPayload> packets) {
    return packets.stream()
        .filter(Messages.View.class::isInstance)
        .map(Messages.View.class::cast)
        .reduce((first, last) -> last)
        .orElseThrow(() -> new AssertionError("No inspector View delivered"));
  }

  private static void assertCurrentView(
      GameTestHelper h, ServerRuntime runtime, UUID id, Messages.View view) {
    var displayed =
        StateCodec.decode(view.bytes()).modules().stream()
            .filter(m -> m.id().equals(id))
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        displayed.revision() == runtime.state().get(id).revision(),
        "Panel has current edit revision");
  }

  private static void control(ServerRuntime runtime, ServerPlayer player, Messages.Control control)
      throws ReflectiveOperationException {
    var method =
        ServerRuntime.class.getDeclaredMethod(
            "control", ServerPlayer.class, Messages.Control.class);
    method.setAccessible(true);
    method.invoke(runtime, player, control);
  }

  private static void tick(ServerRuntime runtime) throws ReflectiveOperationException {
    var broadcast = ServerRuntime.class.getDeclaredField("lastBroadcast");
    broadcast.setAccessible(true);
    long previous = broadcast.getLong(runtime);
    try {
      broadcast.setLong(runtime, System.nanoTime());
      var method = ServerRuntime.class.getDeclaredMethod("tick");
      method.setAccessible(true);
      method.invoke(runtime);
    } finally {
      broadcast.setLong(runtime, previous);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<ServerPlayer> players(PlayerList list) throws ReflectiveOperationException {
    var field = PlayerList.class.getDeclaredField("players");
    field.setAccessible(true);
    return (List<ServerPlayer>) field.get(list);
  }

  @SuppressWarnings("unchecked")
  private static Set<UUID> pending(ServerRuntime runtime) throws ReflectiveOperationException {
    var field = ServerRuntime.class.getDeclaredField("pendingSuspensions");
    field.setAccessible(true);
    return (Set<UUID>) field.get(runtime);
  }
}
