package dev.ancientrpg.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;

/** Coarse sphere/AABB contacts against the real block collision shapes, including slabs/stairs. */
final class CorpseTerrain {
  private CorpseTerrain() {}

  static CorpseRagdoll.Collider collider(
      ClientLevel level, double x, double y, double z, double yaw) {
    double sin = Math.sin(yaw), cos = Math.cos(yaw);
    return (px, py, pz, r) -> {
      double wx = x + px * cos + pz * sin, wz = z - px * sin + pz * cos;
      var query = new AABB(wx - r, y + py - r, wz - r, wx + r, y + py + r, wz + r);
      double dx = 0, dy = 0, dz = 0;
      int boxes = 0;
      for (var shape : level.getBlockCollisions(null, query))
        for (var box : shape.toAabbs()) {
          if (++boxes > 32) return new CorpseRagdoll.Push(Double.NaN, 0, 0);
          var push = push(query.move(dx, dy, dz), box);
          dx += push.x();
          dy += push.y();
          dz += push.z();
        }
      return new CorpseRagdoll.Push(dx * cos - dz * sin, dy, dx * sin + dz * cos);
    };
  }

  static CorpseRagdoll.Push push(AABB moving, AABB obstacle) {
    if (!moving.intersects(obstacle)) return CorpseRagdoll.Push.NONE;
    double x = nearest(obstacle.minX - moving.maxX, obstacle.maxX - moving.minX);
    double y = nearest(obstacle.minY - moving.maxY, obstacle.maxY - moving.minY);
    double z = nearest(obstacle.minZ - moving.maxZ, obstacle.maxZ - moving.minZ);
    if (Math.abs(y) <= Math.abs(x) && Math.abs(y) <= Math.abs(z))
      return new CorpseRagdoll.Push(0, y + Math.copySign(.0001, y), 0);
    return Math.abs(x) < Math.abs(z)
        ? new CorpseRagdoll.Push(x + Math.copySign(.0001, x), 0, 0)
        : new CorpseRagdoll.Push(0, 0, z + Math.copySign(.0001, z));
  }

  private static double nearest(double a, double b) {
    return Math.abs(a) < Math.abs(b) ? a : b;
  }
}
