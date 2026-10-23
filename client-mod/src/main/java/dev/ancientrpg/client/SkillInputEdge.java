package dev.ancientrpg.client;

/** Tracks physical input even while screens suppress casting. */
public final class SkillInputEdge {
  private boolean held;

  public boolean update(boolean gameplay, boolean down) {
    boolean fire = gameplay && down && !held;
    held = down;
    return fire;
  }
}
