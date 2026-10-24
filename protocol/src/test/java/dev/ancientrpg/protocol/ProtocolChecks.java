package dev.ancientrpg.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same contract checks run by JUnit and by the dependency-free fallback runner. */
public final class ProtocolChecks {
  private static int checks;
  private static final Compatibility ID = new Compatibility(1, 1, "test", "00".repeat(32));

  public static void main(String[] args) {
    run();
    System.out.println("Protocol checks passed: " + checks);
  }

  public static void run() {
    checks = 0;
    String hello = "00000001000000010474657374" + "00".repeat(32);
    eq(hello, HexFormat.of().formatHex(WireCodec.encodeCompatibility(ID)));
    eq(ID, WireCodec.decodeCompatibility(HexFormat.of().parseHex(hello)));
    Interaction request = new Interaction(1, -1, 2, 3);
    String fixture = "0000000000000001ffffffff0000000200000003";
    eq(fixture, HexFormat.of().formatHex(WireCodec.encodeInteraction(request)));
    eq(request, WireCodec.decodeInteraction(HexFormat.of().parseHex(fixture)));
    InteractionResult response = new InteractionResult(1, InteractionResult.Status.APPLIED, true);
    eq("00000000000000010001", HexFormat.of().formatHex(WireCodec.encodeResult(response)));
    eq(response, WireCodec.decodeResult(WireCodec.encodeResult(response)));
    eq(
        new InteractionResult(1, InteractionResult.Status.DENIED, false),
        WireCodec.decodeResult(HexFormat.of().parseHex("00000000000000010100")));
    rejects(() -> new InteractionResult(1, InteractionResult.Status.DENIED, true));
    rejects(() -> new Interaction(-1, 0, 0, 0));
    rejects(() -> new Compatibility(-1, 1, "test", "00".repeat(32)));
    rejects(() -> new Compatibility(1, 0, "test", "00".repeat(32)));
    for (String release : new String[] {"", "x".repeat(65), "bad release", "é", "a\n"}) {
      rejects(() -> new Compatibility(1, 1, release, "00".repeat(32)));
    }
    Compatibility maximum = new Compatibility(1, 1, "x".repeat(64), "ff".repeat(32));
    eq(maximum, WireCodec.decodeCompatibility(WireCodec.encodeCompatibility(maximum)));
    rejects(() -> new Compatibility(1, 1, "test", "A".repeat(64)));
    rejects(() -> new Compatibility(1, 1, "test", "0".repeat(63)));

    byte[] encoded = WireCodec.encodeCompatibility(ID);
    for (int length = 0; length < encoded.length; length++) {
      byte[] truncated = Arrays.copyOf(encoded, length);
      rejects(() -> WireCodec.decodeCompatibility(truncated));
    }
    rejects(() -> WireCodec.decodeCompatibility(Arrays.copyOf(encoded, encoded.length + 1)));
    rejects(() -> WireCodec.decodeCompatibility(new byte[1025]));
    byte[] invalidAscii = encoded.clone();
    invalidAscii[9] = (byte) 0xff;
    rejects(() -> WireCodec.decodeCompatibility(invalidAscii));
    for (int length = 0; length <= 257; length++) {
      if (length == 20) continue;
      byte[] malformed = new byte[length];
      rejects(() -> WireCodec.decodeInteraction(malformed));
    }
    rejects(() -> WireCodec.decodeInteraction(ByteBuffer.allocate(20).putLong(-1).array()));
    for (int length = 0; length <= 11; length++) {
      if (length == 10) continue;
      byte[] malformed = new byte[length];
      rejects(() -> WireCodec.decodeResult(malformed));
    }
    rejects(() -> WireCodec.decodeResult(new byte[1025]));
    rejects(() -> WireCodec.decodeResult(HexFormat.of().parseHex("00000000000000010200")));
    rejects(() -> WireCodec.decodeResult(HexFormat.of().parseHex("00000000000000010002")));
    rejects(() -> WireCodec.decodeCompatibility(null));
    rejects(() -> WireCodec.decodeInteraction(null));
    rejects(() -> WireCodec.decodeResult(null));

    Map<String, byte[]> first = new LinkedHashMap<>();
    first.put("assets/a.json", "one".getBytes(StandardCharsets.UTF_8));
    first.put("registry/blocks.json", "two".getBytes(StandardCharsets.UTF_8));
    Map<String, byte[]> reverse = new LinkedHashMap<>();
    reverse.put("registry/blocks.json", first.get("registry/blocks.json"));
    reverse.put("assets/a.json", first.get("assets/a.json"));
    eq(ContentManifest.fingerprint(first), ContentManifest.fingerprint(reverse));
    String before = ContentManifest.fingerprint(first);
    first.put("assets/a.json", "changed".getBytes(StandardCharsets.UTF_8));
    check(!before.equals(ContentManifest.fingerprint(first)), "Asset mutation must change hash");
    rejects(() -> ContentManifest.fingerprint(Map.of("../private", new byte[0])));
    rejects(() -> ContentManifest.fingerprint(Map.of("Assets/foo", new byte[0])));
    // Independent SHA-256 fixture generated from documented canonical framing.
    eq(
        "62b581615b4611cb5f3d970261c24090e3bec9e3fd60491c71fa409b0ffc0584",
        ContentManifest.fingerprint(Map.of()));
    var actual =
        PublicContent.identity(
            776, 1196, 32366, 32367, 1537, 1538, 1539, 1540, 1541, 1542, 1543, 1544, 1545, 1546);
    eq(actual, WireCodec.decodeCompatibility(WireCodec.encodeCompatibility(actual)));
    check(
        !actual
            .manifest()
            .equals(
                PublicContent.identity(
                        776, 1196, 32366, 32367, 1537, 1539, 1539, 1540, 1541, 1542, 1543, 1544,
                        1545, 1546)
                    .manifest()),
        "Training sword mapping participates in compatibility");
    check(
        !actual
            .manifest()
            .equals(
                PublicContent.identity(
                        776, 1196, 32367, 32366, 1537, 1538, 1539, 1540, 1541, 1542, 1543, 1544,
                        1545, 1546)
                    .manifest()),
        "Block state mapping participates in compatibility");
    check(
        !actual
            .manifest()
            .equals(
                PublicContent.identity(
                        776, 1196, 32366, 32367, 1538, 1538, 1539, 1540, 1541, 1542, 1543, 1544,
                        1545, 1546)
                    .manifest()),
        "Item ID participates in compatibility");
  }

  private static void rejects(Runnable action) {
    checks++;
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("Expected rejection");
  }

  private static void eq(Object expected, Object actual) {
    check(expected.equals(actual), "Expected " + expected + ", got " + actual);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
