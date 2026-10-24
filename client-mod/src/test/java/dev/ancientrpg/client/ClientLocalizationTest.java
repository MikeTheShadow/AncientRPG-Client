package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.InventoryWire;
import dev.ancientrpg.protocol.Skill;
import dev.ancientrpg.protocol.SkillWire;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ClientLocalizationTest {
  private static final List<String> PRESENTATION_CLASSES =
      List.of(
          "ClientText",
          "InventoryClient",
          "MainInventoryScreen",
          "MainInventoryScreen$Control",
          "MainInventoryScreen$Tile",
          "SkillClient",
          "SkillTreeScreen",
          "SkillTreeScreen$Card",
          "AncientRpgClient");
  private static final Set<String> PREFIXES =
      Set.of(
          "inventory.",
          "common.",
          "skills.",
          "skill.",
          "hud.",
          "format.",
          "family.",
          "connection.",
          "interaction.");

  @Test
  void allStaticGuiKeysExistAndPresentationDoesNotFallBackToHardcodedEnglish() throws Exception {
    var language = language();
    for (String name : PRESENTATION_CLASSES) {
      var node = new ClassNode();
      try (var stream =
          getClass().getResourceAsStream("/dev/ancientrpg/client/" + name + ".class")) {
        assertNotNull(stream, name);
        new ClassReader(stream).accept(node, 0);
      }
      for (var method : node.methods) {
        for (var instruction : method.instructions) {
          if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String value) {
            if (PREFIXES.stream().anyMatch(value::startsWith) && !value.endsWith("."))
              require(language, "ancientrpg." + value);
            assertFalse(
                value.matches(".*[A-Za-z]+\\s+[A-Za-z]+.*"),
                () -> "Hardcoded GUI text in " + name + ": " + value);
          }
          if (instruction instanceof MethodInsnNode call) {
            assertFalse(
                call.owner.equals("net/minecraft/network/chat/Component")
                    && call.name.equals("literal"),
                name + " must retain translatable components");
            assertFalse(
                call.owner.startsWith("dev/ancientrpg/protocol/")
                    && Set.of("title", "description", "family", "message").contains(call.name)
                    && !name.equals("ClientText"),
                name + " must use localized catalogue presentation");
          }
        }
      }
    }
  }

  @Test
  void allDynamicCatalogueSlotCategoryAndResultKeysExist() throws Exception {
    var language = language();
    for (var item : InventoryCatalog.ITEMS) {
      require(language, ClientText.itemKey(item));
      require(language, ClientText.itemKey(item) + ".description");
      require(
          language,
          "ancientrpg.family." + item.family().toLowerCase(Locale.ROOT).replace(' ', '_'));
    }
    for (var slot : InventoryCatalog.Slot.values())
      require(language, "ancientrpg.inventory.slot." + slot.name().toLowerCase(Locale.ROOT));
    for (var result : InventoryWire.Result.values())
      require(language, "ancientrpg.inventory.result." + result.name().toLowerCase(Locale.ROOT));
    for (var skill : Skill.values()) {
      String key = "ancientrpg.skill." + skill.name().toLowerCase(Locale.ROOT);
      require(language, key);
      require(language, key + ".description");
      require(language, key + ".short");
      require(language, "key.ancientrpg." + skill.name().toLowerCase(Locale.ROOT));
    }
    for (var reason : SkillWire.Reason.values())
      require(language, "ancientrpg.skills.reason." + reason.name().toLowerCase(Locale.ROOT));
    for (String category : List.of("all", "weapons", "supplies"))
      require(language, "ancientrpg.inventory.category." + category);
    assertTrue(
        language.entrySet().stream()
            .noneMatch(
                e ->
                    e.getValue().getAsString().toLowerCase(Locale.ROOT).contains("kept on death")));
  }

  private static JsonObject language() throws Exception {
    try (var stream =
        ClientLocalizationTest.class.getResourceAsStream("/assets/ancientrpg/lang/en_us.json")) {
      assertNotNull(stream);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static void require(JsonObject language, String key) {
    assertTrue(language.has(key), () -> "Missing English translation: " + key);
    assertTrue(language.get(key).isJsonPrimitive(), key);
  }
}
