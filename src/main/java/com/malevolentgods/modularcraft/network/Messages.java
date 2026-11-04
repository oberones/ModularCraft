package com.malevolentgods.modularcraft.network;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class Messages {
  private Messages() {}

  public enum Action {
    PARAMETER,
    ENABLE,
    GATE,
    CLOSE,
    REFRESH
  }

  // Installed only by the client entrypoint; common loading resolves no client classes.
  public static Consumer<Patch> patchReceiver = ignored -> {};
  public static Consumer<Roster> rosterReceiver = ignored -> {};
  public static Consumer<View> viewReceiver = ignored -> {};
  public static Consumer<Clock> clockReceiver = ignored -> {};

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
    return new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(ModularCraft.ID, path));
  }

  public record Control(UUID id, long revision, Action action, Parameter parameter, double value)
      implements CustomPacketPayload {
    public static final Type<Control> TYPE = Messages.type("control");
    public static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeUUID(v.id);
              b.writeLong(v.revision);
              b.writeEnum(v.action);
              b.writeEnum(v.parameter);
              b.writeDouble(v.value);
            },
            b -> {
              if (b.readableBytes() > 4096) throw new IllegalArgumentException("edit_size");
              Control request =
                  new Control(
                      b.readUUID(),
                      b.readLong(),
                      b.readEnum(Action.class),
                      b.readEnum(Parameter.class),
                      b.readDouble());
              if (b.readableBytes() != 0
                  || request.revision() < 0
                  || !Double.isFinite(request.value()))
                throw new IllegalArgumentException("malformed_edit");
              return request;
            });

    @Override
    public Type<Control> type() {
      return TYPE;
    }
  }

  public record Patch(byte[] bytes) implements CustomPacketPayload {
    public static final Type<Patch> TYPE = Messages.type("patch");
    public static final StreamCodec<RegistryFriendlyByteBuf, Patch> CODEC =
        StreamCodec.of(
            (b, v) -> b.writeByteArray(v.bytes),
            b -> new Patch(b.readByteArray(StateCodec.MAX_PATCH)));

    @Override
    public Type<Patch> type() {
      return TYPE;
    }
  }

  public record Roster(long sequence, long sample, String dimension, List<UUID> patches)
      implements CustomPacketPayload {
    public static final Type<Roster> TYPE = Messages.type("roster");
    public static final StreamCodec<RegistryFriendlyByteBuf, Roster> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeLong(v.sequence);
              b.writeLong(v.sample);
              b.writeUtf(v.dimension, 128);
              b.writeVarInt(v.patches.size());
              for (UUID id : v.patches) b.writeUUID(id);
            },
            b -> {
              long sequence = b.readLong(), sample = b.readLong();
              String dimension = b.readUtf(128);
              int count = b.readVarInt();
              if (count < 0 || count > 256 || sequence < 0 || sample < 0)
                throw new IllegalArgumentException("roster");
              java.util.ArrayList<UUID> ids = new java.util.ArrayList<>(count);
              for (int i = 0; i < count; i++) ids.add(b.readUUID());
              return new Roster(sequence, sample, dimension, List.copyOf(ids));
            });

    @Override
    public Type<Roster> type() {
      return TYPE;
    }
  }

  public record View(byte[] bytes, boolean editable, boolean open, String status)
      implements CustomPacketPayload {
    public static final Type<View> TYPE = Messages.type("view");
    public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeByteArray(v.bytes);
              b.writeBoolean(v.editable);
              b.writeBoolean(v.open);
              b.writeUtf(v.status, 128);
            },
            b ->
                new View(
                    b.readByteArray(StateCodec.MAX_PATCH),
                    b.readBoolean(),
                    b.readBoolean(),
                    b.readUtf(128)));

    @Override
    public Type<View> type() {
      return TYPE;
    }
  }

  public record Clock(long clientNanos, long sample) implements CustomPacketPayload {
    public static final Type<Clock> TYPE = Messages.type("clock");
    public static final StreamCodec<RegistryFriendlyByteBuf, Clock> CODEC =
        StreamCodec.of(
            (b, v) -> {
              b.writeLong(v.clientNanos);
              b.writeLong(v.sample);
            },
            b -> new Clock(b.readLong(), b.readLong()));

    @Override
    public Type<Clock> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar
        .executesOn(HandlerThread.NETWORK)
        .playToServer(
            Control.TYPE,
            Control.CODEC,
            (message, context) -> {
              if (context.player() instanceof ServerPlayer player)
                ServerRuntime.enqueue(player, message);
            });
    registrar.playToClient(
        Patch.TYPE, Patch.CODEC, (message, context) -> patchReceiver.accept(message));
    registrar.playToClient(
        Roster.TYPE, Roster.CODEC, (message, context) -> rosterReceiver.accept(message));
    registrar.playToClient(
        View.TYPE, View.CODEC, (message, context) -> viewReceiver.accept(message));
    registrar.playBidirectional(
        Clock.TYPE,
        Clock.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            ServerRuntime.get(player.getServer()).clock(player, message);
          else clockReceiver.accept(message);
        });
  }
}
