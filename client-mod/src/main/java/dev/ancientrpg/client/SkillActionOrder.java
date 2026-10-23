package dev.ancientrpg.client;

import java.util.HashMap;
import java.util.Map;

/** Terminal action IDs prevent delayed snapshots from restoring a completed control lock. */
final class SkillActionOrder {
  private final Map<Integer, Long> finished = new HashMap<>();

  boolean accepts(int actor, long action) {
    return action > finished.getOrDefault(actor, -1L);
  }

  void finish(int actor, long action) {
    finished.merge(actor, action, Math::max);
  }

  void clear() {
    finished.clear();
  }
}
