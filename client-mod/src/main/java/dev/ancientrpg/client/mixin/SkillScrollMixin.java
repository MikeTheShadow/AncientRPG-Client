package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class SkillScrollMixin {
  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
    if (SkillClient.INSTANCE.committed()
        && net.minecraft.client.Minecraft.getInstance().gui.screen() == null) ci.cancel();
  }
}
