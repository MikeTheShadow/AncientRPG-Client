package dev.ancientrpg.client;

import java.util.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Six articulated angular bodies. Local cosmetic transforms never change an entity's position. */
public final class CorpseRagdoll {
  public static final double STEP = 1.0 / 60, DURATION = 2;
  public static final int MAX_STEPS = 4, ITERATIONS = 4;
  private static final double HALF_PI = Math.PI / 2, SCALE = .9375;
  private static final double[][] JOINTS = {
    {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9, 12, 0}, {1.9, 12, 0}
  };
  private static final double[][] CENTERS = {
    {0, 6, 0}, {0, -4, 0}, {-1, 4, 0}, {1, 4, 0}, {0, 6, 0}, {0, 6, 0}
  };
  private static final double[] RADII = {.20, .24, .12, .12, .12, .12};

  public record Part(float x, float y, float z, float pitch, float yaw, float roll) {}

  public record Pose(
      double height, double backward, double yaw, double pitch, double roll, List<Part> parts) {
    public Pose {
      parts = List.copyOf(parts);
    }
  }

  public record Push(double x, double y, double z) {
    public static final Push NONE = new Push(0, 0, 0);

    public double lengthSquared() {
      return x * x + y * y + z * z;
    }
  }

  @FunctionalInterface
  public interface Collider {
    Push push(double x, double y, double z, double radius);
  }

  private final double[][] angle = new double[6][3], velocity = new double[6][3];
  private final double twist;
  private double pitch, pitchVelocity, roll, rollVelocity, lift, accumulator;
  private boolean stopped, fallback;
  private Pose pose;
  private int steps, queries;

  public CorpseRagdoll(long seed, double age) {
    if (!Double.isFinite(age) || age < 0) throw new IllegalArgumentException("Invalid corpse age");
    var random = new Random(seed);
    twist = (random.nextDouble() - .5) * .24;
    double p = Math.clamp(age / 1.2, 0, 1);
    double buckle = Math.sin(p * Math.PI);
    angle[0][0] = .35 * buckle;
    angle[1][0] = .2 * buckle;
    angle[2][0] = angle[3][0] = -.5 * buckle;
    angle[4][0] = angle[5][0] = -.65 * buckle;
    angle[2][2] = .15 + .6 * buckle;
    angle[3][2] = -.15 - .6 * buckle;
    pitch = HALF_PI * smooth(p);
    pitchVelocity = 2.1 + random.nextDouble() * .6;
    rollVelocity = twist;
    for (int i = 0; i < 6; i++) {
      velocity[i][0] = (random.nextDouble() - .5) * (i < 2 ? 1.5 : 7);
      velocity[i][2] = (random.nextDouble() - .5) * (i < 2 ? 1 : 5);
    }
    if (age >= DURATION) {
      stopped = true;
      fallback = true;
    }
    pose = fallback(age);
  }

  public Pose pose() {
    return pose;
  }

  public boolean stopped() {
    return stopped;
  }

  public boolean fallback() {
    return fallback;
  }

  public int steps() {
    return steps;
  }

  public int queries() {
    return queries;
  }

  public void abandon(double age) {
    stopped = true;
    fallback = true;
    pose = fallback(age);
  }

  /** dt belongs to one render frame; a stall selects fallback instead of a catch-up loop. */
  public void advance(double dt, double age, Collider world) {
    steps = queries = 0;
    if (stopped) return;
    if (!Double.isFinite(dt) || dt < 0 || dt > STEP * MAX_STEPS + 1e-7) {
      abandon(age);
      return;
    }
    if (age >= DURATION) {
      settle(world);
      return;
    }
    accumulator += dt;
    while (accumulator + 1e-9 >= STEP && steps < MAX_STEPS) {
      integrate(age - (accumulator - STEP));
      for (int iteration = 0; iteration < ITERATIONS; iteration++) {
        Pose candidate = snapshot();
        boolean contact = false;
        for (int part = 0; part < 6; part++)
          for (int sample = 0; sample < 3; sample++) {
            Vector3d point = sample(candidate, part, sample);
            // Never let a cosmetic flail leave the authoritative interaction footprint.
            if (Math.hypot(point.x, point.z) + RADII[part] > 1) {
              angle[part][0] *= .6;
              angle[part][1] *= .6;
              angle[part][2] *= .6;
              velocity[part][0] *= -.1;
              velocity[part][2] *= -.1;
            }
            queries++;
            Push push = world.push(point.x, point.y, point.z, RADII[part]);
            if (!Double.isFinite(push.lengthSquared()) || push.lengthSquared() > 4) {
              abandon(age);
              return;
            }
            if (push.lengthSquared() < 1e-10) continue;
            contact = true;
            if (push.y() > 0) lift = Math.min(.65, lift + push.y());
            // Contact torque is applied at the attached joint rather than detaching body parts.
            angle[part][0] = Math.clamp(angle[part][0] + push.z() * .8, -.9, .9);
            angle[part][2] = Math.clamp(angle[part][2] - push.x() * .8, -.8, .8);
            velocity[part][0] *= -.18;
            velocity[part][2] *= -.18;
            if (part < 2 && push.y() > 0 && pitch > .65 && pitchVelocity > 1) pitchVelocity *= -.15;
            if (Math.abs(push.x()) + Math.abs(push.z()) > .015) {
              rollVelocity += push.x() * .4;
              pitchVelocity -= Math.abs(push.z()) * .4;
            }
          }
        if (!contact) break;
      }
      pose = snapshot();
      accumulator -= STEP;
      steps++;
    }
  }

