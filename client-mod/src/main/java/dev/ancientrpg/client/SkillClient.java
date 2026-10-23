package dev.ancientrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ancientrpg.protocol.Skill;
import dev.ancientrpg.protocol.SkillRequest;
import dev.ancientrpg.protocol.SkillWire;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

public final class SkillClient {
  public static final SkillClient INSTANCE = new SkillClient();
  private final KeyMapping[] keys = new KeyMapping[6];
  private final SkillInputEdge[] edges = new SkillInputEdge[6];
  private final Map<Integer, SkillWire.Action> actions = new HashMap<>();
  private final Map<Integer, Long> effectTicks = new HashMap<>();
  private LongSupplier sequence;
  private SkillWire.State state;
  private long serverTick, receivedAt;
  private String feedback = "";
  private long feedbackUntil;
  private long pendingUnlock = -1, pendingAt;

  private SkillClient() {}

  public SkillWire.State state() {
    return state;
  }

  public String binding(int i) {
    return keys[i].getTranslatedKeyMessage().getString();
  }

  public boolean pending() {
    return pendingUnlock >= 0;
  }

  public void install(LongSupplier sequence) {
    this.sequence = sequence;
    PayloadTypeRegistry.serverboundPlay().register(SkillPayloads.Use.TYPE, SkillPayloads.Use.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(SkillPayloads.Unlock.TYPE, SkillPayloads.Unlock.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(SkillPayloads.State.TYPE, SkillPayloads.State.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(SkillPayloads.Event.TYPE, SkillPayloads.Event.CODEC);
    ClientPlayNetworking.registerGlobalReceiver(
        SkillPayloads.State.TYPE, (p, c) -> c.client().execute(() -> accept(p.value())));
    ClientPlayNetworking.registerGlobalReceiver(
        SkillPayloads.Event.TYPE, (p, c) -> c.client().execute(() -> accept(p.value())));
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    int[] defaults = {90, 88, 67, 86, 66, 75};
    for (int i = 0; i < 6; i++) {
      edges[i] = new SkillInputEdge();
      String name = i == 5 ? "tree" : Skill.values()[i].name().toLowerCase(Locale.ROOT);
      keys[i] =
          KeyMappingHelper.registerKeyMapping(
              new KeyMapping(
                  "key.ancientrpg." + name,
                  InputConstants.Type.KEYSYM,
                  defaults[i],
                  KeyMapping.Category.MISC));
    }
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    HudElementRegistry.attachElementBefore(
        VanillaHudElements.CHAT, Identifier.parse("ancientrpg:skills"), (g, d) -> hud(g));
    AttackBlockCallback.EVENT.register(
        (p, w, h, pos, dir) ->
            p.getMainHandItem().is(DemoRegistration.sword)
                ? InteractionResult.FAIL
                : InteractionResult.PASS);
    AttackEntityCallback.EVENT.register(
        (p, w, h, e, hit) ->
            p.getMainHandItem().is(DemoRegistration.sword)
                ? InteractionResult.FAIL
                : InteractionResult.PASS);
  }

  private void reset() {
    state = null;
    serverTick = 0;
    actions.clear();
    effectTicks.clear();
    pendingUnlock = -1;
    feedback = "";
  }

  private void clock(long tick) {
    if (tick >= serverTick) {
      serverTick = tick;
      receivedAt = System.nanoTime();
    }
  }

  public double visualTick() {
    return serverTick + Math.min(1, Math.max(0, (System.nanoTime() - receivedAt) / 50_000_000.0));
  }

  private void accept(SkillWire.State value) {
    if (state != null && value.tick() < state.tick()) return;
    state = value;
    clock(value.tick());
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) {
      if (value.action() == null) actions.remove(mc.player.getId());
      else actions.put(mc.player.getId(), value.action());
    }
    if (value.sequence() == pendingUnlock) pendingUnlock = -1;
    if (value.sequence() >= 0 && value.reason() != SkillWire.Reason.OK)
      message(reason(value.reason()));
  }

  private void accept(SkillWire.Event event) {
    long previous = serverTick;
    clock(event.tick());
    SkillWire.Action current = actions.get(event.actor());
    if (current != null && current.id() > event.action().id()) return;
    switch (event.phase()) {
      case START, SNAPSHOT -> {
        if (event.tick() >= previous
            && event.tick() - event.action().start() < event.action().skill().duration())
          actions.put(event.actor(), event.action());
      }
      case END, CANCEL, REMOVE -> {
        actions.remove(event.actor());
        effectTicks.remove(event.actor());
      }
      case HIT -> {
        if (event.tick() < previous) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var actor = mc.level.getEntity(event.actor());
        if (actor != null)
          mc.level.playLocalSound(
              actor, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.0f);
        if (mc.player != null && event.actor() == mc.player.getId()) {
          int damage = event.hits().stream().mapToInt(SkillWire.Hit::damage).sum();
          message(
              damage == 0 ? "Miss" : damage + " damage · " + event.hits().size() + " target(s)");
        }
      }
    }
  }

  public void unlock(Skill skill) {
    if (pending() || state == null || !ClientPlayNetworking.canSend(SkillPayloads.Unlock.TYPE))
      return;
    pendingUnlock = sequence.getAsLong();
    pendingAt = System.nanoTime();
    ClientPlayNetworking.send(
        new SkillPayloads.Unlock(new SkillRequest(pendingUnlock, skill, true)));
  }

  private void tick(Minecraft mc) {
    boolean gameplay =
        mc.player != null
            && mc.gui.screen() == null
            && mc.gui.overlay() == null
            && mc.level != null
            && state != null;
    for (int i = 0; i < 6; i++) {
      var bound = KeyMappingHelper.getBoundKeyOf(keys[i]);
      boolean down =
          bound.getValue() >= 0
              && (bound.getType() == InputConstants.Type.MOUSE
                  ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                          mc.getWindow().handle(), bound.getValue())
                      == org.lwjgl.glfw.GLFW.GLFW_PRESS
                  : InputConstants.isKeyDown(mc.getWindow(), bound.getValue()));
      while (keys[i].consumeClick()) {
        /* Drain OS repeat; rising edge alone submits. */
      }
      if (edges[i].update(gameplay, down)) {
        if (i == 5) {
          mc.gui.setScreen(new SkillTreeScreen());
          gameplay = false;
        } else if (ClientPlayNetworking.canSend(SkillPayloads.Use.TYPE))
          ClientPlayNetworking.send(
              new SkillPayloads.Use(
                  new SkillRequest(sequence.getAsLong(), Skill.values()[i], false)));
      }
    }
    if (pending() && System.nanoTime() - pendingAt > 2_000_000_000L) {
      pendingUnlock = -1;
      message("Unlock not confirmed; try again");
    }
    if (mc.level == null) return;
    actions
        .entrySet()
        .removeIf(
            e ->
                mc.level.getEntity(e.getKey()) == null
                    || serverTick - e.getValue().start() >= e.getValue().skill().duration());
    effectTicks.keySet().retainAll(actions.keySet());
    for (var e : actions.entrySet()) {
      var actor = mc.level.getEntity(e.getKey());
      var a = e.getValue();
      if (effectTicks.getOrDefault(e.getKey(), -1L) == serverTick) continue;
      effectTicks.put(e.getKey(), serverTick);
      double elapsed = serverTick - a.start();
      if (elapsed < 0 || elapsed >= a.skill().duration()) continue;
      Vec3 aim = new Vec3(a.x(), a.y(), a.z());
      Vec3 side = aim.cross(new Vec3(0, 1, 0)).normalize();
      if (side.lengthSqr() < 0.1) side = new Vec3(1, 0, 0);
      Vec3 up = side.cross(aim).normalize();
      double phase = elapsed / a.skill().duration();
      double angle =
          switch (a.skill()) {
            case QUICK_CUT -> -0.8 + phase * 1.6;
            case SWEEPING_ARC -> -1.3 + phase * 2.6;
            case PIERCING_THRUST -> 0;
            case TWIN_FANG -> (phase < 0.5 ? -1 + phase * 4 : 3 - phase * 4);
            case CRESCENT_FINISH -> -1.6 + phase * 3.2;
          };
      double reach =
          a.skill() == Skill.PIERCING_THRUST ? 0.5 + Math.sin(phase * Math.PI) * 2.5 : 1.5;
      Vec3 tip =
          actor
              .getEyePosition()
              .add(aim.scale(Math.cos(angle) * reach))
              .add(side.scale(Math.sin(angle) * reach));
      if (a.skill() == Skill.CRESCENT_FINISH) tip = tip.add(up.scale(0.4 * Math.sin(angle)));
      mc.level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, 0, 0);
    }
  }

