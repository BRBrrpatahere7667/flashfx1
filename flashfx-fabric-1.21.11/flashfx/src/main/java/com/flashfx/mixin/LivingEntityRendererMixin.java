package com.flashfx.mixin;

import com.flashfx.FlashFxClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glass players: draw the player model on a translucent render layer and tint it with
 * an alpha colour. Both injectors are optional (require = 0), so if a method name differs
 * in your Yarn build the game still launches and only the glass look is skipped.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashfx$glassLayer(LivingEntityRenderState state, boolean showBody, boolean translucent,
                                    boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        if (showBody && FlashFxClient.glassOn() && state instanceof PlayerEntityRenderState) {
            cir.setReturnValue(RenderLayer.getEntityTranslucent(
                    ((LivingEntityRenderer) (Object) this).getTexture(state)));
        }
    }

    @Inject(method = "getMixColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void flashfx$glassTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (FlashFxClient.glassOn() && state instanceof PlayerEntityRenderState) {
            cir.setReturnValue(FlashFxClient.glassColor());
        }
    }
}
