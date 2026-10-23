package dev.ancientrpg.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Original keyframes shared by first-person hands and the vanilla player rig. Angles in radians.
 */
public final class SwordAnimation {
  public record Pose(
      float lean,
      float twist,
      float pitch,
      float yaw,
      float roll,
      float offArm,
      float stride,
      float thrust,
      float lift) {
    public Pose mix(Pose b, double t) {
      float u = (float) Math.clamp(t, 0, 1), v = 1 - u;
      return new Pose(
          lean * v + b.lean * u,
          twist * v + b.twist * u,
          pitch * v + b.pitch * u,
          yaw * v + b.yaw * u,
          roll * v + b.roll * u,
          offArm * v + b.offArm * u,
          stride * v + b.stride * u,
          thrust * v + b.thrust * u,
          lift * v + b.lift * u);
    }
  }

  private record Frame(int tick, Pose pose) {}

  public static final Pose REST = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0);

  private static Frame f(
      int t,
      double lean,
      double twist,
      double pitch,
      double yaw,
      double roll,
      double off,
      double stride,
      double thrust,
      double lift) {
    return new Frame(
        t,
        new Pose(
            (float) lean,
            (float) twist,
            (float) pitch,
            (float) yaw,
            (float) roll,
            (float) off,
            (float) stride,
            (float) thrust,
            (float) lift));
  }

  private static final Frame[][] TRACKS = {
    {
      new Frame(0, REST),
      f(2, -.1, -.25, -.5, .45, .35, -.3, .25, 0, 0),
      f(4, .12, .4, -1, -.6, -.8, -.7, .75, .15, 0),
      f(6, .08, .5, -.4, -1, -1, -.4, .3, 0, -.1),
      new Frame(10, REST)
    },
    {
      new Frame(0, REST),
      f(3, -.1, -.6, -.9, 1.1, .3, -.4, .2, 0, .1),
      f(7, .18, .6, -1.2, -.5, -.9, -.8, .8, .15, 0),
      f(10, .1, .8, -.5, -1.4, -1.2, -.5, .35, 0, -.1),
      new Frame(16, REST)
    },
    {
      new Frame(0, REST),
      f(2, -.12, -.15, -.4, .4, 0, -.4, .15, -.15, 0),
      f(6, .45, .1, -1.55, 0, 0, -.8, 1, .8, -.1),
      f(9, .25, .1, -.9, 0, 0, -.5, .4, .3, 0),
      new Frame(14, REST)
    },
    {
      new Frame(0, REST),
      f(2, -.1, -.3, -.6, .6, .4, -.4, .3, 0, 0),
      f(4, .15, .4, -1.1, -.7, -.8, -.6, .8, .2, 0),
      f(6, -.08, .4, -.7, -.5, -.3, -.4, -.3, 0, .1),
      f(9, .2, -.5, -1.2, .8, .9, -.7, -.8, .25, 0),
      f(12, .1, -.6, -.4, 1, 1, -.4, -.3, 0, 0),
      new Frame(18, REST)
    },
    {
      new Frame(0, REST),
      f(5, -.2, -.9, -1.4, 1, .6, -.8, .25, -.15, .2),
      f(12, .3, .8, -1.3, -.7, -1.1, -1, .9, .4, -.1),
      f(16, .2, 1, -.5, -1.4, -1.4, -.5, .4, .15, -.15),
      new Frame(24, REST)
    }
  };

  private SwordAnimation() {}

  public static Pose sample(Skill skill, double tick) {
    if (!Double.isFinite(tick)) return REST;
    Frame[] frames = TRACKS[skill.ordinal()];
    if (tick <= 0 || tick >= skill.duration()) return REST;
    for (int i = 1; i < frames.length; i++)
      if (tick <= frames[i].tick()) {
        Frame a = frames[i - 1], b = frames[i];
        double u = (tick - a.tick()) / (b.tick() - a.tick());
        return a.pose().mix(b.pose(), u * u * (3 - 2 * u));
      }
    return REST;
  }

  public static boolean striking(Skill skill, double tick) {
    int[] contacts =
        switch (skill) {
          case QUICK_CUT -> new int[] {4};
          case SWEEPING_ARC -> new int[] {7};
          case PIERCING_THRUST -> new int[] {6};
          case TWIN_FANG -> new int[] {4, 9};
          case CRESCENT_FINISH -> new int[] {12};
        };
    for (int contact : contacts) if (tick >= contact - 1 && tick <= contact + 2) return true;
    return false;
  }

  public static byte[] manifest() {
    StringBuilder s = new StringBuilder("AncientRPG-motion-v1\n");
    for (Skill skill : Skill.values()) {
      s.append(skill.name()).append('\n');
      for (Frame f : TRACKS[skill.ordinal()])
        s.append(f.tick()).append(':').append(f.pose()).append('\n');
      for (int t = 0; t <= skill.duration(); t++)
        s.append(Double.toHexString(SkillMotion.distance(skill, t))).append('\n');
    }
    return s.toString().getBytes(StandardCharsets.US_ASCII);
  }
}
