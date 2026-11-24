package com.comag.shoresmp.client.mixin;

import com.comag.shoresmp.client.TierData;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    // Runs right after the game works out a player's name in the tab list, and puts their tier tag in front
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void shoresmp$addTierTag(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        MutableComponent tag = TierData.tagFor(info.getProfile().name());
        if (tag != null) {
            cir.setReturnValue(tag.append(cir.getReturnValue()));
        }
    }
}