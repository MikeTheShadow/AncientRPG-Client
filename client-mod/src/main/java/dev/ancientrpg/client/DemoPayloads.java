package dev.ancientrpg.client;

import dev.ancientrpg.protocol.Compatibility;
import dev.ancientrpg.protocol.Interaction;
import dev.ancientrpg.protocol.InteractionResult;
import dev.ancientrpg.protocol.WireCodec;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class DemoPayloads {
  private DemoPayloads() {}

  public record Hello(Compatibility identity) implements CustomPacketPayload {
    public static final Type<Hello> TYPE =
        new Type<>(Identifier.parse(WireCodec.HANDSHAKE_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, Hello> CODEC =
        codec(
            WireCodec.HANDSHAKE_MAX_BYTES,
            p -> WireCodec.encodeCompatibility(p.identity()),
            bytes -> new Hello(WireCodec.decodeCompatibility(bytes)));

    @Override
    public Type<Hello> type() {
      return TYPE;
    }
  }

  public record Activate(Interaction request) implements CustomPacketPayload {
    public static final Type<Activate> TYPE =
        new Type<>(Identifier.parse(WireCodec.INTERACTION_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, Activate> CODEC =
        codec(
            WireCodec.REQUEST_MAX_BYTES,
            p -> WireCodec.encodeInteraction(p.request()),
            bytes -> new Activate(WireCodec.decodeInteraction(bytes)));

    @Override
    public Type<Activate> type() {
      return TYPE;
    }
  }

  public record Result(InteractionResult result) implements CustomPacketPayload {
    public static final Type<Result> TYPE = new Type<>(Identifier.parse(WireCodec.RESULT_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, Result> CODEC =
        codec(
            WireCodec.RESPONSE_MAX_BYTES,
            p -> WireCodec.encodeResult(p.result()),
            bytes -> new Result(WireCodec.decodeResult(bytes)));

    @Override
    public Type<Result> type() {
      return TYPE;
    }
  }

  static <T> StreamCodec<FriendlyByteBuf, T> codec(
      int maximum, Function<T, byte[]> encode, Function<byte[], T> decode) {
    return StreamCodec.of(
        (buffer, value) -> {
          byte[] bytes = encode.apply(value);
          if (bytes.length > maximum) throw new IllegalArgumentException("Payload exceeds limit");
          buffer.writeBytes(bytes);
        },
        buffer -> {
          int length = buffer.readableBytes();
          if (length > maximum) throw new IllegalArgumentException("Payload exceeds limit");
          byte[] bytes = new byte[length];
          buffer.readBytes(bytes);
          return decode.apply(bytes);
        });
  }
}
