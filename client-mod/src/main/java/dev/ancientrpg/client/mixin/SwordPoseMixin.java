package dev.ancientrpg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ancientrpg.client.DemoRegistration;
import dev.ancientrpg.client.SkillClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class SwordPoseMixin {
  @Inject(method = "submitArmWithItem", at = @At("HEAD"))
  private void ancientrpg$begin(
      AbstractClientPlayer player,
      float partial,
      float pitch,
      InteractionHand hand,
      float swing,
      ItemStack item,
      float equip,
      PoseStack pose,
      SubmitNodeCollector collector,
      int light,
      CallbackInfo ci) {
    pose.pushPose();
    if (hand == InteractionHand.MAIN_HAND && item.is(DemoRegistration.sword))
      SkillClient.INSTANCE.transform(pose);
  }

  @Inject(method = "submitArmWithItem", at = @At("RETURN"))
  private void ancientrpg$end(
      AbstractClientPlayer player,
      float partial,
      float pitch,
      InteractionHand hand,
      float swing,
      ItemStack item,
      float equip,
      PoseStack pose,
      SubmitNodeCollector collector,
      int light,
      CallbackInfo ci) {
    pose.popPose();
  }
}
