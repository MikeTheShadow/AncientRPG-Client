package dev.ancientrpg.client;

import static dev.ancientrpg.client.ClientText.tr;

import dev.ancientrpg.protocol.InventoryRequest;
import dev.ancientrpg.protocol.InventoryWire;
import java.util.Locale;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;

/** Presentation cache only. Moves wait for the server's durable authoritative response. */
public final class InventoryClient {
  public static final InventoryClient INSTANCE = new InventoryClient();
  private InventoryWire.State state;
  private LongSupplier sequence;
  private long pending = -1, pendingAt, lastResponse = -1;
  private Component feedback = tr("inventory.waiting");

  private InventoryClient() {}

  public InventoryWire.State state() {
    return state;
  }

  public boolean pending() {
    return pending >= 0;
  }

  public Component feedback() {
    return feedback;
  }

  public boolean connected() {
    return ClientPlayNetworking.canSend(InventoryPayloads.Request.TYPE);
  }

  public void install(LongSupplier sequence) {
    this.sequence = sequence;
    PayloadTypeRegistry.serverboundPlay()
        .register(InventoryPayloads.Request.TYPE, InventoryPayloads.Request.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(InventoryPayloads.State.TYPE, InventoryPayloads.State.CODEC);
    ClientPlayNetworking.registerGlobalReceiver(
        InventoryPayloads.State.TYPE, (p, c) -> c.client().execute(() -> accept(p.value())));
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (pending >= 0 && System.nanoTime() - pendingAt > 5_000_000_000L) {
            pending = -1;
            feedback = tr("inventory.timeout");
          }
        });
  }

  void accept(InventoryWire.State incoming) {
    if (state != null && incoming.revision() < state.revision()) return;
    if (incoming.sequence() >= 0 && incoming.sequence() < lastResponse) return;
    state = incoming;
    if (incoming.sequence() >= 0) lastResponse = incoming.sequence();
    if (incoming.sequence() == pending) pending = -1;
    feedback = tr("inventory.result." + incoming.result().name().toLowerCase(Locale.ROOT));
  }

  private void reset() {
    state = null;
    pending = -1;
    lastResponse = -1;
    feedback = tr("inventory.waiting");
  }

  public void sync() {
    send(true, 0, 0);
  }

  public void move(int from, int to) {
    if (state == null) {
      sync();
      return;
    }
    send(false, from, to);
  }

  private void send(boolean sync, int from, int to) {
    if (!connected() || pending() || CorpseClient.INSTANCE.pending()) return;
    pending = sequence.getAsLong();
    pendingAt = System.nanoTime();
    ClientPlayNetworking.send(
        new InventoryPayloads.Request(
            new InventoryRequest(pending, state == null ? 0 : state.revision(), sync, from, to)));
    feedback = tr(sync ? "inventory.synchronizing" : "inventory.saving");
  }
}
