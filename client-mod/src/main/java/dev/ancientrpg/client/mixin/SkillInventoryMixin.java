package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.SkillClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class SkillInventoryMixin {
  @Inject(
      method = {
        "handleContainerInput",
        "handleInventoryButtonClick",
        "handleCreativeModeItemAdd",
        "handleCreativeModeItemDrop"
      },
      at = @At("HEAD"),
      cancellable = true)
  private void ancientrpg$inventory(CallbackInfo ci) {
    if (SkillClient.INSTANCE.committed()
        || dev.ancientrpg.client.InventoryClient.INSTANCE.connected()) ci.cancel();
  }
}
