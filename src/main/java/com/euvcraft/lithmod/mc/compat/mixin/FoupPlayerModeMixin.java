package com.euvcraft.lithmod.mc.compat.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refresh unchanged stacks when recipient permissions change their wire representation. */
@Mixin(ServerPlayer.class)
public abstract class FoupPlayerModeMixin {
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void lithmod$refreshFoupData(GameType requested, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ()) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
    }
}
