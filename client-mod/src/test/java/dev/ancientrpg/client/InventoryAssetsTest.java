package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ancientrpg.protocol.InventoryCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import org.junit.jupiter.api.Test;

class InventoryAssetsTest {
  @Test
  void customFontDefinitionUsesAnExistingProviderResource() throws Exception {
    var path = "/assets/ancientrpg/font/dossier.json";
    try (var stream = getClass().getResourceAsStream(path)) {
      assertNotNull(stream);
      var root =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      var provider = root.getAsJsonArray("providers").get(0);
      var parsed =
          TrueTypeGlyphProviderDefinition.CODEC
              .codec()
              .parse(JsonOps.INSTANCE, provider)
              .getOrThrow();
      String file = provider.getAsJsonObject().get("file").getAsString();
      var parts = file.split(":", 2);
      // Minecraft's TTF loader prefixes font/ itself.
      try (var font = getClass().getResourceAsStream("/assets/" + parts[0] + "/font/" + parts[1])) {
        assertNotNull(font);
        assertTrue(font.readAllBytes().length > 1000);
      }
      assertNotNull(parsed);
    }
  }

  @Test
  void everyInventoryExampleHasARegisteredItemModelAsset() throws Exception {
    for (var item : InventoryCatalog.ITEMS) {
      String name = item.key().substring("ancientrpg:".length());
      try (var itemAsset =
              getClass().getResourceAsStream("/assets/ancientrpg/items/" + name + ".json");
          var modelAsset =
              getClass().getResourceAsStream("/assets/ancientrpg/models/item/" + name + ".json")) {
        assertNotNull(itemAsset, item.key());
        assertNotNull(modelAsset, item.key());
        assertTrue(
            JsonParser.parseReader(new InputStreamReader(itemAsset, StandardCharsets.UTF_8))
                .isJsonObject());
        assertTrue(
            JsonParser.parseReader(new InputStreamReader(modelAsset, StandardCharsets.UTF_8))
                .isJsonObject());
      }
    }
  }
}
