package dev.ancientrpg.protocol;

/** Public authored trajectories. Clients may present them; only the server authorizes travel. */
public final class SkillMotion {
  private SkillMotion() {}

  public static double distance(Skill skill, double tick) {
    if (!Double.isFinite(tick)) throw new IllegalArgumentException("Nonfinite tick");
    return switch (skill) {
      case QUICK_CUT -> segment(tick, 1, 4, .70);
      case SWEEPING_ARC -> segment(tick, 2, 7, 1.10);
      case PIERCING_THRUST -> segment(tick, 2, 6, 2.80);
      case TWIN_FANG -> segment(tick, 1, 4, .75) + segment(tick, 6, 9, .85);
      case CRESCENT_FINISH -> segment(tick, 5, 12, 2.20);
    };
  }

  public static double step(Skill skill, long tick) {
    return distance(skill, tick) - distance(skill, tick - 1);
  }

  private static double segment(double tick, int first, int last, double distance) {
    double u = Math.clamp((tick - first + 1) / (last - first + 1), 0, 1);
    return distance * u * u * (3 - 2 * u);
  }
}
