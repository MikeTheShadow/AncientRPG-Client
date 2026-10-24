package dev.ancientrpg.client.mixin;

import dev.ancientrpg.client.InventoryClient;
import dev.ancientrpg.client.MainInventoryScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class MainInventoryMixin {
  @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
  private Screen ancientrpg$inventory(Screen screen) {
    return screen instanceof InventoryScreen && InventoryClient.INSTANCE.connected()
        ? new MainInventoryScreen()
        : screen;
  }
}
