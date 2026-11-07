package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.client.CableGeometry;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModularCraft.ID)
@PrefixGameTestTemplate(false)
public final class AmpGameTests {
  private AmpGameTests() {}

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void twentyModulePatchRejectsTwentyFirstWithoutMutation(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var runtime = ServerRuntime.get(h.getLevel().getServer());
    java.util.List<ModuleEntity> modules = new java.util.ArrayList<>();
    for (int i = 0; i < 21; i++) {
      BlockPos pos = new BlockPos(1 + i % 3, 1 + i / 9, 1 + i / 3 % 3);
      h.setBlock(pos, ModularCraft.MODULES.get(ModuleKind.FILTER).get());
      var entity = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
      entity.initialize(owner);
      modules.add(entity);
    }
    for (int i = 1; i < 20; i++) {
      var a = runtime.state().get(modules.get(i - 1).state().id());
      var b = runtime.state().get(modules.get(i).state().id());
      runtime
          .state()
          .connect(
              owner,
              false,
              new Cable(a.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN),
              a.revision(),
              b.revision());
    }
    modules.forEach(runtime::attach);
    var snapshot = runtime.currentPatch(modules.getFirst().state().id());
    h.assertTrue(
        snapshot.modules().size() == 20 && snapshot.cables().size() == 19,
        "Twenty connected modules are available to the client");
    h.assertTrue(
        snapshot.equals(
            com.malevolentgods.modularcraft.domain.StateCodec.decode(
                com.malevolentgods.modularcraft.domain.StateCodec.encode(snapshot))),
        "Twenty-module runtime snapshot roundtrips");
    var before = runtime.state().modules();
    var cables = runtime.state().cables();
    long revision = runtime.state().revision();
    var a = runtime.state().get(modules.get(19).state().id());
    var b = runtime.state().get(modules.get(20).state().id());
    boolean refused = false;
    try {
      runtime
          .state()
          .connect(
              owner,
              false,
              new Cable(a.id(), Port.AUDIO_OUT, b.id(), Port.AUDIO_IN),
              a.revision(),
              b.revision());
    } catch (IllegalArgumentException expected) {
      refused = "patch_modules:20".equals(expected.getMessage());
    }
    h.assertTrue(refused, "Twenty-first module rejected with the correct limit");
    h.assertTrue(
        before.equals(runtime.state().modules())
            && cables.equals(runtime.state().cables())
            && revision == runtime.state().revision(),
        "Rejected connection preserves modules, cables and revisions");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void ampAndSpeakerJacksAlignInEveryOrientation(GameTestHelper h) {
    for (ModuleKind kind : java.util.List.of(ModuleKind.AMP, ModuleKind.SPEAKER)) {
      var block = ModularCraft.MODULES.get(kind).get();
      BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
      for (Direction face : Direction.Plane.HORIZONTAL) {
        var state =
            block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        for (int i = 0; i < kind.ports.size(); i++) {
          var point =
              CableGeometry.port(
                  pos.getX(),
                  pos.getY(),
                  pos.getZ(),
                  CableGeometry.Facing.valueOf(face.name()),
                  i,
                  kind.ports.size());
          var hit = new BlockHitResult(new Vec3(point.x(), point.y(), point.z()), face, pos, false);
          h.assertTrue(
              block.portAt(state, hit) == kind.ports.get(i),
              "Jack matches cable endpoint " + face + " " + i);
        }
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void ampReloadAndRemovalPreserveWorldIntegrity(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
    h.setBlock(a, ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get());
    h.setBlock(b, ModularCraft.MODULES.get(ModuleKind.AMP).get());
    var source = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(a));
    var mixer = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(b));
    source.initialize(owner);
    mixer.initialize(owner);
    var runtime = ServerRuntime.get(h.getLevel().getServer());
    for (var parameter : ModuleKind.AMP.parameters) {
      var m = runtime.state().get(mixer.state().id());
      runtime
          .state()
          .edit(
              owner,
              false,
              m.id(),
              m.revision(),
              parameter,
              parameter == com.malevolentgods.modularcraft.domain.Parameter.DRONE
                  ? 1
                  : parameter.initial);
    }
    for (Port input : java.util.List.of(Port.AUDIO_IN)) {
      var s = runtime.state().get(source.state().id());
      var m = runtime.state().get(mixer.state().id());
      runtime
          .state()
          .connect(
              owner,
              false,
              new Cable(s.id(), Port.AUDIO_OUT, m.id(), input),
              s.revision(),
              m.revision());
    }
    runtime.attach(source);
    runtime.attach(mixer);
    var tag = mixer.saveWithFullMetadata(h.getLevel().registryAccess());
    var restored =
        (ModuleEntity)
            BlockEntity.loadStatic(
                h.absolutePos(b), mixer.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(
        restored != null && restored.state().values().equals(mixer.state().values()),
        "All controls survive NBT reload");
    UUID id = mixer.state().id();
    h.setBlock(b, Blocks.AIR);
    h.assertTrue(
        runtime.state().modules().stream().noneMatch(m -> m.id().equals(id)),
        "Removed mixer leaves no world record");
    h.assertTrue(
        runtime.state().cables().stream()
            .noneMatch(c -> c.destination().equals(id) || c.source().equals(id)),
        "Removal clears incident cables");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void ampControlsRejectGuestsAndStaleEdits(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(owner, "AmpOwner"));
    var guest =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AmpGuest"));
    BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
    h.getLevel()
        .setBlock(pos, ModularCraft.MODULES.get(ModuleKind.AMP).get().defaultBlockState(), 3);
    var amp = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    amp.initialize(owner);
    player.setPos(pos.getCenter());
    guest.setPos(pos.getCenter());
    var runtime = ServerRuntime.get(h.getLevel().getServer());
    UUID id = amp.state().id();
    long revision = amp.state().revision();
    ServerRuntime.enqueue(
        guest,
        new com.malevolentgods.modularcraft.network.Messages.Control(
            id,
            revision,
            com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
            com.malevolentgods.modularcraft.domain.Parameter.ATTACK,
            9));
    ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Control(
            id,
            revision + 100,
            com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
            com.malevolentgods.modularcraft.domain.Parameter.DRONE,
            1));
    ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Control(
            id,
            revision,
            com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
            com.malevolentgods.modularcraft.domain.Parameter.AMP_LEVEL,
            37.125));
    h.startSequence()
        .thenWaitUntil(() -> h.assertTrue(runtime.pendingRequests() == 0, "Requests drained"))
        .thenExecute(
            () -> {
              var m = runtime.state().get(id);
              h.assertTrue(
                  m.value(com.malevolentgods.modularcraft.domain.Parameter.AMP_LEVEL) == 37.125,
                  "Owner edit accepted");
              h.assertTrue(
                  m.value(com.malevolentgods.modularcraft.domain.Parameter.ATTACK) == .01,
                  "Guest rejected");
              h.assertTrue(
                  m.value(com.malevolentgods.modularcraft.domain.Parameter.DRONE) == 0,
                  "Stale edit rejected");
              h.succeed();
            });
  }
}