  private void integrate(double age) {
    double rest = Math.clamp((age - 1.05) / .65, 0, 1), damping = 4 + rest * 12;
    pitchVelocity +=
        (4.8 * Math.sin(pitch) + rest * 35 * (HALF_PI - pitch) - damping * .32 * pitchVelocity)
            * STEP;
    pitch = Math.clamp(pitch + pitchVelocity * STEP, 0, HALF_PI);
    rollVelocity += (-18 * roll - 5 * rollVelocity) * STEP;
    roll = Math.clamp(roll + rollVelocity * STEP, -.12, .12);
    lift = Math.max(0, lift - .65 * STEP);
    for (int i = 0; i < 6; i++)
      for (int axis = 0; axis < 3; axis++) {
        double limit = i < 2 ? .35 : .95;
        double target = axis == 2 && i == 2 ? .17 : axis == 2 && i == 3 ? -.17 : 0;
        // Gravity on each off-center rigid part plus body acceleration drives the joints.
        double gravity =
            axis == 0
                ? Math.sin(pitch + angle[i][axis]) * (i == 1 ? -2 : 3)
                : axis == 2 ? twist * Math.sin(pitch) * (i % 2 == 0 ? 1 : -1) : 0;
        velocity[i][axis] +=
            (gravity * (1 - rest)
                    - (10 + rest * 60) * (angle[i][axis] - target)
                    - damping * velocity[i][axis])
                * STEP;
        angle[i][axis] = Math.clamp(angle[i][axis] + velocity[i][axis] * STEP, -limit, limit);
      }
  }

  private Pose snapshot() {
    return build(.08 + .18 * Math.sin(pitch) + lift, .90 * Math.sin(pitch), 0, pitch, roll, angle);
  }

  private void settle(Collider world) {
    // Try a finite set of face-up headings against coarse terrain, keeping the best clearance.
    Pose best = null;
    double bestCost = Double.POSITIVE_INFINITY;
    for (int heading = 0; heading < 8; heading++) {
      Pose candidate = build(.26, .90, heading * Math.PI / 4, HALF_PI, 0, new double[6][3]);
      double required = 0, cost = 0;
      for (int i = 0; i < 6; i++)
        for (int s = 0; s < 3; s++) {
          var p = sample(candidate, i, s);
          queries++;
          var push = world.push(p.x, p.y, p.z, RADII[i]);
          if (!Double.isFinite(push.lengthSquared())) {
            abandon(DURATION);
            return;
          }
          required = Math.max(required, push.y());
          cost += Math.abs(push.x()) + Math.abs(push.z()) + Math.max(0, push.y());
        }
      if (cost < bestCost) {
        bestCost = cost;
        best =
            build(
                .26 + Math.min(.65, required),
                .90,
                heading * Math.PI / 4,
                HALF_PI,
                0,
                new double[6][3]);
      }
      if (cost < 1e-8) break;
    }
    pose = best;
    stopped = true;
  }

  public static Pose fallback(double age) {
    if (age >= 1.2) return Rest.POSE;
    double p = Math.clamp(age / 1.2, 0, 1), smooth = smooth(p), buckle = Math.sin(p * Math.PI);
    var a = new double[6][3];
    a[0][0] = .35 * buckle;
    a[1][0] = .2 * buckle;
    a[2][0] = a[3][0] = -.5 * buckle;
    a[2][2] = .15 + .6 * buckle;
    a[3][2] = -.15 - .6 * buckle;
    a[4][0] = a[5][0] = -.65 * buckle;
    return build(.08 + .18 * smooth, .90 * smooth, 0, HALF_PI * smooth, 0, a);
  }

  private static final class Rest {
    static final Pose POSE = build(.26, .90, 0, HALF_PI, 0, new double[6][3]);
  }

  private static double smooth(double p) {
    return p * p * (3 - 2 * p);
  }

  private static Pose build(
      double height, double backward, double yaw, double pitch, double roll, double[][] angles) {
    var parts = new ArrayList<Part>(6);
    var torso = new Quaterniond().rotationXYZ(angles[0][0], angles[0][1], angles[0][2]);
    for (int i = 0; i < 6; i++) {
      Vector3d pivot = new Vector3d(JOINTS[i]);
      if (i < 4) pivot.sub(0, 12, 0).rotate(torso).add(0, 12, 0);
      Quaterniond rotation =
          i == 0
              ? new Quaterniond(torso)
              : i < 4
                  ? new Quaterniond(torso).rotateXYZ(angles[i][0], angles[i][1], angles[i][2])
                  : new Quaterniond().rotationXYZ(angles[i][0], angles[i][1], angles[i][2]);
      var euler = rotation.getEulerAnglesZYX(new Vector3d());
      parts.add(
          new Part(
              (float) pivot.x,
              (float) pivot.y,
              (float) pivot.z,
              (float) euler.x,
              (float) euler.y,
              (float) euler.z));
    }
    return new Pose(height, backward, yaw, pitch, roll, parts);
  }

  /** Coarse capsule samples in the same local axes/scale as the pinned player renderer. */
  public static Vector3d sample(Pose pose, int part, int sample) {
    Part bone = pose.parts().get(part);
    double f = sample == 0 ? .25 : sample == 1 ? 1 : 1.75;
    var point = new Vector3d(CENTERS[part]);
    if (part == 1) point.mul(1);
    else point.mul(f);
    point
        .rotate(new Quaterniond().rotationZYX(bone.roll(), bone.yaw(), bone.pitch()))
        .add(bone.x(), bone.y(), bone.z())
        .div(16);
    point.set(-point.x * SCALE, (1.501 - point.y) * SCALE, point.z * SCALE);
    point
        .rotateZ(pose.roll())
        .rotateX(pose.pitch())
        .add(0, pose.height(), -pose.backward())
        .rotateY(pose.yaw());
    return point;
  }
}
