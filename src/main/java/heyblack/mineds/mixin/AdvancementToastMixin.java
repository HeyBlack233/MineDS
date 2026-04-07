package heyblack.mineds.mixin;

import heyblack.mineds.listener.AdvancementListener;
import net.minecraft.advancement.Advancement;
import net.minecraft.client.toast.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept advancement grant events.
 * Injects into AdvancementToast constructor to detect when player earns advancements.
 */
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onAdvancementGranted(Advancement advancement, CallbackInfo ci) {
        AdvancementListener.onAdvancementGranted(advancement);
    }
}
