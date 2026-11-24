package com.comag.shoresmp.client.mixin;

import com.comag.shoresmp.client.TierData;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class NameTagMixin {

    // After the game prepares an entity's name tag for drawing, put the player's tier tag in front of it.
    // This also covers your own name in F5 / third person when another mod makes it visible.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void shoresmp$addTierToNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        Component current = state.nameTag;
        if (current == null) return;
        MutableComponent tag = TierData.tagFor(player.getGameProfile().name());
        if (tag != null) {
            state.nameTag = tag.append(current);
        }
    }
}