  public void transform(PoseStack pose) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;
    var a = actions.get(mc.player.getId());
    if (a == null) return;
    double t = (visualTick() - a.start()) / a.skill().duration();
    if (t < 0 || t >= 1) return;
    float wave = (float) Math.sin(t * Math.PI);
    switch (a.skill()) {
      case QUICK_CUT -> {
        pose.mulPose(Axis.ZP.rotationDegrees(65 * wave));
        pose.translate(-0.3 * wave, 0, 0);
      }
      case SWEEPING_ARC -> {
        pose.mulPose(Axis.YP.rotationDegrees(100 * wave));
        pose.translate(-0.5 * wave, 0, 0);
      }
      case PIERCING_THRUST -> {
        pose.mulPose(Axis.XP.rotationDegrees(-35 * wave));
        pose.translate(0, 0, -0.8 * wave);
      }
      case TWIN_FANG -> {
        float cut = (float) Math.sin(t * Math.PI * 2);
        pose.mulPose(Axis.ZP.rotationDegrees(75 * cut));
        pose.translate(-0.25 * cut, 0, 0);
      }
      case CRESCENT_FINISH -> {
        pose.mulPose(Axis.ZP.rotationDegrees(-110 * wave));
        pose.mulPose(Axis.XP.rotationDegrees(-45 * wave));
        pose.translate(0, 0.3 * wave, -0.3 * wave);
      }
    }
  }

  private void hud(GuiGraphicsExtractor g) {
    Minecraft mc = Minecraft.getInstance();
    if (state == null || mc.player == null) return;
    int width = Math.min(76, (g.guiWidth() - 16) / 5),
        left = (g.guiWidth() - width * 5) / 2,
        y = g.guiHeight() - 86;
    for (Skill skill : Skill.values()) {
      int i = skill.ordinal(), x = left + i * width;
      boolean learned = (state.learned() & skill.bit()) != 0;
      long remaining = Math.max(0, state.cooldowns().get(i) - serverTick);
      g.fill(x, y, x + width - 2, y + 28, learned ? 0xCC153D43 : 0xCC252932);
      g.text(
          mc.font,
          binding(i) + " " + new String[] {"Cut", "Arc", "Thrust", "Twin", "Finish"}[i],
          x + 4,
          y + 4,
          learned ? 0xFF80DDD0 : 0xFF888888);
      String status =
          !learned
              ? "Locked"
              : remaining > 0 ? String.format(Locale.ROOT, "%.1fs", remaining / 20.0) : "Ready";
      g.text(mc.font, status, x + 4, y + 16, 0xFFE7EDF0);
    }
    if (state.action() != null) {
      double p =
          Math.clamp(
              (visualTick() - state.action().start()) / state.action().skill().duration(), 0, 1);
      g.fill(left, y - 5, left + (int) (width * 5 * p), y - 2, 0xFFE8BD69);
      g.text(mc.font, state.action().skill().title(), left, y - 16, 0xFFE8BD69);
    }
    if (System.nanoTime() < feedbackUntil) g.text(mc.font, feedback, left, y - 28, 0xFFFFFFFF);
    g.text(mc.font, binding(5) + " · Sword tree", left, y + 31, 0xFFA7B8BB);
  }

  private void message(String text) {
    feedback = text;
    feedbackUntil = System.nanoTime() + 2_000_000_000L;
  }

  public String feedback() {
    return System.nanoTime() < feedbackUntil ? feedback : "";
  }

  private static String reason(SkillWire.Reason r) {
    return switch (r) {
      case LOCKED -> "Learn this skill first";
      case PREREQUISITES -> "Learn the required skills first";
      case POINTS -> "No practice points remaining";
      case ALREADY_LEARNED -> "Already learned";
      case BUSY -> "Finish your current action";
      case COOLDOWN -> "Skill is cooling down";
      case INELIGIBLE -> "Hold the practice sword with an empty offhand";
      case QUEUE_PRESSURE -> "Too many requests; try again";
      case DUPLICATE -> "Request already handled";
      case OK -> "";
    };
  }
}
