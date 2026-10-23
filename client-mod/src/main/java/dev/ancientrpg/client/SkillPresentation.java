package dev.ancientrpg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ancientrpg.protocol.SkillMotion;
import dev.ancientrpg.protocol.SkillWire;
import dev.ancientrpg.protocol.SwordAnimation;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Rendering/prediction only; server snapshots determine movement and commitment. */
public final class SkillPresentation {
  private record Blend(long action, long start, SwordAnimation.Pose previous) {}

  private static final Map<Integer, Blend> BLENDS = new HashMap<>();
  private static final Map<Integer, Double> SPEED = new HashMap<>();
  private static final Map<Integer, SkillWire.Motion> PREVIOUS = new HashMap<>();
  private static final Map<Integer, SkillWire.Action> ENDED = new HashMap<>();
  private static final Map<Integer, Long> SAMPLES = new HashMap<>();
  private static boolean moving;

  private SkillPresentation() {}

  public static void reset() {
    BLENDS.clear();
    SPEED.clear();
    PREVIOUS.clear();
    ENDED.clear();
    SAMPLES.clear();
    moving = false;
  }

  public static void changed(
      int actor, SkillWire.Action previous, SkillWire.Action next, long tick) {
    if (previous != null && next == null && tick == previous.start() + previous.skill().duration())
      ENDED.put(actor, previous);
    if (previous == null && next != null) {
      var ended = ENDED.remove(actor);
      if (ended != null && ended.start() + ended.skill().duration() == next.start())
        previous = ended;
    }
    ENDED
        .entrySet()
        .removeIf(e -> tick > e.getValue().start() + e.getValue().skill().duration() + 2);
    if (previous != null && next != null && previous.id() != next.id())
      BLENDS.put(
          actor,
          new Blend(
              next.id(),
              tick,
              SwordAnimation.sample(previous.skill(), previous.skill().duration() - 2)));
    if (previous != null
        && next != null
        && previous.id() == next.id()
        && tick > SAMPLES.getOrDefault(actor, -1L)) {
      var a = previous.motion();
      var b = next.motion();
      PREVIOUS.put(actor, a);
      double distance = Math.hypot(b.px() - a.px(), b.pz() - a.pz());
      SPEED.put(actor, b.blocked() || b.disabled() ? 0 : Math.min(1, distance / .15));
    }
    if (next == null) {
      SPEED.remove(actor);
      PREVIOUS.remove(actor);
      SAMPLES.remove(actor);
    } else SAMPLES.put(actor, tick);
  }

  private static SwordAnimation.Pose pose(int actor, SkillWire.Action a) {
    var local = Minecraft.getInstance().player;
    double elapsed =
        SkillClient.INSTANCE.visualTick()
            - a.start()
            - (local != null && local.getId() == actor ? 0 : 1);
    var pose = SwordAnimation.sample(a.skill(), elapsed);
    Blend blend = BLENDS.get(actor);
    if (blend != null && blend.action() == a.id() && elapsed < 2)
      pose = blend.previous().mix(pose, elapsed / 2);
    else BLENDS.remove(actor);
    return pose;
  }

  public static void hands(PoseStack stack) {
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    var action = SkillClient.INSTANCE.action(mc.player.getId());
    if (action == null) return;
    var p = pose(mc.player.getId(), action);
    int sign = mc.player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    stack.translate(sign * (p.yaw() * .15), p.lift(), -p.thrust());
    stack.mulPose(Axis.XP.rotation(p.pitch() * .65f));
    stack.mulPose(Axis.YP.rotation(sign * p.yaw()));
    stack.mulPose(Axis.ZP.rotation(sign * p.roll()));
  }

  public static void body(HumanoidModel<?> model, AvatarRenderState state) {
    var action = SkillClient.INSTANCE.action(state.id);
    if (action == null) return;
    var mc = Minecraft.getInstance();
    var actor = mc.level == null ? null : mc.level.getEntity(state.id);
    boolean left =
        actor instanceof net.minecraft.world.entity.LivingEntity living
            && living.getMainArm() == HumanoidArm.LEFT;
    int sign = left ? -1 : 1;
    var p = pose(state.id, action);
    model.body.xRot = p.lean();
    model.body.yRot = sign * p.twist();
    var arm = left ? model.leftArm : model.rightArm;
    var off = left ? model.rightArm : model.leftArm;
    arm.xRot = p.pitch();
    arm.yRot = sign * (p.yaw() + p.twist());
    arm.zRot = sign * p.roll();
    off.xRot = p.offArm();
    off.yRot = sign * p.twist() * .5f;
    // Move shoulders with torso twist so hands/items follow the body instead of floating.
    model.rightArm.x = (float) (-5 * Math.cos(model.body.yRot));
    model.rightArm.z = (float) (5 * Math.sin(model.body.yRot));
    model.leftArm.x = -model.rightArm.x;
    model.leftArm.z = -model.rightArm.z;
    float stride = (float) (p.stride() * SPEED.getOrDefault(state.id, 0.0));
    model.rightLeg.xRot = sign * stride;
    model.leftLeg.xRot = -sign * stride;
  }

  public static void facing(AvatarRenderState state) {
    var action = SkillClient.INSTANCE.action(state.id);
    if (action == null) return;
    var local = Minecraft.getInstance().player;
    if (local == null || state.id != local.getId()) {
      var now = action.motion();
      var old = PREVIOUS.getOrDefault(state.id, now);
      double alpha =
          Math.clamp(SkillClient.INSTANCE.visualTick() - SkillClient.INSTANCE.serverTick(), 0, 1);
      state.x = old.px() + (now.px() - old.px()) * alpha;
      state.y = old.py() + (now.py() - old.py()) * alpha;
      state.z = old.pz() + (now.pz() - old.pz()) * alpha;
    }
    float head = state.bodyRot + state.yRot;
    state.bodyRot = action.motion().yaw();
    state.yRot = net.minecraft.util.Mth.wrapDegrees(head - state.bodyRot);
  }

  public static boolean interceptMove(LocalPlayer player) {
    if (moving || !SkillClient.INSTANCE.committed()) return false;
    var action = SkillClient.INSTANCE.action(player.getId());
    if (action == null) return false;
    var m = action.motion();
    double elapsed = SkillClient.INSTANCE.visualTick() - action.start();
    double confirmed = SkillClient.INSTANCE.serverTick() - action.start();
    double prediction =
        m.blocked() || m.disabled()
            ? 0
            : Math.max(
                0,
                SkillMotion.distance(action.skill(), Math.min(confirmed + 1, elapsed))
                    - SkillMotion.distance(action.skill(), confirmed));
    double yaw = Math.toRadians(m.yaw());
    Vec3 authoritative = new Vec3(m.px(), m.py(), m.pz());
    Vec3 predicted = authoritative.add(-Math.sin(yaw) * prediction, 0, Math.cos(yaw) * prediction);
    var level = player.level();
    var projected = player.getBoundingBox().move(predicted.subtract(player.position()));
    if (!level.noCollision(player, projected)
        || level.noCollision(player, projected.move(0, -.05, 0))) predicted = authoritative;
    Vec3 delta = predicted.subtract(player.position());
    boolean penetrating = !level.noCollision(player, player.getBoundingBox().deflate(1e-5));
    if (delta.lengthSqr() > 1 || penetrating) player.setPos(authoritative);
    else {
      // Vanilla movement supplies local collision, stepping and render interpolation.
      moving = true;
      try {
        player.move(MoverType.SELF, delta);
      } finally {
        moving = false;
      }
    }
    player.setDeltaMovement(Vec3.ZERO);
    player.setSprinting(false);
    return true;
  }
}
