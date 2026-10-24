package dev.ancientrpg.client;

import static dev.ancientrpg.client.ClientText.tr;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ancientrpg.protocol.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/** Nearby cosmetic bodies and authorized loot cache only. Ownership stays on the server. */
public final class CorpseClient {
  public static final CorpseClient INSTANCE = new CorpseClient();

  private static final class Visual {
    CorpseWire.Body body;
    long received;
    RemotePlayer player;

    Visual(CorpseWire.Body body) {
      this.body = body;
      received = System.nanoTime();
    }

    long elapsed() {
      return Math.max(0, (System.nanoTime() - received) / 1_000_000);
    }

    double progress() {
      return Math.clamp((body.ageMillis() + elapsed()) / 1200.0, 0, 1);
    }
  }

  private final Map<Integer, Visual> bodies = new HashMap<>();
  private LongSupplier sequence;
  private KeyMapping interact;
  private final SkillInputEdge interactEdge = new SkillInputEdge();
  private UUID current;
  private CorpseWire.State state;
  private long pending = -1, pendingAt, received, lastResponse = -1;
  private boolean opening;
  private Component feedback = Component.empty();

  private CorpseClient() {}

  public boolean pending() {
    return pending >= 0;
  }

  CorpseWire.State state() {
    return state;
  }

  Component feedback() {
    return feedback;
  }

  int remaining() {
    return state == null
        ? 0
        : (int) Math.max(0, state.remainingMillis() - (System.nanoTime() - received) / 1_000_000);
  }

  int lockRemaining() {
    return state == null
        ? 0
        : (int) Math.max(0, state.lockMillis() - (System.nanoTime() - received) / 1_000_000);
  }

  Component title() {
    if (current != null)
      for (var v : bodies.values())
        if (v.body.id().equals(current)) return tr("loot.body_name", v.body.name());
    return tr("loot.title");
  }

