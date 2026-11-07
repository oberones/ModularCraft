package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded client-only LED overlay; shared cached ring geometry, no world scans or chunk loads. */
public final class SequencerRenderer {
  private SequencerRenderer() {}

  private static final class LedState extends RenderStateShard {
    private static final RenderType TYPE =
        RenderType.create(
            "modularcraft_rhythm",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            4096,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_LEASH_SHADER)
                .setTextureState(NO_TEXTURE)
                .setTransparencyState(NO_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false));

    private LedState() {
      super("modularcraft_rhythm", () -> {}, () -> {});
    }
  }

  // Borrowed Minecraft singleton, level and render buffers remain owned by the host.
  @SuppressWarnings("PMD.CloseResource")
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;
    SequencerVisuals.advance(ClientRuntime.visualSample());
    var camera = event.getCamera().getPosition();
    PoseStack poses = event.getPoseStack();
    VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(LedState.TYPE);
    for (var patch : ClientRuntime.patches())
      for (var module : patch.modules()) {
        if ((module.kind() != ModuleKind.EUCLIDEAN_SEQUENCER
                && module.kind() != ModuleKind.STEP_SEQUENCER)
            || !module.loaded()) continue;
        BlockPos position = new BlockPos(module.x(), module.y(), module.z());
        if (camera.distanceToSqr(module.x() + .5, module.y() + .5, module.z() + .5) > 1024
            || !mc.level.hasChunkAt(position)
            || !event.getFrustum().isVisible(new AABB(position))) continue;
        var block = mc.level.getBlockState(position);
        if (!(block.getBlock()
                instanceof com.malevolentgods.modularcraft.game.ModuleBlock moduleBlock)
            || moduleBlock.kind != module.kind()) continue;
        Sequencer seq = SequencerVisuals.get(module.id());
        var melody = SequencerVisuals.steps(module.id());
        if (seq == null && melody == null) continue;
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        double front =
            (camera.x - module.x() - .5) * facing.getStepX()
                + (camera.z - module.z() - .5) * facing.getStepZ();
        if (front <= 0) continue;
        poses.pushPose();
        try {
          poses.translate(module.x() - camera.x, module.y() - camera.y, module.z() - camera.z);
          if (melody != null) {
            for (int i = 0; i < 16; i++) {
              double u = (11 + (i % 8) * 6) / 64.0, v = 1 - (28 + (i / 8) * 10) / 64.0;
              if (i == melody.step())
                square(vertices, poses, facing, u, v, 2.5 / 64, 0xfff2f5f7, 0);
              int color =
                  i >= melody.active().length()
                      ? 0xff64727c
                      : melody.active().notes().get(i) < 0 ? 0xff18212a : 0xff65d7e5;
              square(vertices, poses, facing, u, v, 1.5 / 64, color, .0001);
            }
          }
          for (int c = 0; seq != null && c < 4; c++) {
            var pattern = seq.active().channels().get(c);
            double cx = c % 2 == 0 ? 20 : 44, cy = c < 2 ? 24 : 39;
            // Cover the authored idle ring before drawing a different live length/rotation.
            for (int step = 0; step < 16; step++) {
              double u = (cx + 6 * SequencerVisuals.x(16, step)) / 64;
              double v = 1 - (cy + 6 * SequencerVisuals.y(16, step)) / 64;
              square(vertices, poses, facing, u, v, 1.3 / 64, 0xff202d37, -.0004);
            }
            for (int step = 0; step < pattern.steps(); step++) {
              double u = (cx + 6 * SequencerVisuals.x(pattern.steps(), step)) / 64;
              double v = 1 - (cy + 6 * SequencerVisuals.y(pattern.steps(), step)) / 64;
              int color =
                  module.value(
                              com.malevolentgods.modularcraft.domain.SequencerSettings.parameter(
                                  c, 3))
                          == 1
                      ? 0xff64727c
                      : SequencerVisuals.COLORS[c];
              if (step == seq.step(c))
                square(vertices, poses, facing, u, v, 1.3 / 64, 0xfff2f5f7, 0);
              square(vertices, poses, facing, u, v, .8 / 64, color, .0001);
              if (!pattern.hit(step))
                square(vertices, poses, facing, u, v, .4 / 64, 0xff18212a, .0002);
            }
          }
        } finally {
          poses.popPose();
        }
      }
    mc.renderBuffers().bufferSource().endBatch(LedState.TYPE);
  }

  private static void square(
      VertexConsumer vertices,
      PoseStack poses,
      Direction face,
      double u,
      double v,
      double r,
      int color,
      double offset) {
    vertex(vertices, poses, face, u - r, v - r, color, offset);
    vertex(vertices, poses, face, u + r, v - r, color, offset);
    vertex(vertices, poses, face, u + r, v + r, color, offset);
    vertex(vertices, poses, face, u - r, v + r, color, offset);
  }

  private static void vertex(
      VertexConsumer vertices,
      PoseStack poses,
      Direction face,
      double u,
      double v,
      int color,
      double offset) {
    double x =
        switch (face) {
          case NORTH -> 1 - u;
          case SOUTH -> u;
          case EAST -> 1.002 + offset;
          default -> -.002 - offset;
        };
    double z =
        switch (face) {
          case NORTH -> -.002 - offset;
          case SOUTH -> 1.002 + offset;
          case EAST -> 1 - u;
          default -> u;
        };
    vertices
        .addVertex(poses.last(), (float) x, (float) v, (float) z)
        .setColor(color)
        .setLight(0xf000f0);
  }
}
