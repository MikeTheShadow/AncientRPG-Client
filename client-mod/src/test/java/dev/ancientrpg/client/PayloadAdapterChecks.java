package dev.ancientrpg.client;

import dev.ancientrpg.protocol.Compatibility;
import dev.ancientrpg.protocol.Interaction;
import dev.ancientrpg.protocol.InteractionResult;
import dev.ancientrpg.protocol.WireCodec;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.network.FriendlyByteBuf;

/** Cross-checks actual Minecraft buffer codecs against the public wire contract. */
public final class PayloadAdapterChecks {
  private static int assertions;

  public static void main(String[] args) {
    run();
    System.out.println("Fabric payload adapter assertions passed: " + assertions);
  }

  public static void run() {
    assertions = 0;
    check(
        DemoPayloads.Hello.TYPE.id().toString().equals(WireCodec.HANDSHAKE_CHANNEL),
        "Handshake channel namespace");
    check(
        DemoPayloads.Activate.TYPE.id().toString().equals(WireCodec.INTERACTION_CHANNEL),
        "Interaction channel namespace");
    check(
        DemoPayloads.Result.TYPE.id().toString().equals(WireCodec.RESULT_CHANNEL),
        "Result channel namespace");
    var identity = new Compatibility(1, 1, "test", "00".repeat(32));
    var hello = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DemoPayloads.Hello.CODEC.encode(hello, new DemoPayloads.Hello(identity));
      byte[] actual = new byte[hello.readableBytes()];
      hello.getBytes(hello.readerIndex(), actual);
      check(
          Arrays.equals(actual, WireCodec.encodeCompatibility(identity)),
          "Handshake matches shared codec");
      check(
          DemoPayloads.Hello.CODEC.decode(hello).identity().equals(identity),
          "Handshake roundtrip");
      check(hello.readableBytes() == 0, "Decoder consumes exact body");
    } finally {
      hello.release();
    }
    var request =
        new FriendlyByteBuf(
            Unpooled.wrappedBuffer(
                HexFormat.of().parseHex("0000000000000001ffffffff0000000200000003")));
    try {
      var decoded = DemoPayloads.Activate.CODEC.decode(request);
      check(decoded.request().equals(new Interaction(1, -1, 2, 3)), "Request golden fixture");
    } finally {
      request.release();
    }
    var result =
        new FriendlyByteBuf(
            Unpooled.wrappedBuffer(HexFormat.of().parseHex("00000000000000010001")));
    try {
      check(
          DemoPayloads.Result.CODEC
              .decode(result)
              .result()
              .equals(new InteractionResult(1, InteractionResult.Status.APPLIED, true)),
          "Result golden fixture");
    } finally {
      result.release();
    }
    for (int length : new int[] {0, 1, 19, 21, 257}) {
      var invalid = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[length]));
      try {
        boolean rejected = false;
        try {
          DemoPayloads.Activate.CODEC.decode(invalid);
        } catch (IllegalArgumentException expected) {
          rejected = true;
        }
        check(rejected, "Invalid request length rejected: " + length);
      } finally {
        invalid.release();
      }
    }
    var oversized = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[1025]));
    try {
      boolean rejected = false;
      try {
        DemoPayloads.Hello.CODEC.decode(oversized);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      check(
          rejected && oversized.readerIndex() == 0,
          "Oversized handshake rejected before body copy");
    } finally {
      oversized.release();
    }
  }

  private static void check(boolean condition, String description) {
    assertions++;
    if (!condition) throw new AssertionError(description);
  }
}
