package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class InventoryWire {
  public static final String REQUEST = "ancientrpg:inventory_request";
  public static final String STATE = "ancientrpg:inventory_state";
  public static final int REQUEST_BYTES = 19;
  public static final int STATE_BYTES = 265;

  public enum Result {
    SYNC("Inventory synchronized"),
    APPLIED("Equipment saved"),
    STALE("Inventory changed; try again"),
    INVALID("That item does not fit this slot"),
    HAND_LOCKED("Offhand is locked by your mainhand"),
    FULL("Main bag is full"),
    BUSY("Finish your current action first"),
    INELIGIBLE("Inventory is unavailable right now"),
    STORAGE("Could not save; nothing changed"),
    PRESSURE("Request delayed; please try again");
    private final String message;

    Result(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  public record Entry(int item, int quantity, boolean risky) {
    public static final Entry EMPTY = new Entry(0, 0);

    public Entry(int item, int quantity) {
      this(item, quantity, false);
    }

    public Entry {
      if (risky && item != 11) throw new IllegalArgumentException("Ineligible risky stack");
      if (item < 0
          || item > InventoryCatalog.ITEMS.size()
          || (item == 0
              ? quantity != 0
              : quantity < 1 || quantity > InventoryCatalog.item(item).maxStack()))
        throw new IllegalArgumentException("Invalid item stack");
    }

    public boolean empty() {
      return item == 0;
    }
  }

  public record State(
      long sequence,
      long revision,
      Result result,
      float health,
      float maxHealth,
      List<Entry> entries) {
    public State {
      if (sequence < -1
          || revision < 0
          || result == null
          || !Float.isFinite(health)
          || !Float.isFinite(maxHealth)
          || health < 0
          || maxHealth <= 0
          || health > maxHealth) throw new IllegalArgumentException("Invalid inventory state");
      entries = List.copyOf(entries);
      if (entries.size() != InventoryCatalog.SLOT_COUNT)
        throw new IllegalArgumentException("Invalid slot count");
      for (int i = 0; i < entries.size(); i++)
        if (!InventoryCatalog.fits(entries.get(i).item(), i)
            || (i >= InventoryCatalog.BAG_SIZE && entries.get(i).risky()))
          throw new IllegalArgumentException("Invalid equipped item");
      var main = entries.get(InventoryCatalog.Slot.MAINHAND.index());
      if (!main.empty()
          && InventoryCatalog.item(main.item()).locksOffhand()
          && !entries.get(InventoryCatalog.Slot.OFFHAND.index()).empty())
        throw new IllegalArgumentException("Conflicting hand equipment");
    }
  }

  private InventoryWire() {}

  public static byte[] encodeRequest(InventoryRequest r) {
    return ByteBuffer.allocate(REQUEST_BYTES)
        .putLong(r.sequence())
        .putLong(r.revision())
        .put((byte) (r.sync() ? 0 : 1))
        .put((byte) r.source())
        .put((byte) r.target())
        .array();
  }

  public static InventoryRequest decodeRequest(byte[] bytes) {
    if (bytes.length != REQUEST_BYTES)
      throw new IllegalArgumentException("Inventory request length");
    var b = ByteBuffer.wrap(bytes);
    long sequence = b.getLong(), revision = b.getLong();
    int op = Byte.toUnsignedInt(b.get());
    if (op > 1) throw new IllegalArgumentException("Inventory operation");
    return new InventoryRequest(
        sequence, revision, op == 0, Byte.toUnsignedInt(b.get()), Byte.toUnsignedInt(b.get()));
  }

  public static byte[] encodeState(State s) {
    var b =
        ByteBuffer.allocate(STATE_BYTES)
            .putLong(s.sequence())
            .putLong(s.revision())
            .put((byte) s.result().ordinal())
            .putFloat(s.health())
            .putFloat(s.maxHealth());
    for (var entry : s.entries()) putEntry(b, entry);
    return b.array();
  }

  public static void putEntry(ByteBuffer b, Entry entry) {
    b.putShort((short) entry.item())
        .putShort((short) entry.quantity())
        .put((byte) (entry.risky() ? 1 : 0));
  }

  public static Entry getEntry(ByteBuffer b) {
    int item = Short.toUnsignedInt(b.getShort()), quantity = Short.toUnsignedInt(b.getShort());
    int flags = Byte.toUnsignedInt(b.get());
    if (flags > 1) throw new IllegalArgumentException("Inventory flags");
    return new Entry(item, quantity, flags == 1);
  }

  public static State decodeState(byte[] bytes) {
    if (bytes.length != STATE_BYTES) throw new IllegalArgumentException("Inventory state length");
    var b = ByteBuffer.wrap(bytes);
    long sequence = b.getLong(), revision = b.getLong();
    int result = Byte.toUnsignedInt(b.get());
    if (result >= Result.values().length) throw new IllegalArgumentException("Inventory result");
    float health = b.getFloat(), max = b.getFloat();
    var entries = new ArrayList<Entry>();
    for (int i = 0; i < InventoryCatalog.SLOT_COUNT; i++) entries.add(getEntry(b));
    return new State(sequence, revision, Result.values()[result], health, max, entries);
  }
}
