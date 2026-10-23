package dev.ancientrpg.protocol;

/** Public presentation metadata; execution and balance remain server-owned. */
public enum Skill {
  QUICK_CUT("Quick Cut", "Fast single-target slash", 10),
  SWEEPING_ARC("Sweeping Arc", "Wide sweep, up to three targets", 16),
  PIERCING_THRUST("Piercing Thrust", "Narrow, longer-reaching strike", 14),
  TWIN_FANG("Twin Fang", "Two independently resolved strikes", 18),
  CRESCENT_FINISH("Crescent Finish", "Heavy arc, up to three targets", 24);
  private final String title;
  private final String description;
  private final int duration;

  Skill(String title, String description, int duration) {
    this.title = title;
    this.description = description;
    this.duration = duration;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public int duration() {
    return duration;
  }

  public int bit() {
    return 1 << ordinal();
  }

  public static Skill fromId(int id) {
    if (id < 0 || id >= values().length) throw new IllegalArgumentException("Unknown skill");
    return values()[id];
  }
}
