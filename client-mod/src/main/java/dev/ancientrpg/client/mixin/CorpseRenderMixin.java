package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.CorpseClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class CorpseRenderMixin {
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$visible(
      Entity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      CallbackInfoReturnable<Boolean> ci) {
    if (CorpseClient.hideOriginal(entity)) ci.setReturnValue(false);
    else if (CorpseClient.isBody(entity))
      ci.setReturnValue(
          entity.distanceToSqr(x, y, z) < 128 * 128
              && frustum.isVisible(entity.getBoundingBox().inflate(2)));
  }

  @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
  private void ancientrpg$body(
      Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> ci) {
    var state = CorpseClient.render((EntityRenderDispatcher) (Object) this, entity, partial);
    if (state != null) ci.setReturnValue(state);
  }
}
