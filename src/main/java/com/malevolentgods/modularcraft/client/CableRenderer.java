package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class CableRenderer {
  private CableRenderer() {}

  // Client render-thread only, bounded by the accepted scene's 512-cable world limit.
  private static final Map<Cable, CachedMesh> MESHES = new HashMap<>();
  private static final Set<Cable> VISIBLE = new HashSet<>();

  private record Shape(CableGeometry.Point from, CableGeometry.Point to, double base) {}

  private record CachedMesh(Shape shape, CableGeometry.Mesh mesh) {}

  static void clear() {
    MESHES.clear();
    VISIBLE.clear();
  }

  private static final class CableRenderState extends RenderStateShard {
    private static final RenderType TYPE =
        RenderType.create(
            "modularcraft_cable",
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

    private CableRenderState() {
      super("modularcraft_cable", () -> {}, () -> {});
    }
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) {
      clear();
      return;
    }
    PoseStack poses = event.getPoseStack();
    poses.pushPose();
    VISIBLE.clear();
    try {
      Vec3 camera = event.getCamera().getPosition();
      VertexConsumer vertices = mc.renderBuffers().bufferSource().getBuffer(CableRenderState.TYPE);
      for (var patch : ClientRuntime.patches()) {
        Map<UUID, ModuleState> modules = new HashMap<>();
        patch.modules().forEach(m -> modules.put(m.id(), m));
        for (Cable cable : patch.cables()) {
          ModuleState a = modules.get(cable.source()), b = modules.get(cable.destination());
          if (a == null || b == null || !a.loaded() || !b.loaded()) continue;
          CableGeometry.Point from = port(a, cable.output()), to = port(b, cable.input());
          if (from == null || to == null) continue;
          Shape shape = new Shape(from, to, Math.min(a.y(), b.y()));
          CachedMesh cached = MESHES.get(cable);
          if (cached == null || !cached.shape().equals(shape)) {
            // Local vertices retain the thin cross-section even near the world border.
            cached =
                new CachedMesh(
                    shape,
                    CableGeometry.mesh(
                        new CableGeometry.Point(0, 0, 0),
                        to.subtract(from),
                        shape.base() - from.y()));
            MESHES.put(cable, cached);
          }
          VISIBLE.add(cable);
          int color = cable.output().control ? 0xffffc36a : 0xff65d7e5;
          BlockPos lightPos = BlockPos.containing(from.x(), from.y(), from.z());
          int light =
              mc.level.hasChunkAt(lightPos) ? LevelRenderer.getLightColor(mc.level, lightPos) : 0;
          poses.pushPose();
          try {
            poses.translate(from.x() - camera.x, from.y() - camera.y, from.z() - camera.z);
            surface(vertices, poses, cached.mesh().tube(), color, light, true);
            surface(vertices, poses, cached.mesh().collar(), color, light, false);
          } finally {
            poses.popPose();
          }
        }
      }
    } finally {
      poses.popPose();
      MESHES.keySet().retainAll(VISIBLE);
      mc.renderBuffers().bufferSource().endBatch(CableRenderState.TYPE);
    }
  }

  private static void surface(
      VertexConsumer consumer,
      PoseStack poses,
      List<CableGeometry.Ring> rings,
      int color,
      int light,
      boolean caps) {
    for (int ring = 1; ring < rings.size(); ring++) {
      for (int side = 0; side < CableGeometry.SIDES; side++) {
        int next = (side + 1) % CableGeometry.SIDES;
        var a = rings.get(ring - 1);
        var b = rings.get(ring);
        // Facet shading makes the solid cross-section legible without another texture.
        double shade =
            .72 + .28 * (1 - Math.sin(2 * Math.PI * (side + .5) / CableGeometry.SIDES)) / 2;
        int shaded = shade(color, shade);
        vertex(consumer, poses, a.vertices().get(side), shaded, light);
        vertex(consumer, poses, b.vertices().get(side), shaded, light);
        vertex(consumer, poses, b.vertices().get(next), shaded, light);
        vertex(consumer, poses, a.vertices().get(next), shaded, light);
      }
    }
    if (caps && !rings.isEmpty()) {
      for (var ring : List.of(rings.getFirst(), rings.getLast())) {
        for (int side = 0; side < CableGeometry.SIDES; side++) {
          vertex(consumer, poses, ring.center(), color, light);
          vertex(consumer, poses, ring.vertices().get(side), color, light);
          vertex(
              consumer, poses, ring.vertices().get((side + 1) % CableGeometry.SIDES), color, light);
          vertex(consumer, poses, ring.center(), color, light);
        }
      }
    }
  }

  private static int shade(int color, double amount) {
    return 0xff000000
        | ((int) (((color >> 16) & 255) * amount) << 16)
        | ((int) (((color >> 8) & 255) * amount) << 8)
        | (int) ((color & 255) * amount);
  }

  private static void vertex(
      VertexConsumer consumer, PoseStack poses, CableGeometry.Point point, int color, int light) {
    consumer
        .addVertex(poses.last(), (float) point.x(), (float) point.y(), (float) point.z())
        .setColor(color)
        .setLight(light);
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  private static CableGeometry.Point port(ModuleState module, Port port) {
    Minecraft mc = Minecraft.getInstance();
    BlockPos pos = new BlockPos(module.x(), module.y(), module.z());
    if (mc.level == null || !mc.level.hasChunkAt(pos)) return null;
    var state = mc.level.getBlockState(pos);
    if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
    Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    return CableGeometry.port(
        module.x(),
        module.y(),
        module.z(),
        CableGeometry.Facing.valueOf(face.name()),
        module.kind().ports.indexOf(port),
        module.kind().ports.size());
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public static void hud(RenderGuiEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.level == null
        || !mc.player.getMainHandItem().is(ModularCraft.TOOL.get())
        || !(mc.hitResult instanceof BlockHitResult hit)) return;
    var state = mc.level.getBlockState(hit.getBlockPos());
    if (!(state.getBlock() instanceof ModuleBlock module)) return;
    Port port = module.portAt(state, hit);
    Component text =
        port == null
            ? Component.translatable("modularcraft.tool_help")
            : Component.translatable(
                "modularcraft.port_hint",
                Component.translatable(
                    "port.modularcraft." + port.name().toLowerCase(Locale.ROOT)));
    event
        .getGuiGraphics()
        .drawCenteredString(
            mc.font,
            text,
            mc.getWindow().getGuiScaledWidth() / 2,
            mc.getWindow().getGuiScaledHeight() / 2 + 18,
            0xffeeeeee);
    if (port == Port.GAIN_IN)
      event
          .getGuiGraphics()
          .drawCenteredString(
              mc.font,
              Component.translatable("modularcraft.gain_warning"),
              mc.getWindow().getGuiScaledWidth() / 2,
              mc.getWindow().getGuiScaledHeight() / 2 + 30,
              0xffffc36a);
  }
}
