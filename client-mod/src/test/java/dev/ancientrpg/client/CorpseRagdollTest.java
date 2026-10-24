package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class CorpseRagdollTest {
  private static final CorpseRagdoll.Collider FLOOR =
      (x, y, z, r) -> new CorpseRagdoll.Push(0, Math.max(0, r - y), 0);

  @Test
  void independentJointMotionStaysAttachedAndFreezesByDeadline() {
    var a = new CorpseRagdoll(1, 0);
    var b = new CorpseRagdoll(2, 0);
    var calls = new AtomicInteger();
    for (int frame = 1; frame < 120; frame++) {
      double age = frame / 60.0;
      a.advance(
          CorpseRagdoll.STEP,
          age,
          (x, y, z, r) -> {
            calls.incrementAndGet();
            return FLOOR.push(x, y, z, r);
          });
      b.advance(CorpseRagdoll.STEP, age, FLOOR);
      assertTrue(a.steps() <= 4);
      assertTrue(a.queries() <= 4 * 4 * 18);
      var parts = a.pose().parts();
      assertEquals(6, parts.size());
      assertEquals(parts.get(0).x(), parts.get(1).x());
      assertEquals(parts.get(0).y(), parts.get(1).y());
      assertEquals(parts.get(0).z(), parts.get(1).z());
      for (var p : parts) {
        assertTrue(Float.isFinite(p.pitch()));
        assertTrue(Float.isFinite(p.roll()));
        assertTrue(Math.abs(p.roll()) < 2);
      }
      if (frame == 30) {
        assertNotEquals(a.pose(), b.pose());
        assertNotEquals(parts.get(2).pitch(), parts.get(3).pitch());
      }
    }
    a.advance(CorpseRagdoll.STEP, 2, FLOOR);
    assertTrue(a.stopped());
    assertEquals(Math.PI / 2, a.pose().pitch(), 1e-8);
    assertEquals(0, a.pose().roll());
    var settled = a.pose();
    int previous = calls.get();
    for (int i = 0; i < 300; i++) a.advance(1, 3 + i, FLOOR);
    assertSame(settled, a.pose());
    assertEquals(previous, calls.get());
    assertEquals(0, a.queries());
  }

  @Test
  void frameBudgetAndLateOrStalledFallbackNeverCatchUp() {
    var body = new CorpseRagdoll(1, 0);
    body.advance(CorpseRagdoll.STEP * 4, .066666666, FLOOR);
    assertEquals(4, body.steps());
    body.advance(.5, .6, FLOOR);
    assertTrue(body.stopped());
    assertTrue(body.fallback());
    assertEquals(0, body.steps());
    assertEquals(0, body.queries());
    var late = new CorpseRagdoll(1, 3);
    assertTrue(late.stopped());
    assertEquals(Math.PI / 2, late.pose().pitch());
    assertSame(CorpseRagdoll.fallback(5), CorpseRagdoll.fallback(300));
  }

  @Test
  void twentyCandidatesNeverExceedSixActiveAndInvisibleBodiesDoNotRestart() {
    var pool = new CorpseRagdollPool();
    var ids = new ArrayList<UUID>();
    int accepted = 0;
    for (int i = 0; i < 20; i++) {
      var id = new UUID(0, i);
      ids.add(id);
      if (pool.start(id, i, 0) != null) accepted++;
    }
    assertEquals(6, accepted);
    for (var id : ids) pool.visible(id, 0, FLOOR);
    pool.finishFrame(1_000_000_000L);
    assertEquals(6, pool.stats().active());
    for (var id : ids) pool.visible(id, .05, FLOOR);
    pool.finishFrame(1_050_000_000L);
    assertTrue(pool.stats().substeps() <= 24);
    assertTrue(pool.stats().collisionQueries() <= 1728);
    pool.finishFrame(1_066_666_667L);
    assertEquals(0, pool.stats().active());
    assertEquals(0, pool.stats().collisionQueries());
    assertNull(pool.start(UUID.randomUUID(), 1, 2));
    pool.clear();
    assertEquals(0, pool.stats().active());
  }

  @Test
  void stairsWallsAndEdgesProduceFiniteBoundedContacts() {
    var floor = new AABB(-4, -1, -4, 4, 0, 4);
    var step = new AABB(.3, 0, .2, 1, .5, 1);
    var wall = new AABB(-1, 0, .8, 1, 3, 1);
    for (var obstacles :
        List.of(
            List.of(floor),
            List.of(floor, step),
            List.of(floor, wall),
            List.of(new AABB(-1, -1, -1, 0, 0, 1)))) {
      CorpseRagdoll.Collider collider =
          (x, y, z, r) -> {
            var q = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
            double dx = 0, dy = 0, dz = 0;
            for (var box : obstacles) {
              var p = CorpseTerrain.push(q.move(dx, dy, dz), box);
              dx += p.x();
              dy += p.y();
              dz += p.z();
            }
            return new CorpseRagdoll.Push(dx, dy, dz);
          };
      var body = new CorpseRagdoll(18, 0);
      for (int i = 1; i <= 120; i++) body.advance(1.0 / 60, i / 60.0, collider);
      assertTrue(body.stopped());
      assertEquals(Math.PI / 2, body.pose().pitch());
      assertTrue(body.pose().height() <= .91);
      for (int part = 0; part < 6; part++)
        for (int sample = 0; sample < 3; sample++) {
          var point = CorpseRagdoll.sample(body.pose(), part, sample);
          assertTrue(Double.isFinite(point.lengthSquared()));
          assertTrue(
              Math.hypot(point.x, point.z) < 1.01, "Corpse sample left interaction footprint");
        }
    }
    var push = CorpseTerrain.push(new AABB(-.1, -.1, -.1, .1, .1, .1), floor);
    assertTrue(push.y() > 0);
    assertEquals(0, push.x());
  }

  @Test
  void duplicateRegistrationAndInvalidTerrainCannotReplayOrCrashABody() {
    var pool = new CorpseRagdollPool();
    var id = UUID.randomUUID();
    var body = pool.start(id, 1, 0);
    assertSame(body, pool.start(id, 2, 0));
    body.advance(CorpseRagdoll.STEP, .1, (x, y, z, r) -> new CorpseRagdoll.Push(Double.NaN, 0, 0));
    assertTrue(body.stopped());
    assertTrue(body.fallback());
    assertNotNull(body.pose());
    var deadline = new CorpseRagdoll(3, 1.9);
    deadline.advance(
        CorpseRagdoll.STEP, 2, (x, y, z, r) -> new CorpseRagdoll.Push(Double.NaN, 0, 0));
    assertTrue(deadline.stopped());
    assertNotNull(deadline.pose());
  }
}