  public void install(LongSupplier sequence) {
    this.sequence = sequence;
    interact =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.ancientrpg.interact",
                InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_F,
                KeyMapping.Category.MISC));
    HudElementRegistry.attachElementBefore(
        VanillaHudElements.CHAT,
        Identifier.parse("ancientrpg:corpse_interact"),
        (g, delta) -> {
          var mc = Minecraft.getInstance();
          if (target(mc) == null) return;
          var label = tr("loot.interact", interact.getTranslatedKeyMessage());
          g.text(
              mc.font,
              label,
              (g.guiWidth() - mc.font.width(label)) / 2,
              g.guiHeight() / 2 + 18,
              0xFFF0E9DB);
        });
    PayloadTypeRegistry.serverboundPlay()
        .register(CorpsePayloads.Request.TYPE, CorpsePayloads.Request.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(CorpsePayloads.State.TYPE, CorpsePayloads.State.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(CorpsePayloads.Event.TYPE, CorpsePayloads.Event.CODEC);
    ClientPlayNetworking.registerGlobalReceiver(
        CorpsePayloads.State.TYPE, (p, c) -> c.client().execute(() -> accept(p.value())));
    ClientPlayNetworking.registerGlobalReceiver(
        CorpsePayloads.Event.TYPE, (p, c) -> c.client().execute(() -> event(p.value())));
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          tickInput(c);
          if (pending() && System.nanoTime() - pendingAt > 5_000_000_000L) {
            pending = -1;
            feedback = tr("inventory.timeout");
          }
          if (opening && !pending() && !InventoryClient.INSTANCE.pending()) {
            opening = false;
            refresh();
          }
        });
  }

  private boolean gameplay(Minecraft mc) {
    return mc.player != null
        && mc.player.isAlive()
        && mc.level != null
        && mc.gui.screen() == null
        && mc.gui.overlay() == null
        && ClientPlayNetworking.canSend(CorpsePayloads.Request.TYPE)
        && !SkillClient.INSTANCE.committed()
        && !pending()
        && !InventoryClient.INSTANCE.pending();
  }

  private Visual target(Minecraft mc) {
    if (!gameplay(mc) || !(mc.hitResult instanceof EntityHitResult hit)) return null;
    var visual = bodies.get(hit.getEntity().getId());
    if (visual == null
        || visual.body.remainingMillis() <= visual.elapsed()
        || hit.getEntity().getBoundingBox().distanceToSqr(mc.player.getEyePosition()) > 9)
      return null;
    return visual;
  }

  private void tickInput(Minecraft mc) {
    var key = KeyMappingHelper.getBoundKeyOf(interact);
    boolean down =
        key.getValue() >= 0
            && (key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(mc.getWindow(), key.getValue()));
    while (interact.consumeClick()) {
      /* Physical rising edge submits once. */
    }
    if (!interactEdge.update(gameplay(mc), down)) return;
    var visual = target(mc);
    if (visual != null) {
      open(visual.body.id());
      mc.gui.setScreen(new CorpseLootScreen());
    }
  }

  /** Reserve a shared F binding for corpse interaction only during compatible sessions. */
  public void suppressNativeSwap(Minecraft mc) {
    if (interact != null
        && ClientPlayNetworking.canSend(CorpsePayloads.Request.TYPE)
        && mc.options.keySwapOffhand.same(interact))
      while (mc.options.keySwapOffhand.consumeClick()) {}
  }

  private void reset() {
    bodies.clear();
    current = null;
    state = null;
    pending = -1;
    lastResponse = -1;
    opening = false;
    feedback = Component.empty();
  }

  private void open(UUID id) {
    current = id;
    state = null;
    feedback = tr("inventory.synchronizing");
    opening = true;
  }

  void refresh() {
    if (current != null)
      send(new CorpseRequest(nextSequence(), CorpseRequest.OPEN, current, 0, 0, 0, 0));
  }

  void take(int source, int target) {
    if (state == null || !state.canLoot() || remaining() == 0) return;
    send(
        new CorpseRequest(
            nextSequence(),
            CorpseRequest.TAKE,
            current,
            state.revision(),
            InventoryClient.INSTANCE.state().revision(),
            source,
            target));
  }

  void takeAll() {
    if (state == null || !state.canLoot() || remaining() == 0) return;
    send(
        new CorpseRequest(
            nextSequence(),
            CorpseRequest.TAKE_ALL,
            current,
            state.revision(),
            InventoryClient.INSTANCE.state().revision(),
            0,
            0));
  }

  void close() {
    if (current != null
        && !pending()
        && !InventoryClient.INSTANCE.pending()
        && ClientPlayNetworking.canSend(CorpsePayloads.Request.TYPE))
      ClientPlayNetworking.send(
          new CorpsePayloads.Request(
              new CorpseRequest(nextSequence(), CorpseRequest.CLOSE, current, 0, 0, 0, 0)));
    current = null;
    state = null;
    opening = false;
  }

  private long nextSequence() {
    return sequence.getAsLong();
  }

  private void send(CorpseRequest request) {
    if (!ClientPlayNetworking.canSend(CorpsePayloads.Request.TYPE)
        || pending()
        || InventoryClient.INSTANCE.pending()) return;
    pending = request.sequence();
    pendingAt = System.nanoTime();
    ClientPlayNetworking.send(new CorpsePayloads.Request(request));
    feedback = tr("loot.waiting");
  }

  private void accept(CorpseWire.State incoming) {
    if (incoming.sequence() == pending) pending = -1;
    if (current == null
        || !incoming.corpse().equals(current)
        || incoming.sequence() >= 0 && incoming.sequence() < lastResponse) return;
    if (incoming.sequence() >= 0) lastResponse = incoming.sequence();
    if (state != null
        && incoming.revision() < state.revision()
        && (incoming.result() == CorpseWire.Result.OPEN
            || incoming.result() == CorpseWire.Result.APPLIED)) return;
    state = incoming;
    received = System.nanoTime();
    feedback = tr("loot.result." + incoming.result().name().toLowerCase(Locale.ROOT));
  }

  private void event(CorpseWire.Event event) {
    if (event.body() == null) {
      bodies.entrySet().removeIf(e -> e.getValue().body.id().equals(event.id()));
      if (event.id().equals(current)) {
        state = null;
        feedback = tr("loot.result.gone");
      }
      return;
    }
    var old = bodies.get(event.body().entity());
    if (old == null || !old.body.id().equals(event.id()))
      bodies.put(event.body().entity(), new Visual(event.body()));
    else {
      old.body = event.body();
      old.received = System.nanoTime();
    }
  }

  public static boolean isBody(Entity entity) {
    return isBody(entity.getId());
  }

  public static boolean isBody(int entityId) {
    return INSTANCE.bodies.containsKey(entityId);
  }

  public static boolean hideOriginal(Entity entity) {
    return !entity.isAlive()
        && INSTANCE.bodies.values().stream().anyMatch(v -> v.body.actor().equals(entity.getUUID()));
  }

  public static EntityRenderState render(
      EntityRenderDispatcher dispatcher, Entity entity, float partial) {
    var visual = INSTANCE.bodies.get(entity.getId());
    var mc = Minecraft.getInstance();
    if (visual == null || mc.level == null) return null;
    if (visual.player == null || visual.player.level() != mc.level) {
      var info =
          mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(visual.body.actor());
      var profile =
          info == null
              ? new GameProfile(visual.body.actor(), visual.body.name())
              : info.getProfile();
      visual.player = new RemotePlayer(mc.level, profile);
      visual.player.setId(entity.getId());
    }
    var fake = visual.player;
    var body = visual.body;
    fake.setPos(body.x(), body.y(), body.z());
    fake.setYRot(body.yaw());
    fake.yBodyRot = fake.yBodyRotO = body.yaw();
    fake.setOldPosAndRot();
    var slots =
        new EquipmentSlot[] {
          EquipmentSlot.MAINHAND,
          EquipmentSlot.OFFHAND,
          EquipmentSlot.HEAD,
          EquipmentSlot.CHEST,
          EquipmentSlot.LEGS,
          EquipmentSlot.FEET
        };
    for (int i = 0; i < slots.length; i++) {
      var e = body.equipment().get(i);
      fake.setItemSlot(
          slots[i],
          e.empty()
              ? ItemStack.EMPTY
              : new ItemStack(DemoRegistration.inventoryItems.get(e.item()), e.quantity()));
    }
    var state = dispatcher.getPlayerRenderer(fake).createRenderState(fake, partial);
    double p = visual.progress(), smooth = p * p * (3 - 2 * p);
    state.x = body.x();
    state.y = body.y() + .08 + .18 * smooth;
    state.z = body.z();
    state.bodyRot = body.yaw() + (float) (180 * smooth);
    state.deathTime = (float) (1 + 12.5 * smooth * smooth);
    state.walkAnimationSpeed = 0;
    state.walkAnimationPos = 0;
    state.ageInTicks = 0;
    state.nameTag = null;
    state.scoreText = null;
    state.isInvisible = false;
    state.isInvisibleToPlayer = false;
    state.hasRedOverlay = false;
    state.isSpectator = false;
    state.displayFireAnimation = false;
    return state;
  }

  public static void pose(HumanoidModel<?> model, AvatarRenderState state) {
    var v = INSTANCE.bodies.get(state.id);
    if (v == null) return;
    float buckle = (float) Math.sin(v.progress() * Math.PI);
    model.body.xRot = .45f * buckle;
    model.head.xRot = .25f * buckle;
    model.head.yRot = 0;
    model.rightLeg.xRot = model.leftLeg.xRot = -.65f * buckle;
    model.rightArm.xRot = model.leftArm.xRot = -.5f * buckle;
    model.rightArm.zRot = .15f + .6f * buckle;
    model.leftArm.zRot = -.15f - .6f * buckle;
  }
}
