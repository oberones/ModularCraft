package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
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

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;
    PoseStack poses = event.getPoseStack();
    poses.pushPose();
    Vec3 camera = event.getCamera().getPosition();
    poses.translate(-camera.x, -camera.y, -camera.z);
    VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
    for (var patch : ClientRuntime.patches()) {
      Map<UUID, ModuleState> modules = new HashMap<>();
      patch.modules().forEach(m -> modules.put(m.id(), m));
      for (Cable cable : patch.cables()) {
        ModuleState a = modules.get(cable.source()), b = modules.get(cable.destination());
        if (a == null || b == null || !a.loaded() || !b.loaded()) continue;
        Vec3 from = port(a, cable.output()), to = port(b, cable.input());
        int color = cable.output().control ? 0xffffc36a : 0xff65d7e5;
        Vec3 previous = from;
        for (int step = 1; step <= 16; step++) {
          double t = step / 16.0;
          Vec3 next = from.lerp(to, t).add(0, -.25 * Math.sin(Math.PI * t), 0);
          line(lines, poses, previous, next, color);
          previous = next;
        }
        Vec3 direction = to.subtract(from).normalize();
        Vec3 wing = new Vec3(-direction.z, 0, direction.x).scale(.11);
        line(lines, poses, to, to.subtract(direction.scale(.25)).add(wing), color);
        line(lines, poses, to, to.subtract(direction.scale(.25)).subtract(wing), color);
      }
    }
    poses.popPose();
    mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
  }

  private static void line(
      VertexConsumer consumer, PoseStack poses, Vec3 from, Vec3 to, int color) {
    Vec3 normal = to.subtract(from).normalize();
    consumer
        .addVertex(poses.last(), (float) from.x, (float) from.y, (float) from.z)
        .setColor(color)
        .setNormal(poses.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    consumer
        .addVertex(poses.last(), (float) to.x, (float) to.y, (float) to.z)
        .setColor(color)
        .setNormal(poses.last(), (float) normal.x, (float) normal.y, (float) normal.z);
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  private static Vec3 port(ModuleState module, Port port) {
    Minecraft mc = Minecraft.getInstance();
    BlockPos pos = new BlockPos(module.x(), module.y(), module.z());
    if (mc.level == null || !mc.level.hasChunkAt(pos)) return pos.getCenter();
    var state = mc.level.getBlockState(pos);
    if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return pos.getCenter();
    Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    double u = (module.kind().ports.indexOf(port) + .5) / module.kind().ports.size();
    return switch (face) {
      case NORTH -> new Vec3(module.x() + 1 - u, module.y() + .2, module.z() - .01);
      case SOUTH -> new Vec3(module.x() + u, module.y() + .2, module.z() + 1.01);
      case EAST -> new Vec3(module.x() + 1.01, module.y() + .2, module.z() + 1 - u);
      default -> new Vec3(module.x() - .01, module.y() + .2, module.z() + u);
    };
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
