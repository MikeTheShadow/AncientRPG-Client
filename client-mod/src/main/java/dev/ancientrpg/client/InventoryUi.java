package dev.ancientrpg.client;

import dev.ancientrpg.protocol.InventoryWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Shared item/text/risk primitives for the main bag and corpse container. */
final class InventoryUi {
  static final int INK = 0xFF10171B,
      PANEL = 0xFF1B252B,
      LINE = 0xFF3E4B50,
      IVORY = 0xFFF0E9DB,
      MUTED = 0xFFB6C1C1,
      BRASS = 0xFFD3B477,
      RISK = 0xFFE36363;
  private static final FontDescription FACE =
      new FontDescription.Resource(Identifier.parse("ancientrpg:dossier"));

  private InventoryUi() {}

  static Component styled(Component value) {
    return value.copy().withStyle(s -> s.withFont(FACE));
  }

  static void text(GuiGraphicsExtractor g, Component value, int x, int y, int color, int width) {
    var font = Minecraft.getInstance().font;
    var lines = font.split(styled(value), Math.max(1, width));
    if (!lines.isEmpty()) g.text(font, lines.getFirst(), x, y, color, false);
  }

  static void item(GuiGraphicsExtractor g, InventoryWire.Entry entry, int x, int y) {
    if (!entry.empty())
      g.item(
          new ItemStack(DemoRegistration.inventoryItems.get(entry.item()), entry.quantity()), x, y);
  }

  static void risk(
      GuiGraphicsExtractor g, InventoryWire.Entry entry, int x, int y, int width, int height) {
    if (entry.risky()) g.outline(x + 2, y + 2, width - 4, height - 4, RISK);
  }
}
