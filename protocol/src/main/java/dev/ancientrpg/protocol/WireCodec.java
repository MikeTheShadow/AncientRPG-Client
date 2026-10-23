package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Fixed, big-endian bodies. Transport adapters must enforce bounds before allocating. */
public final class WireCodec {
  public static final String HANDSHAKE_CHANNEL = "ancientrpg:compatibility";
  public static final String INTERACTION_CHANNEL = "ancientrpg:interaction";
  public static final String RESULT_CHANNEL = "ancientrpg:interaction_result";
  public static final int HANDSHAKE_MAX_BYTES = 1024;
  public static final int REQUEST_MAX_BYTES = 256;
  public static final int RESPONSE_MAX_BYTES = 1024;

  private WireCodec() {}

  public static byte[] encodeCompatibility(Compatibility value) {
    byte[] release = value.release().getBytes(StandardCharsets.US_ASCII);
    return ByteBuffer.allocate(9 + release.length + 32)
        .putInt(value.minecraftProtocol())
        .putInt(value.wireVersion())
        .put((byte) release.length)
        .put(release)
        .put(value.manifestBytes())
        .array();
  }

  public static Compatibility decodeCompatibility(byte[] bytes) {
    ByteBuffer input = bounded(bytes, HANDSHAKE_MAX_BYTES);
    if (input.remaining() < 42) {
      throw invalid();
    }
    int minecraft = input.getInt();
    int wire = input.getInt();
    int length = Byte.toUnsignedInt(input.get());
    if (length < 1 || length > 64 || input.remaining() != length + 32) {
      throw invalid();
    }
    byte[] release = new byte[length];
    input.get(release);
    // Compatibility rejects non-ASCII/replacement characters as well as whitespace.
    byte[] fingerprint = new byte[32];
    input.get(fingerprint);
    return new Compatibility(
        minecraft,
        wire,
        new String(release, StandardCharsets.US_ASCII),
        HexFormat.of().formatHex(fingerprint));
  }

  public static byte[] encodeInteraction(Interaction value) {
    return ByteBuffer.allocate(20)
        .putLong(value.sequence())
        .putInt(value.x())
        .putInt(value.y())
        .putInt(value.z())
        .array();
  }

  public static Interaction decodeInteraction(byte[] bytes) {
    ByteBuffer input = bounded(bytes, REQUEST_MAX_BYTES);
    if (input.remaining() != 20) {
      throw invalid();
    }
    return new Interaction(input.getLong(), input.getInt(), input.getInt(), input.getInt());
  }

  public static byte[] encodeResult(InteractionResult value) {
    return ByteBuffer.allocate(10)
        .putLong(value.sequence())
        .put((byte) (value.status() == InteractionResult.Status.APPLIED ? 0 : 1))
        .put((byte) (value.active() ? 1 : 0))
        .array();
  }

  public static InteractionResult decodeResult(byte[] bytes) {
    ByteBuffer input = bounded(bytes, RESPONSE_MAX_BYTES);
    if (input.remaining() != 10) {
      throw invalid();
    }
    long sequence = input.getLong();
    int status = Byte.toUnsignedInt(input.get());
    int active = Byte.toUnsignedInt(input.get());
    if (status > 1 || active > 1) {
      throw invalid();
    }
    return new InteractionResult(
        sequence,
        status == 0 ? InteractionResult.Status.APPLIED : InteractionResult.Status.DENIED,
        active == 1);
  }

  private static ByteBuffer bounded(byte[] bytes, int maximum) {
    if (bytes == null || bytes.length > maximum) {
      throw invalid();
    }
    return ByteBuffer.wrap(bytes);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Malformed AncientRPG payload");
  }
}
