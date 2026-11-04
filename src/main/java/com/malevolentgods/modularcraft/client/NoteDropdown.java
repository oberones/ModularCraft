package com.malevolentgods.modularcraft.client;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Small fixed-size note/octave menu. The owning screen routes popup input before its controls. */
final class NoteDropdown extends Button {
  private static final int ROW = 14;
  private final String label;
  private final int count, screenHeight;
  private final IntFunction<String> option;
  private final IntConsumer select;
  private int value, cursor;
  private boolean expanded;

  NoteDropdown(
      int x,
      int y,
      int width,
      int screenHeight,
      String label,
      int count,
      IntFunction<String> option,
      IntConsumer select) {
    super(x, y, width, 18, Component.empty(), button -> {}, DEFAULT_NARRATION);
    this.screenHeight = screenHeight;
    this.label = label;
    this.count = count;
    this.option = option;
    this.select = select;
  }

  void value(int next) {
    value = next;
    setMessage(Component.translatable(label, option.apply(value)));
  }

  boolean expanded() {
    return expanded;
  }

  void close() {
    expanded = false;
  }

  @Override
  public void onPress() {
    if (!active) return;
    expanded = !expanded;
    cursor = value;
  }

  private int popupTop() {
    return Math.max(4, Math.min(getY() + height + 2, screenHeight - count * ROW - 4));
  }

  void clickOption(double x, double y, int button) {
    int index = (int) Math.floor((y - popupTop()) / ROW);
    if (active && button == 0 && x >= getX() && x < getRight() && index >= 0 && index < count)
      select.accept(index);
    close();
  }

  void scroll(double amount) {
    cursor = Math.clamp(cursor - (int) Math.signum(amount), 0, count - 1);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (!active) return false;
    if (!expanded) return super.keyPressed(key, scan, modifiers);
    switch (key) {
      case GLFW.GLFW_KEY_UP -> cursor = Math.max(0, cursor - 1);
      case GLFW.GLFW_KEY_DOWN -> cursor = Math.min(count - 1, cursor + 1);
      case GLFW.GLFW_KEY_HOME -> cursor = 0;
      case GLFW.GLFW_KEY_END -> cursor = count - 1;
      case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
        select.accept(cursor);
        close();
      }
      case GLFW.GLFW_KEY_ESCAPE -> close();
      default -> {
        return false;
      }
    }
    return true;
  }

  void renderPopup(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
    int y = popupTop();
    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 400);
    graphics.fill(getX() - 1, y - 1, getRight() + 1, y + count * ROW + 1, 0xffa6c7cf);
    graphics.fill(getX(), y, getRight(), y + count * ROW, 0xff18212a);
    for (int i = 0; i < count; i++) {
      int row = y + i * ROW;
      boolean hovered =
          mouseX >= getX() && mouseX < getRight() && mouseY >= row && mouseY < row + ROW;
      if (i == cursor || hovered) graphics.fill(getX(), row, getRight(), row + ROW, 0xff375665);
      graphics.drawString(font, option.apply(i), getX() + 5, row + 3, 0xffd9edf1);
    }
    graphics.pose().popPose();
  }

  @Override
  public void updateWidgetNarration(NarrationElementOutput output) {
    if (!expanded) super.updateWidgetNarration(output);
    else {
      output.add(NarratedElementType.TITLE, Component.translatable(label, option.apply(cursor)));
      output.add(NarratedElementType.USAGE, Component.translatable("modularcraft.note.menu_help"));
    }
  }
}
