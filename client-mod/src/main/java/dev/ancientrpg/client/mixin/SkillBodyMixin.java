package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillPresentation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class SkillBodyMixin {
  @Inject(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
      at = @At("TAIL"))
  private void ancientrpg$pose(HumanoidRenderState state, CallbackInfo ci) {
    if (state instanceof AvatarRenderState avatar)
      SkillPresentation.body((HumanoidModel<?>) (Object) this, avatar);
  }
}
