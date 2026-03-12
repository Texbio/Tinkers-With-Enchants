package t.tinkerswithenchants.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import t.tinkerswithenchants.util.UnbreakingUtil;

import javax.annotation.Nullable;

/**
 * Intercepts ALL tool damage in TConstruct to apply vanilla Unbreaking.
 *
 * <p>TConstruct routes every damage source through {@code ToolDamageUtil.damage()},
 * which eventually calls {@code directDamage()}. By injecting at HEAD of
 * {@code directDamage} we catch mining, combat, ranged, armor, and any other
 * damage path in one place.</p>
 */
@Mixin(ToolDamageUtil.class)
public class ToolDamageUtilMixin {

    /** Guard flag to prevent recursive injection when we re-call directDamage */
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Injects at the start of {@code directDamage} to apply Unbreaking probability.
     * If the damage is fully negated, cancels with false (tool not broken).
     * If reduced, cancels and re-calls directDamage with the reduced amount.
     */
    @Inject(method = "directDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void twe$applyUnbreaking(IToolStackView tool, int amount,
                                            @Nullable LivingEntity entity,
                                            @Nullable ItemStack stack,
                                            CallbackInfoReturnable<Boolean> cir) {
        // Skip if we're in our own re-call to avoid infinite recursion
        if (APPLYING.get()) return;

        if (stack != null && !stack.isEmpty()) {
            RandomSource random = entity != null
                    ? entity.getRandom()
                    : RandomSource.create();
            int reduced = UnbreakingUtil.applyUnbreaking(stack, amount, random);

            if (reduced != amount) {
                if (reduced <= 0) {
                    // Fully negated — no damage taken
                    cir.setReturnValue(false);
                } else {
                    // Partially reduced — re-call directDamage with reduced amount
                    APPLYING.set(true);
                    try {
                        cir.setReturnValue(ToolDamageUtil.directDamage(tool, reduced, entity, stack));
                    } finally {
                        APPLYING.set(false);
                    }
                }
            }
            // If reduced == amount, let the original method run unmodified
        }
    }
}
