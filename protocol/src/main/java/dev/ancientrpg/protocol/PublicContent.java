package dev.ancientrpg.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

/** Explicit allowlist: only public content and actual runtime IDs participate in the handshake. */
public final class PublicContent {
  public static final String BLOCK = "ancientrpg:demo_block";
  public static final String ITEM = "ancientrpg:demo_item";
  public static final String SWORD = "ancientrpg:training_sword";
  public static final int WIRE_VERSION = 3;
  private static final List<String> ASSETS =
      List.of(
          "ancientrpg/content.json",
          "ancientrpg/sword-motion.json",
          "assets/ancientrpg/models/item/training_sword.json",
          "assets/ancientrpg/items/training_sword.json",
          "assets/ancientrpg/textures/item/training_sword.png",
          "assets/ancientrpg/blockstates/demo_block.json",
          "assets/ancientrpg/models/block/demo_block_off.json",
          "assets/ancientrpg/models/block/demo_block_on.json",
          "assets/ancientrpg/models/item/demo_item.json",
          "assets/ancientrpg/items/demo_item.json",
          "assets/ancientrpg/lang/en_us.json");

  private PublicContent() {}

  public static Compatibility identity(
      int minecraftProtocol,
      int blockId,
      int activeState,
      int inactiveState,
      int itemId,
      int swordId) {
    var manifest = new LinkedHashMap<String, byte[]>();
    for (String path : ASSETS) manifest.put(path, resource(path));
    String ids =
        BLOCK
            + "="
            + blockId
            + "\nactive=true="
            + activeState
            + "\nactive=false="
            + inactiveState
            + "\n"
            + ITEM
            + "="
            + itemId
            + "\n"
            + SWORD
            + "="
            + swordId
            + "\n";
    manifest.put("animation/runtime.txt", SwordAnimation.manifest());
    manifest.put("registry/runtime.txt", ids.getBytes(StandardCharsets.UTF_8));
    return new Compatibility(
        minecraftProtocol,
        WIRE_VERSION,
        new String(resource("ancientrpg/release.txt"), StandardCharsets.UTF_8).strip(),
        ContentManifest.fingerprint(manifest));
  }

  private static byte[] resource(String path) {
    try (var stream = PublicContent.class.getResourceAsStream("/" + path)) {
      if (stream == null) throw new IllegalStateException("Missing public content: " + path);
      return stream.readAllBytes();
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read public content: " + path, failure);
    }
  }
}
