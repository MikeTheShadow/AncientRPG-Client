package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillPresentation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class SkillFacingMixin {
  @Inject(
      method =
          "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
      at = @At("TAIL"))
  private void ancientrpg$facing(
      Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
    SkillPresentation.facing(state);
  }
}
