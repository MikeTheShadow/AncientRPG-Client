package dev.ancientrpg.client;

import java.util.*;

/**
 * One bounded simulation batch per extracted frame; invisible/settled bodies release their slot.
 */
public final class CorpseRagdollPool {
  public static final int LIMIT = 6;

  private static final class Active {
    final CorpseRagdoll body;
    boolean seen;
    double age;
    CorpseRagdoll.Collider collision;

    Active(CorpseRagdoll body) {
      this.body = body;
    }
  }

  public record Stats(int active, int substeps, int collisionQueries, long nanos) {}

  private final Map<UUID, Active> active = new LinkedHashMap<>();
  private Stats stats = new Stats(0, 0, 0, 0);
  private long previous;

  public CorpseRagdoll start(UUID id, long seed, double age) {
    var known = active.get(id);
    if (known != null) return known.body;
    if (age >= CorpseRagdoll.DURATION || active.size() >= LIMIT) return null;
    var body = new CorpseRagdoll(seed, age);
    active.put(id, new Active(body));
    return body;
  }

  public void visible(UUID id, double age, CorpseRagdoll.Collider collision) {
    var entry = active.get(id);
    if (entry == null) return;
    entry.seen = true;
    entry.age = age;
    entry.collision = collision;
  }

  public void remove(UUID id) {
    active.remove(id);
  }

  public void clear() {
    active.clear();
    previous = 0;
    stats = new Stats(0, 0, 0, 0);
  }

  public Stats stats() {
    return stats;
  }

  public void finishFrame(long now) {
    double dt = previous == 0 ? 0 : (now - previous) / 1_000_000_000.0;
    previous = now;
    long started = System.nanoTime();
    int count = 0, steps = 0, queries = 0;
    for (var iterator = active.values().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (!entry.seen) {
        entry.body.abandon(entry.age + Math.max(0, dt));
        iterator.remove();
        continue;
      }
      entry.seen = false;
      count++;
      entry.body.advance(dt, entry.age, entry.collision);
      steps += entry.body.steps();
      queries += entry.body.queries();
      if (entry.body.stopped()) iterator.remove();
    }
    stats = new Stats(count, steps, queries, System.nanoTime() - started);
  }
}
