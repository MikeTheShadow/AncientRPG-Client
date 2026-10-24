package dev.ancientrpg.client;

import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.Skill;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Client presentation stays translatable through rendering and narration. */
final class ClientText {
  private ClientText() {}

  static Component tr(String key, Object... arguments) {
    return Component.translatable("ancientrpg." + key, arguments);
  }

  static Component item(InventoryCatalog.Item item) {
    return Component.translatable(itemKey(item));
  }

  static Component description(InventoryCatalog.Item item) {
    return Component.translatable(itemKey(item) + ".description");
  }

  static String itemKey(InventoryCatalog.Item item) {
    return "item." + item.key().replace(':', '.');
  }

  static Component family(InventoryCatalog.Item item) {
    return tr("family." + item.family().toLowerCase(Locale.ROOT).replace(' ', '_'));
  }

  static Component slot(InventoryCatalog.Slot slot) {
    return tr("inventory.slot." + slot.name().toLowerCase(Locale.ROOT));
  }

  static Component skill(Skill skill) {
    return tr("skill." + skill.name().toLowerCase(Locale.ROOT));
  }

  static Component skillDescription(Skill skill) {
    return tr("skill." + skill.name().toLowerCase(Locale.ROOT) + ".description");
  }

  static Component seconds(double value, int precision) {
    return tr("format.seconds", String.format(Locale.ROOT, "%." + precision + "f", value));
  }
}
