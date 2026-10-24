package dev.ancientrpg.protocol;

import java.util.List;

/** Public presentation/slot metadata only. Ownership and all mutations belong to the server. */
public final class InventoryCatalog {
  public static final int BAG_SIZE = 36;
  public static final int SLOT_COUNT = 48;

  public enum Slot {
    HEAD("Head"),
    CHEST("Chest"),
    HANDS("Hands"),
    WAIST("Waist"),
    LEGS("Legs"),
    FEET("Feet"),
    NECKLACE("Necklace"),
    RING("Ring"),
    EARRING("Earring"),
    MAINHAND("Mainhand"),
    OFFHAND("Offhand"),
    BANDAGE("First aid");
    private final String title;

    Slot(String title) {
      this.title = title;
    }

    public String title() {
      return title;
    }

    public int index() {
      return BAG_SIZE + ordinal();
    }
  }

  public record Item(
      int id,
      String key,
      String title,
      String family,
      boolean weapon,
      boolean locksOffhand,
      int maxStack,
      String description) {}

  public static final List<Item> ITEMS =
      List.of(
          new Item(
              1,
              PublicContent.SWORD,
              "Practice Sword",
              "Sword",
              true,
              false,
              1,
              "One-handed sword. The five training arts require an empty offhand."),
          new Item(
              2,
              "ancientrpg:practice_dagger",
              "Practice Dagger",
              "Dagger",
              true,
              false,
              1,
              "One-handed dagger. Mainhand or offhand. Combat skills are not included in this"
                  + " preview."),
          new Item(
              3,
              "ancientrpg:practice_axe",
              "Practice Axe",
              "Axe",
              true,
              false,
              1,
              "One-handed axe. Mainhand or offhand. Offhand ability augments are planned."),
          new Item(
              4,
              "ancientrpg:practice_greatsword",
              "Practice Greatsword",
              "Greatsword",
              true,
              true,
              1,
              "Two-handed greatsword. Locks offhand. New combat skills are planned."),
          new Item(
              5,
              "ancientrpg:practice_greataxe",
              "Practice Greataxe",
              "Greataxe",
              true,
              true,
              1,
              "Two-handed greataxe. Locks offhand. New combat skills are planned."),
          new Item(
              6,
              "ancientrpg:practice_hammer",
              "Practice Hammer",
              "Hammer",
              true,
              true,
              1,
              "Two-handed hammer. Locks offhand. New combat skills are planned."),
          new Item(
              7,
              "ancientrpg:practice_spear",
              "Practice Spear",
              "Spear",
              true,
              true,
              1,
              "Two-handed spear. Locks offhand. New combat skills are planned."),
          new Item(
              8,
              "ancientrpg:practice_rapier",
              "Practice Rapier",
              "Rapier",
              true,
              true,
              1,
              "Rapier occupies both hand slots. Its dodge and parry skills are planned."),
          new Item(
              9,
              "ancientrpg:field_bandage",
              "Field Bandage",
              "First aid",
              false,
              false,
              64,
              "Fits the first-aid slot. Healing is not enabled in this inventory preview."),
          new Item(
              10,
              PublicContent.ITEM,
              "Ancient Shard",
              "Tool",
              false,
              false,
              64,
              "Activates the Ancient Switch. Its hotbar shortcut remains available."));

  private InventoryCatalog() {}

  public static Item item(int id) {
    if (id < 1 || id > ITEMS.size()) throw new IllegalArgumentException("Unknown inventory item");
    return ITEMS.get(id - 1);
  }

  public static boolean fits(int itemId, int position) {
    if (position < 0 || position >= SLOT_COUNT) return false;
    if (itemId == 0) return true;
    var item = item(itemId);
    if (position < BAG_SIZE) return true;
    if (position == Slot.MAINHAND.index()) return item.weapon();
    if (position == Slot.OFFHAND.index()) return item.weapon() && !item.locksOffhand();
    return position == Slot.BANDAGE.index() && itemId == 9;
  }
}
