package dev.ancientrpg.client;

import static dev.ancientrpg.client.ClientText.tr;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.network.chat.Component;
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
  private final SkillActionOrder order = new SkillActionOrder();
  private final Map<Integer, Long> effectTicks = new HashMap<>();
  private LongSupplier sequence;
  private SkillWire.State state;
  private long serverTick, receivedAt;
  private Component feedback = Component.empty();
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
    SkillPresentation.reset();
    serverTick = 0;
    actions.clear();
    order.clear();
    effectTicks.clear();
    pendingUnlock = -1;
    feedback = Component.empty();
  }

  private void clock(long tick) {
    if (tick >= serverTick) {
      serverTick = tick;
      receivedAt = System.nanoTime();
    }
  }

  public SkillWire.Action action(int actor) {
    return actions.get(actor);
  }

  public long serverTick() {
    return serverTick;
  }

  public boolean committed() {
    var p = Minecraft.getInstance().player;
    return p != null && actions.containsKey(p.getId());
  }

  public double visualTick() {
    return serverTick + Math.min(1, Math.max(0, (System.nanoTime() - receivedAt) / 50_000_000.0));
  }

  private void accept(SkillWire.State value) {
    if (value.tick() < serverTick || (state != null && value.tick() < state.tick())) return;
    state = value;
    clock(value.tick());
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) {
      var previous = actions.get(mc.player.getId());
      if (value.action() == null
          || (order.accepts(mc.player.getId(), value.action().id())
              && (previous == null || value.action().id() >= previous.id()))) {
        SkillPresentation.changed(mc.player.getId(), previous, value.action(), value.tick());
        if (value.action() == null) actions.remove(mc.player.getId());
        else actions.put(mc.player.getId(), value.action());
      }
    }
    if (value.sequence() == pendingUnlock) pendingUnlock = -1;
    if (value.sequence() >= 0
        && value.reason() != SkillWire.Reason.OK
        && value.reason() != SkillWire.Reason.QUEUED) message(reason(value.reason()));
  }

  private void accept(SkillWire.Event event) {
    long previous = serverTick;
    clock(event.tick());
    SkillWire.Action current = actions.get(event.actor());
    if (current != null && current.id() > event.action().id()) return;
    switch (event.phase()) {
      case START, SNAPSHOT -> {
        if (order.accepts(event.actor(), event.action().id())
            && event.tick() >= previous
            && event.tick() - event.action().start() < event.action().skill().duration()) {
          SkillPresentation.changed(event.actor(), current, event.action(), event.tick());
          actions.put(event.actor(), event.action());
        }
      }
      case END, CANCEL, REMOVE -> {
        if (event.tick() < previous) return;
        if (event.phase() != SkillWire.Phase.REMOVE)
          order.finish(event.actor(), event.action().id());
        if (current == null || current.id() == event.action().id()) {
          SkillPresentation.changed(event.actor(), current, null, event.tick());
          actions.remove(event.actor());
          effectTicks.remove(event.actor());
        }
      }
      case HIT -> {
        if (event.tick() < previous || !order.accepts(event.actor(), event.action().id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var actor = mc.level.getEntity(event.actor());
        if (actor != null)
          mc.level.playLocalSound(
              actor, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.0f);
        if (mc.player != null && event.actor() == mc.player.getId()) {
          int damage = event.hits().stream().mapToInt(SkillWire.Hit::damage).sum();
          message(damage == 0 ? tr("skills.miss") : tr("skills.hit", damage, event.hits().size()));
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
      message(tr("skills.unlock_timeout"));
    }
    if (mc.level == null) return;
    actions.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null);
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
      if (!dev.ancientrpg.protocol.SwordAnimation.striking(a.skill(), elapsed)) continue;
      var pose = dev.ancientrpg.protocol.SwordAnimation.sample(a.skill(), elapsed);
      double angle = pose.yaw() + pose.roll() * .35;
      double reach = 1.2 + pose.thrust();
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
    SkillPresentation.hands(pose);
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
          tr(
              "hud.binding_skill",
              binding(i),
              tr("skill." + skill.name().toLowerCase(Locale.ROOT) + ".short")),
          x + 4,
          y + 4,
          learned ? 0xFF80DDD0 : 0xFF888888);
      Component status =
          !learned
              ? tr("hud.locked")
              : remaining > 0 ? ClientText.seconds(remaining / 20.0, 1) : tr("hud.ready");
      g.text(mc.font, status, x + 4, y + 16, 0xFFE7EDF0);
    }
    if (state.queued() != null)
      g.text(
          mc.font,
          tr("hud.next", ClientText.skill(state.queued().skill())),
          left,
          y - 40,
          0xFF80DDD0);
    if (state.action() != null) {
      double p =
          Math.clamp(
              (visualTick() - state.action().start()) / state.action().skill().duration(), 0, 1);
      g.fill(left, y - 5, left + (int) (width * 5 * p), y - 2, 0xFFE8BD69);
      g.text(mc.font, ClientText.skill(state.action().skill()), left, y - 16, 0xFFE8BD69);
    }
    if (System.nanoTime() < feedbackUntil) g.text(mc.font, feedback, left, y - 28, 0xFFFFFFFF);
    g.text(mc.font, tr("hud.tree", binding(5)), left, y + 31, 0xFFA7B8BB);
  }

  private void message(Component text) {
    feedback = text;
    feedbackUntil = System.nanoTime() + 2_000_000_000L;
  }

  public Component feedback() {
    return System.nanoTime() < feedbackUntil ? feedback : Component.empty();
  }

  private static Component reason(SkillWire.Reason r) {
    return tr("skills.reason." + r.name().toLowerCase(Locale.ROOT));
  }
}
