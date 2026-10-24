package dev.ancientrpg.client;

import dev.ancientrpg.protocol.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class CorpsePayloads {
  private CorpsePayloads() {}

  public record Request(CorpseRequest value) implements CustomPacketPayload {
    public static final Type<Request> TYPE = new Type<>(Identifier.parse(CorpseWire.REQUEST));
    public static final StreamCodec<FriendlyByteBuf, Request> CODEC =
        DemoPayloads.codec(
            CorpseWire.REQUEST_BYTES,
            p -> CorpseWire.encodeRequest(p.value()),
            b -> new Request(CorpseWire.decodeRequest(b)));

    public Type<Request> type() {
      return TYPE;
    }
  }

  public record State(CorpseWire.State value) implements CustomPacketPayload {
    public static final Type<State> TYPE = new Type<>(Identifier.parse(CorpseWire.STATE));
    public static final StreamCodec<FriendlyByteBuf, State> CODEC =
        DemoPayloads.codec(
            CorpseWire.STATE_BYTES,
            p -> CorpseWire.encodeState(p.value()),
            b -> new State(CorpseWire.decodeState(b)));

    public Type<State> type() {
      return TYPE;
    }
  }

  public record Event(CorpseWire.Event value) implements CustomPacketPayload {
    public static final Type<Event> TYPE = new Type<>(Identifier.parse(CorpseWire.EVENT));
    public static final StreamCodec<FriendlyByteBuf, Event> CODEC =
        DemoPayloads.codec(
            CorpseWire.EVENT_MAX_BYTES,
            p ->
                p.value().body() == null
                    ? CorpseWire.encodeRemoval(p.value().id())
                    : CorpseWire.encodeBody(p.value().body()),
            b -> new Event(CorpseWire.decodeEvent(b)));

    public Type<Event> type() {
      return TYPE;
    }
  }
}
