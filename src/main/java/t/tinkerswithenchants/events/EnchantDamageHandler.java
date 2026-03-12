package t.tinkerswithenchants.events;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import t.tinkerswithenchants.TinkersWithEnchants;

/**
 * Restores all vanilla enchantment effects that TConstruct's
 * {@code ToolAttackUtil.performAttack()} intentionally skips.
 *
 * <h3>What TConstruct skips:</h3>
 * <ul>
 *   <li>{@code EnchantmentHelper.getDamageBonus()} — Sharpness, Smite, BoA</li>
 *   <li>Fire Aspect</li>
 *   <li>Knockback enchantment</li>
 *   <li>{@code EnchantmentHelper.doPostDamageEffects()} — BoA slowness, modded post-attack</li>
 *   <li>Sweeping Edge — sweep damage boost (handled via sweep hit detection)</li>
 * </ul>
 *
 * <h3>Sweeping Edge implementation</h3>
 * <p>TConstruct's {@code SweepWeaponAttack.afterMeleeHit()} deals sweep damage
 * to nearby entities via {@code aoeTarget.hurt()}, which fires {@code LivingHurtEvent}
 * for each target. We detect these sweep hits by tracking the main target in a
 * {@link ThreadLocal} and boosting sweep damage for subsequent hits from the same
 * player in the same tick.</p>
 */
@Mod.EventBusSubscriber(modid = TinkersWithEnchants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnchantDamageHandler {

    private EnchantDamageHandler() {}

    /** Context for tracking sweep hits after a main melee attack. */
    private record SweepCtx(Player player, Entity mainTarget, int sweepLevel, float baseDamage, int tick) {}

    private static final ThreadLocal<SweepCtx> sweepContext = new ThreadLocal<>();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        // Only player direct melee attacks (not projectiles, not thorns, etc.)
        if (!(source.getEntity() instanceof Player player)) return;
        if (source.getDirectEntity() != player) return;

        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof IModifiable)) return;

        LivingEntity target = event.getEntity();

        // ── Check if this is a sweep hit ─────────────────────────────────
        SweepCtx ctx = sweepContext.get();
        if (ctx != null && ctx.player == player && ctx.tick == player.tickCount
                && target != ctx.mainTarget) {
            // This is a sweep hit — boost damage with vanilla Sweeping Edge formula
            float vanillaSweep = Math.min(ctx.baseDamage,
                    (float) ctx.sweepLevel / (ctx.sweepLevel + 1) * ctx.baseDamage + 1);
            event.setAmount(Math.max(event.getAmount(), vanillaSweep));
            return; // Don't apply main hit effects to sweep targets
        }

        // ── Main hit processing ──────────────────────────────────────────

        // Clear any stale sweep context
        sweepContext.remove();

        // 1. Damage bonus (Sharpness, Smite, Bane of Arthropods)
        float bonus = EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
        if (bonus > 0) {
            event.setAmount(event.getAmount() + bonus);
        }

        // 2. Fire Aspect
        int fireAspect = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.FIRE_ASPECT, weapon);
        if (fireAspect > 0) {
            target.setSecondsOnFire(fireAspect * 4);
        }

        // 3. Knockback enchantment
        int knockback = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.KNOCKBACK, weapon);
        if (knockback > 0) {
            float angle = player.getYRot() * ((float) Math.PI / 180F);
            target.knockback(knockback * 0.5F,
                    Mth.sin(angle), -Mth.cos(angle));
        }

        // 4. Post-attack enchantment effects
        EnchantmentHelper.doPostDamageEffects(player, target);

        // 5. Set sweep context for upcoming sweep hits (same tick)
        int sweepLevel = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.SWEEPING_EDGE, weapon);
        if (sweepLevel > 0) {
            sweepContext.set(new SweepCtx(player, target, sweepLevel,
                    event.getAmount(), player.tickCount));
        }
    }
}
