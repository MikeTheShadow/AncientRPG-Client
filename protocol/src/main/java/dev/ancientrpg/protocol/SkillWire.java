package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fixed-width, big-endian, bounded v3 skill contracts. */
public final class SkillWire {
  public static final String USE = "ancientrpg:skill_use";
  public static final String UNLOCK = "ancientrpg:skill_unlock";
  public static final String STATE = "ancientrpg:skill_state";
  public static final String EVENT = "ancientrpg:skill_event";
  public static final int MAX_BYTES = 1024;

  public enum Reason {
    OK,
    LOCKED,
    PREREQUISITES,
    POINTS,
    ALREADY_LEARNED,
    BUSY,
    COOLDOWN,
    INELIGIBLE,
    QUEUE_PRESSURE,
    DUPLICATE,
    QUEUED,
    BUFFER_FULL
  }

  public enum Phase {
    START,
    HIT,
    END,
    CANCEL,
    SNAPSHOT,
    REMOVE
  }

  public record Motion(
      double originX,
      double originY,
      double originZ,
      double px,
      double py,
      double pz,
      float yaw,
      boolean blocked,
      boolean disabled) {
    public Motion {
      for (double v : new double[] {originX, originY, originZ, px, py, pz})
        if (!Double.isFinite(v) || Math.abs(v) > 2_000_000_000)
          throw new IllegalArgumentException("Invalid motion coordinate");
      if (!Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid facing");
    }

    public Motion at(double x, double y, double z, boolean block, boolean disable) {
      return new Motion(originX, originY, originZ, x, y, z, yaw, block, disable);
    }
  }

  public record Queued(long sequence, Skill skill) {
    public Queued {
      if (sequence < 0) throw new IllegalArgumentException("Invalid queued sequence");
      Objects.requireNonNull(skill);
    }
  }

  public record Action(
      long id, Skill skill, long start, double x, double y, double z, Motion motion) {
    public Action(long id, Skill skill, long start, double x, double y, double z) {
      this(id, skill, start, x, y, z, new Motion(0, 0, 0, 0, 0, 0, 0, false, false));
    }

    public Action withMotion(Motion motion) {
      return new Action(id, skill, start, x, y, z, motion);
    }

    public Action {
      Objects.requireNonNull(skill);
      Objects.requireNonNull(motion);
      double length = x * x + y * y + z * z;
      if (id < 0 || start < 0 || !Double.isFinite(length) || Math.abs(length - 1) > 1e-6)
        throw new IllegalArgumentException("Invalid action");
    }
  }

  public record State(
      long sequence,
      Reason reason,
      long tick,
      int learned,
      int points,
      List<Long> cooldowns,
      Action action,
      Queued queued) {
    public State(
        long sequence,
        Reason reason,
        long tick,
        int learned,
        int points,
        List<Long> cooldowns,
        Action action) {
      this(sequence, reason, tick, learned, points, cooldowns, action, null);
    }

    public State {
      Objects.requireNonNull(reason);
      cooldowns = List.copyOf(cooldowns);
      if (sequence < -1
          || tick < 0
          || learned < 1
          || learned > 31
          || (learned & 1) == 0
          || points < 0
          || points > 4
          || cooldowns.size() != 5
          || cooldowns.stream().anyMatch(v -> v < 0)
          || (action != null && action.start() > tick)
          || (queued != null && action == null))
        throw new IllegalArgumentException("Invalid skill state");
    }
  }

  public record Hit(int target, int damage, int health) {
    public Hit {
      if (target < 0 || damage < 1 || damage > 24 || health < 0 || health > 100)
        throw new IllegalArgumentException("Invalid hit");
    }
  }

  public record Event(long tick, int actor, Phase phase, Action action, List<Hit> hits) {
    public Event {
      Objects.requireNonNull(phase);
      Objects.requireNonNull(action);
      hits = List.copyOf(hits);
      if (tick < action.start()
          || actor < 0
          || hits.size() > 3
          || (phase != Phase.HIT && !hits.isEmpty())
          || hits.stream().map(Hit::target).distinct().count() != hits.size())
        throw new IllegalArgumentException("Invalid skill event");
    }
  }

  private SkillWire() {}

  public static byte[] encodeRequest(SkillRequest r) {
    return ByteBuffer.allocate(9).putLong(r.sequence()).put((byte) r.skill().ordinal()).array();
  }

  public static SkillRequest decodeRequest(byte[] bytes, boolean unlock) {
    ByteBuffer b = bounded(bytes, 9, 9);
    return new SkillRequest(b.getLong(), Skill.fromId(Byte.toUnsignedInt(b.get())), unlock);
  }

  public static byte[] encodeState(State s) {
    ByteBuffer b =
        ByteBuffer.allocate(61 + (s.action() == null ? 0 : 94) + (s.queued() == null ? 0 : 9));
    b.putLong(s.sequence())
        .put((byte) s.reason().ordinal())
        .putLong(s.tick())
        .put((byte) s.learned())
        .put((byte) s.points());
    for (long c : s.cooldowns()) b.putLong(c);
    b.put((byte) (s.action() == null ? 0 : 1));
    if (s.action() != null) writeAction(b, s.action());
    b.put((byte) (s.queued() == null ? 0 : 1));
    if (s.queued() != null)
      b.putLong(s.queued().sequence()).put((byte) s.queued().skill().ordinal());
    return b.array();
  }

  public static State decodeState(byte[] bytes) {
    ByteBuffer b = bounded(bytes, 61, 164);
    long sequence = b.getLong();
    int reason = Byte.toUnsignedInt(b.get());
    if (reason >= Reason.values().length) throw invalid();
    long tick = b.getLong();
    int learned = Byte.toUnsignedInt(b.get()), points = Byte.toUnsignedInt(b.get());
    List<Long> cooldowns = new ArrayList<>();
    for (int i = 0; i < 5; i++) cooldowns.add(b.getLong());
    int present = Byte.toUnsignedInt(b.get());
    if (present > 1 || b.remaining() < (present == 1 ? 95 : 1)) throw invalid();
    Action action = present == 1 ? readAction(b) : null;
    int queued = Byte.toUnsignedInt(b.get());
    if (queued > 1 || b.remaining() != (queued == 1 ? 9 : 0)) throw invalid();
    Queued next =
        queued == 1 ? new Queued(b.getLong(), Skill.fromId(Byte.toUnsignedInt(b.get()))) : null;
    return new State(
        sequence, Reason.values()[reason], tick, learned, points, cooldowns, action, next);
  }

  public static byte[] encodeEvent(Event e) {
    ByteBuffer b = ByteBuffer.allocate(108 + 12 * e.hits().size());
    b.putLong(e.tick()).putInt(e.actor()).put((byte) e.phase().ordinal());
    writeAction(b, e.action());
    b.put((byte) e.hits().size());
    for (Hit h : e.hits()) b.putInt(h.target()).putInt(h.damage()).putInt(h.health());
    return b.array();
  }

  public static Event decodeEvent(byte[] bytes) {
    ByteBuffer b = bounded(bytes, 108, 144);
    long tick = b.getLong();
    int actor = b.getInt(), phase = Byte.toUnsignedInt(b.get());
    if (phase >= Phase.values().length) throw invalid();
    Action a = readAction(b);
    int count = Byte.toUnsignedInt(b.get());
    if (count > 3 || b.remaining() != 12 * count) throw invalid();
    List<Hit> hits = new ArrayList<>();
    for (int i = 0; i < count; i++) hits.add(new Hit(b.getInt(), b.getInt(), b.getInt()));
    return new Event(tick, actor, Phase.values()[phase], a, hits);
  }

  private static void writeAction(ByteBuffer b, Action a) {
    b.putLong(a.id())
        .put((byte) a.skill().ordinal())
        .putLong(a.start())
        .putDouble(a.x())
        .putDouble(a.y())
        .putDouble(a.z());
    Motion m = a.motion();
    b.putDouble(m.originX())
        .putDouble(m.originY())
        .putDouble(m.originZ())
        .putDouble(m.px())
        .putDouble(m.py())
        .putDouble(m.pz())
        .putFloat(m.yaw())
        .put((byte) ((m.blocked() ? 1 : 0) | (m.disabled() ? 2 : 0)));
  }

  private static Action readAction(ByteBuffer b) {
    return new Action(
        b.getLong(),
        Skill.fromId(Byte.toUnsignedInt(b.get())),
        b.getLong(),
        b.getDouble(),
        b.getDouble(),
        b.getDouble(),
        readMotion(b));
  }

  private static Motion readMotion(ByteBuffer b) {
    double ox = b.getDouble(),
        oy = b.getDouble(),
        oz = b.getDouble(),
        x = b.getDouble(),
        y = b.getDouble(),
        z = b.getDouble();
    float yaw = b.getFloat();
    int flags = Byte.toUnsignedInt(b.get());
    if (flags > 3) throw invalid();
    return new Motion(ox, oy, oz, x, y, z, yaw, (flags & 1) != 0, (flags & 2) != 0);
  }

  private static ByteBuffer bounded(byte[] bytes, int min, int max) {
    if (bytes == null || bytes.length < min || bytes.length > max || bytes.length > MAX_BYTES)
      throw invalid();
    return ByteBuffer.wrap(bytes);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Malformed skill payload");
  }
}
