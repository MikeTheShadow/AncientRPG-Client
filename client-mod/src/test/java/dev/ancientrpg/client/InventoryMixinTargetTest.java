package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

class InventoryMixinTargetTest {
  @Test
  void inventoryScreenHookTargetsAnExistingMethodWithTheScreenArgument() throws Exception {
    assertTarget(
        "MainInventoryMixin",
        "ancientrpg$inventory",
        "(Lnet/minecraft/client/gui/screens/Screen;)V");
  }

  @Test
  void perspectiveHookTargetsAnExistingCameraSetter() throws Exception {
    assertTarget(
        "ThirdPersonCameraMixin", "ancientrpg$perspective", "(Lnet/minecraft/client/CameraType;)V");
  }

  private static void assertTarget(String mixinName, String handlerName, String descriptor)
      throws Exception {
    var mixin = readClass("dev/ancientrpg/client/mixin/" + mixinName);
    var targetAnnotation =
        mixin.invisibleAnnotations.stream()
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
            .findFirst()
            .orElseThrow();
    var targets = (List<?>) value(targetAnnotation, "value");
    assertEquals(1, targets.size());
    var target = readClass(((Type) targets.getFirst()).getInternalName());
    var handler =
        mixin.methods.stream().filter(m -> m.name.equals(handlerName)).findFirst().orElseThrow();
    var injection =
        handler.visibleAnnotations.stream()
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;"))
            .findFirst()
            .orElseThrow();
    var selectors = (List<?>) value(injection, "method");
    for (var selector : selectors) {
      assertTrue(
          target.methods.stream()
              .anyMatch(
                  m ->
                      (m.name.equals(selector) || (m.name + m.desc).equals(selector))
                          && m.desc.equals(descriptor)),
          () -> "Mixin target does not exist: " + target.name + "." + selector);
    }
  }

  private static ClassNode readClass(String name) throws Exception {
    try (var stream = InventoryMixinTargetTest.class.getResourceAsStream("/" + name + ".class")) {
      assertNotNull(stream, name);
      var node = new ClassNode();
      new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
      return node;
    }
  }

  private static Object value(AnnotationNode annotation, String name) {
    for (int i = 0; i < annotation.values.size(); i += 2) {
      if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
    }
    throw new AssertionError("Missing annotation property: " + name);
  }
}
