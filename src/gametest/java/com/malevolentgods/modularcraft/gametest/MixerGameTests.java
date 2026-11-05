package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.client.CableGeometry;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.MixerSettings;
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
public final class MixerGameTests {
  private MixerGameTests() {}

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void allFiveJacksAlignInEveryOrientation(GameTestHelper h) {
    var block = ModularCraft.MODULES.get(ModuleKind.MIXER).get();
    BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
    for (Direction face : Direction.Plane.HORIZONTAL) {
      var state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face);
      for (int i = 0; i < 5; i++) {
        var point =
            CableGeometry.port(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                CableGeometry.Facing.valueOf(face.name()),
                i,
                5);
        var hit = new BlockHitResult(new Vec3(point.x(), point.y(), point.z()), face, pos, false);
        h.assertTrue(
            block.portAt(state, hit) == ModuleKind.MIXER.ports.get(i),
            "Jack matches cable endpoint " + face + " " + i);
      }
    }
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void mixerReloadAndRemovalPreserveWorldIntegrity(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
    h.setBlock(a, ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get());
    h.setBlock(b, ModularCraft.MODULES.get(ModuleKind.MIXER).get());
    var source = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(a));
    var mixer = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(b));
    source.initialize(owner);
    mixer.initialize(owner);
    var runtime = ServerRuntime.get(h.getLevel().getServer());
    for (var parameter : MixerSettings.PARAMETERS) {
      var m = runtime.state().get(mixer.state().id());
      runtime
          .state()
          .edit(owner, false, m.id(), m.revision(), parameter, parameter.max == 1 ? 1 : 37.125);
    }
    for (Port input : MixerSettings.INPUTS) {
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
}
