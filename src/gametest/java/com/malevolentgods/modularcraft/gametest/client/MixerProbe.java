package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.AudioSettingsScreen;
import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.client.MixerScreen;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleState;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/**
 * Exercises real mixer widgets and accepted state; captures do not establish listening acceptance.
 */
public final class MixerProbe {
  private static UUID id;

  private MixerProbe() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(int tick) {
    if (Boolean.getBoolean("modularcraft.qa.load") || tick < 85) return;
    Minecraft mc = Minecraft.getInstance();
    if (tick == 85) {
      mc.options.guiScale().set(1);
      mc.resizeDisplay();
      return;
    }
    if (tick < 90) return;
    if (tick >= 490) {
      if (tick == 510 && ClientRuntime.mixerMeter(id) != null)
        throw new IllegalStateException("Retired mixer meter remains visible");
      if (tick >= 520 && tick <= 560 && tick % 10 == 0) {
        ClientRuntime.recover("resync");
        if (com.malevolentgods.modularcraft.client.AudioOutput.channels() != 0
            || com.malevolentgods.modularcraft.client.AudioOutput.timers() != 0)
          throw new IllegalStateException("Mixer recovery resource leak");
        System.out.println("MODULARCRAFT_QA mixer recovery cleanup PASS cycle=" + tick);
      }
      return;
    }
    if (tick == 415 && mc.screen instanceof AudioSettingsScreen settings) {
      settings.onClose();
      return;
    }
    if (tick > 410 && tick < 420) return;
    if (tick >= 460) {
      if (tick == 460 && mc.screen instanceof MixerScreen screen) screen.onClose();
      if (tick == 470) capture("world");
      return;
    }
    if (!(mc.screen instanceof MixerScreen screen))
      throw new IllegalStateException("Mixer editor missing at " + tick);
    id = screen.identity();
    ModuleState state =
        ClientRuntime.patches().stream()
            .flatMap(p -> p.modules().stream())
            .filter(m -> m.id().equals(id))
            .findFirst()
            .orElseThrow();
    if (tick == 90) {
      if (ClientRuntime.mixerMeter(id) == null)
        throw new IllegalStateException("Active mixer meter unavailable");
      capture("full");
    }
    if (tick >= 100 && tick <= 120) {
      var fader =
          screen.children().stream()
              .filter(AbstractSliderButton.class::isInstance)
              .map(AbstractSliderButton.class::cast)
              .findFirst()
              .orElseThrow();
      double x = fader.getX() + 22,
          y = fader.getY() + 5 + (1 - (100 - (tick - 100) * 3.75) / 100) * (fader.getHeight() - 10);
      if (tick == 100) screen.mouseClicked(x, y, 0);
      else screen.mouseDragged(x, y, 0, 0, 0);
      if (tick == 120) screen.mouseReleased(x, y, 0);
    }
    if (tick == 110
        && (state.value(MixerSettings.level(0)) >= 100
            || state.value(MixerSettings.level(0)) <= 25))
      throw new IllegalStateException("Live fader did not change sound before release");
    if (tick == 140) {
      if (Math.abs(state.value(MixerSettings.level(0)) - 25) > .001)
        throw new IllegalStateException("Final live fader edit lost: " + state.values());
      System.out.println("MODULARCRAFT_QA mixer live drag final=25 PASS");
    }
    if (tick == 150) click(screen, buttons(screen, "Mute:").get(1));
    if (tick == 170) click(screen, buttons(screen, "Solo:").get(0));
    if (tick == 190) click(screen, buttons(screen, "Solo:").get(1));
    if (tick == 210) click(screen, buttons(screen, "Mute:").get(4));
    if (tick == 230) {
      if (state.value(MixerSettings.mute(1)) != 1
          || state.value(MixerSettings.solo(0)) != 1
          || state.value(MixerSettings.solo(1)) != 1
          || state.value(MixerSettings.mute(4)) != 1)
        throw new IllegalStateException("Mixer mute/solo controls rejected");
      var meter = ClientRuntime.mixerMeter(id);
      if (meter == null || meter.peaks()[4] > .0001)
        throw new IllegalStateException("Master mute meter not silent");
      capture("mute-solo");
      System.out.println("MODULARCRAFT_QA mixer mute/solo/master and meter PASS");
    }
    if (tick == 240) click(screen, buttons(screen, "Mute:").get(4));
    if (tick == 260) click(screen, buttons(screen, "Solo:").get(0));
    if (tick == 280) click(screen, buttons(screen, "Solo:").get(1));
    if (tick == 300) click(screen, buttons(screen, "Mute:").get(1));
    if (tick == 320) edit(screen, 4, "37.125");
    if (tick == 340) edit(screen, 4, "-1");
    if (tick == 360) {
      if (state.value(MixerSettings.level(4)) != 37.125)
        throw new IllegalStateException("Numeric validation failed");
      screen.resize(mc, 380, 240);
      for (int i = 0; i < 2; i++) {
        var firstMute = buttons(screen, "Mute:").get(i);
        var firstSolo = buttons(screen, "Solo:").get(i);
        if (!firstMute.visible || !firstSolo.visible)
          throw new IllegalStateException("Compact strips require scrolling within a channel");
      }
    }
    if (tick == 380) edit(screen, 4, "25");
    if (tick == 400) {
      if (state.value(MixerSettings.level(4)) != 25)
        throw new IllegalStateException("Compact master entry failed");
      for (var child : screen.children())
        if (child instanceof AbstractWidget widget
            && widget.visible
            && (widget.getY() < 0 || widget.getBottom() > screen.height))
          throw new IllegalStateException("Mixer clipped control");
      screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
      capture("compact");
      System.out.println("MODULARCRAFT_QA mixer numeric validation/compact scrolling PASS");
    }
    if (tick == 405) ClientRuntime.toggleMute();
    if (tick == 406 && ClientRuntime.mixerMeter(id) != null)
      throw new IllegalStateException("Local mute meter active");
    if (tick == 407) ClientRuntime.toggleMute();
    if (tick == 410) click(screen, buttons(screen, "ModularCraft Audio").getFirst());
    if (tick == 425) edit(screen, 0, "50");
    if (tick == 445) {
      if (state.value(MixerSettings.level(0)) != 50)
        throw new IllegalStateException("Edit after local settings failed");
      System.out.println("MODULARCRAFT_QA mixer local mute/settings recovery PASS");
    }
  }

  private static List<Button> buttons(MixerScreen screen, String prefix) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getMessage().getString().startsWith(prefix))
        .toList();
  }

  private static void reveal(MixerScreen screen, AbstractWidget widget) {
    for (int i = 0; i < 40 && !widget.visible; i++)
      screen.keyPressed(widget.getY() < 30 ? GLFW.GLFW_KEY_PAGE_UP : GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
    if (!widget.visible)
      throw new IllegalStateException(
          "Cannot reach mixer control " + widget.getMessage().getString());
  }

  private static void click(MixerScreen screen, AbstractWidget widget) {
    reveal(screen, widget);
    screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + 8, 0);
    screen.mouseReleased(widget.getX() + widget.getWidth() / 2.0, widget.getY() + 8, 0);
  }

  private static void edit(MixerScreen screen, int channel, String value) {
    EditBox field =
        screen.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .toList()
            .get(channel);
    reveal(screen, field);
    field.setValue(value);
    screen.setFocused(field);
    screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "mixer-" + name + ".png",
        mc.getMainRenderTarget(),
        message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
  }
}
