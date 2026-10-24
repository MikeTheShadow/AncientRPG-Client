package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Bounded public body presentation and authorized loot snapshots, never encounter decisions. */
public final class CorpseWire {
  public static final String REQUEST = "ancientrpg:corpse_request",
      STATE = "ancientrpg:corpse_state",
      EVENT = "ancientrpg:corpse_event";
  public static final int REQUEST_BYTES = 43, STATE_BYTES = 230, EVENT_MAX_BYTES = 512;
  public static final List<InventoryWire.Entry> EMPTY =
      List.copyOf(Collections.nCopies(36, InventoryWire.Entry.EMPTY));

  public enum Result {
    OPEN,
    APPLIED,
    LOCKED,
    STALE,
    FULL,
    GONE,
    INELIGIBLE,
    BUSY,
    STORAGE,
    PRESSURE
  }

  public record Body(
      UUID id,
      UUID actor,
      String name,
      int entity,
      double x,
      double y,
      double z,
      float yaw,
      int ageMillis,
      int remainingMillis,
      List<InventoryWire.Entry> equipment) {
    public Body {
      if (id == null
          || actor == null
          || name == null
          || name.isBlank()
          || name.length() > 32
          || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7)
          || entity < 0
          || !Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || !Float.isFinite(yaw)
          || Math.abs(x) > 30_000_000
          || Math.abs(z) > 30_000_000
          || Math.abs(y) > 30_000_000
          || ageMillis < 0
          || ageMillis > 300_000
          || remainingMillis < 0
          || remainingMillis > 300_000) throw new IllegalArgumentException("Corpse presentation");
      equipment = List.copyOf(equipment);
      if (equipment.size() != 6 || equipment.stream().anyMatch(InventoryWire.Entry::risky))
        throw new IllegalArgumentException("Corpse equipment");
    }
  }

  public record Event(UUID id, Body body) {}

  public record State(
      long sequence,
      UUID corpse,
      long revision,
      long inventoryRevision,
      Result result,
      int lockMillis,
      int remainingMillis,
      boolean canLoot,
      List<InventoryWire.Entry> entries) {
    public State {
      if (sequence < -1
          || corpse == null
          || revision < 0
          || inventoryRevision < 0
          || result == null
          || lockMillis < 0
          || lockMillis > 60_000
          || remainingMillis < 0
          || remainingMillis > 300_000) throw new IllegalArgumentException("Corpse state");
      entries = List.copyOf(entries);
      if (entries.size() != 36
          || entries.stream().anyMatch(e -> !e.empty() && !e.risky())
          || (!canLoot && entries.stream().anyMatch(e -> !e.empty())))
        throw new IllegalArgumentException("Corpse contents");
    }
  }

  private CorpseWire() {}

  public static byte[] encodeRequest(CorpseRequest r) {
    var b = ByteBuffer.allocate(REQUEST_BYTES).putLong(r.sequence()).put((byte) r.operation());
    putUuid(b, r.corpse());
    return b.putLong(r.revision())
        .putLong(r.inventoryRevision())
        .put((byte) r.slot())
        .put((byte) r.target())
        .array();
  }

  public static CorpseRequest decodeRequest(byte[] bytes) {
    if (bytes.length != REQUEST_BYTES) throw new IllegalArgumentException("Corpse request length");
    var b = ByteBuffer.wrap(bytes);
    return new CorpseRequest(
        b.getLong(),
        Byte.toUnsignedInt(b.get()),
        uuid(b),
        b.getLong(),
        b.getLong(),
        Byte.toUnsignedInt(b.get()),
        Byte.toUnsignedInt(b.get()));
  }

  public static byte[] encodeState(State s) {
    var b = ByteBuffer.allocate(STATE_BYTES).putLong(s.sequence());
    putUuid(b, s.corpse());
    b.putLong(s.revision())
        .putLong(s.inventoryRevision())
        .put((byte) s.result().ordinal())
        .putInt(s.lockMillis())
        .putInt(s.remainingMillis())
        .put((byte) (s.canLoot() ? 1 : 0));
    s.entries().forEach(e -> InventoryWire.putEntry(b, e));
    return b.array();
  }

  public static State decodeState(byte[] bytes) {
    if (bytes.length != STATE_BYTES) throw new IllegalArgumentException("Corpse state length");
    var b = ByteBuffer.wrap(bytes);
    long seq = b.getLong();
    UUID id = uuid(b);
    long rev = b.getLong(), inv = b.getLong();
    int result = Byte.toUnsignedInt(b.get()),
        lock = b.getInt(),
        remaining = b.getInt(),
        access = Byte.toUnsignedInt(b.get());
    if (result >= Result.values().length || access > 1)
      throw new IllegalArgumentException("Corpse result");
    var entries = new ArrayList<InventoryWire.Entry>();
    for (int i = 0; i < 36; i++) entries.add(InventoryWire.getEntry(b));
    return new State(
        seq, id, rev, inv, Result.values()[result], lock, remaining, access == 1, entries);
  }

  public static byte[] encodeBody(Body s) {
    var b = ByteBuffer.allocate(EVENT_MAX_BYTES).put((byte) 0);
    putUuid(b, s.id());
    putUuid(b, s.actor());
    byte[] name = s.name().getBytes(StandardCharsets.UTF_8);
    b.putShort((short) name.length).put(name);
    b.putInt(s.entity())
        .putDouble(s.x())
        .putDouble(s.y())
        .putDouble(s.z())
        .putFloat(s.yaw())
        .putInt(s.ageMillis())
        .putInt(s.remainingMillis());
    s.equipment().forEach(e -> InventoryWire.putEntry(b, e));
    return java.util.Arrays.copyOf(b.array(), b.position());
  }

  public static byte[] encodeRemoval(UUID id) {
    var b = ByteBuffer.allocate(17).put((byte) 1);
    putUuid(b, id);
    return b.array();
  }

  public static Event decodeEvent(byte[] bytes) {
    if (bytes.length < 17 || bytes.length > EVENT_MAX_BYTES)
      throw new IllegalArgumentException("Corpse event length");
    try {
      var b = ByteBuffer.wrap(bytes);
      int op = Byte.toUnsignedInt(b.get());
      UUID id = uuid(b);
      if (op == 1) {
        if (b.hasRemaining()) throw new IllegalArgumentException("Trailing corpse removal");
        return new Event(id, null);
      }
      if (op != 0) throw new IllegalArgumentException("Corpse operation");
      UUID actor = uuid(b);
      int len = Short.toUnsignedInt(b.getShort());
      if (len < 1 || len > 128 || len > b.remaining())
        throw new IllegalArgumentException("Corpse name");
      byte[] name = new byte[len];
      b.get(name);
      String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(name)).toString();
      int entity = b.getInt();
      double x = b.getDouble(), y = b.getDouble(), z = b.getDouble();
      float yaw = b.getFloat();
      int age = b.getInt(), remain = b.getInt();
      var gear = new ArrayList<InventoryWire.Entry>();
      for (int i = 0; i < 6; i++) gear.add(InventoryWire.getEntry(b));
      if (b.hasRemaining()) throw new IllegalArgumentException("Trailing corpse event");
      return new Event(id, new Body(id, actor, text, entity, x, y, z, yaw, age, remain, gear));
    } catch (java.nio.BufferUnderflowException
        | java.nio.charset.CharacterCodingException malformed) {
      throw new IllegalArgumentException("Corpse event", malformed);
    }
  }

  private static void putUuid(ByteBuffer b, UUID id) {
    b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
  }

  private static UUID uuid(ByteBuffer b) {
    return new UUID(b.getLong(), b.getLong());
  }
}
