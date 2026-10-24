package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SkillControlsMixin {
  @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$controls(CallbackInfo ci) {
    dev.ancientrpg.client.CorpseClient.INSTANCE.suppressNativeSwap((Minecraft) (Object) this);
    if (!SkillClient.INSTANCE.committed()) return;
    var options = ((Minecraft) (Object) this).options;
    for (var key :
        new net.minecraft.client.KeyMapping[] {
          options.keyAttack, options.keyUse, options.keyDrop, options.keySwapOffhand
        }) while (key.consumeClick()) {}
    for (var key : options.keyHotbarSlots) while (key.consumeClick()) {}
    ci.cancel();
  }
}
