package dev.ancientrpg.client;

import dev.ancientrpg.protocol.InventoryRequest;
import dev.ancientrpg.protocol.InventoryWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class InventoryPayloads {
  private InventoryPayloads() {}

  public record Request(InventoryRequest value) implements CustomPacketPayload {
    public static final Type<Request> TYPE = new Type<>(Identifier.parse(InventoryWire.REQUEST));
    public static final StreamCodec<FriendlyByteBuf, Request> CODEC =
        DemoPayloads.codec(
            InventoryWire.REQUEST_BYTES,
            p -> InventoryWire.encodeRequest(p.value()),
            b -> new Request(InventoryWire.decodeRequest(b)));

    public Type<Request> type() {
      return TYPE;
    }
  }

  public record State(InventoryWire.State value) implements CustomPacketPayload {
    public static final Type<State> TYPE = new Type<>(Identifier.parse(InventoryWire.STATE));
    public static final StreamCodec<FriendlyByteBuf, State> CODEC =
        DemoPayloads.codec(
            InventoryWire.STATE_BYTES,
            p -> InventoryWire.encodeState(p.value()),
            b -> new State(InventoryWire.decodeState(b)));

    public Type<State> type() {
      return TYPE;
    }
  }
}
