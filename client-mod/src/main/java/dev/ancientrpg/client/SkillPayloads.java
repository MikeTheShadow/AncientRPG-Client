package dev.ancientrpg.client;

import dev.ancientrpg.protocol.SkillRequest;
import dev.ancientrpg.protocol.SkillWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class SkillPayloads {
  private SkillPayloads() {}

  public record Use(SkillRequest request) implements CustomPacketPayload {
    public static final Type<Use> TYPE = new Type<>(Identifier.parse(SkillWire.USE));
    public static final StreamCodec<FriendlyByteBuf, Use> CODEC =
        DemoPayloads.codec(
            9,
            p -> SkillWire.encodeRequest(p.request()),
            b -> new Use(SkillWire.decodeRequest(b, false)));

    public Type<Use> type() {
      return TYPE;
    }
  }

  public record Unlock(SkillRequest request) implements CustomPacketPayload {
    public static final Type<Unlock> TYPE = new Type<>(Identifier.parse(SkillWire.UNLOCK));
    public static final StreamCodec<FriendlyByteBuf, Unlock> CODEC =
        DemoPayloads.codec(
            9,
            p -> SkillWire.encodeRequest(p.request()),
            b -> new Unlock(SkillWire.decodeRequest(b, true)));

    public Type<Unlock> type() {
      return TYPE;
    }
  }

  public record State(SkillWire.State value) implements CustomPacketPayload {
    public static final Type<State> TYPE = new Type<>(Identifier.parse(SkillWire.STATE));
    public static final StreamCodec<FriendlyByteBuf, State> CODEC =
        DemoPayloads.codec(
            SkillWire.MAX_BYTES,
            p -> SkillWire.encodeState(p.value()),
            b -> new State(SkillWire.decodeState(b)));

    public Type<State> type() {
      return TYPE;
    }
  }

  public record Event(SkillWire.Event value) implements CustomPacketPayload {
    public static final Type<Event> TYPE = new Type<>(Identifier.parse(SkillWire.EVENT));
    public static final StreamCodec<FriendlyByteBuf, Event> CODEC =
        DemoPayloads.codec(
            SkillWire.MAX_BYTES,
            p -> SkillWire.encodeEvent(p.value()),
            b -> new Event(SkillWire.decodeEvent(b)));

    public Type<Event> type() {
      return TYPE;
    }
  }
}
