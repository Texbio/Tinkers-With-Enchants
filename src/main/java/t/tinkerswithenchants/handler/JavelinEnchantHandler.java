package t.tinkerswithenchants.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.tools.entity.ToolProjectile;
import t.tinkerswithenchants.TinkersWithEnchants;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * Implements trident enchantment effects for TConstruct thrown tools (javelin).
 *
 * <p>TConstruct's {@code ThrownTool} extends {@code ThrownTrident}, so it
 * inherits vanilla's loyalty return logic. This handler bridges vanilla
 * enchantment NBT to ThrownTool's entity data.</p>
 *
 * <h3>Loyalty</h3>
 * <p>On {@code EntityJoinLevelEvent}, reads Loyalty enchant level from the
 * tool's NBT and sets it on the entity's synched data. The parent
 * {@code ThrownTrident.tick()} handles the actual return-to-player logic,
 * with return speed scaling by level automatically.</p>
 *
 * <h3>Channeling</h3>
 * <p>On {@code LivingHurtEvent} from a thrown tool projectile, checks for
 * Channeling enchant. If present and the target is outdoors during a
 * thunderstorm, summons a lightning bolt at the target's position.</p>
 *
 * <h3>Impaling</h3>
 * <p>On {@code LivingHurtEvent} from a thrown tool projectile, applies
 * {@code getDamageBonus()} which includes Impaling (extra damage to aquatic
 * mobs) as well as Sharpness/Smite/BoA. Also applies Fire Aspect and
 * post-damage effects.</p>
 */
@Mod.EventBusSubscriber(modid = TinkersWithEnchants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JavelinEnchantHandler {

    private JavelinEnchantHandler() {}

    /** Cached accessor for ThrownTrident.ID_LOYALTY (made protected by TC's AT) */
    @Nullable
    private static EntityDataAccessor<Byte> ID_LOYALTY_ACCESSOR;
    private static boolean loyaltyLookupFailed = false;

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> getLoyaltyAccessor() {
        if (ID_LOYALTY_ACCESSOR != null) return ID_LOYALTY_ACCESSOR;
        if (loyaltyLookupFailed) return null;

        try {
            // TC's access transformer makes this protected: f_37558_ = ID_LOYALTY
            // Try mapped name first (dev), then SRG name (production)
            Field field = null;
            for (String name : new String[]{"ID_LOYALTY", "f_37558_"}) {
                try {
                    field = ThrownTrident.class.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (field == null) {
                loyaltyLookupFailed = true;
                TinkersWithEnchants.LOGGER.warn("TWE: Could not find ThrownTrident.ID_LOYALTY field");
                return null;
            }
            field.setAccessible(true);
            ID_LOYALTY_ACCESSOR = (EntityDataAccessor<Byte>) field.get(null);
            return ID_LOYALTY_ACCESSOR;
        } catch (Exception e) {
            loyaltyLookupFailed = true;
            TinkersWithEnchants.LOGGER.warn("TWE: Failed to access ThrownTrident.ID_LOYALTY", e);
            return null;
        }
    }

    // ── Loyalty: set on entity spawn ──────────────────────────────────────

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) return;

        // Only handle ThrownTool (TC's javelin projectile, extends ThrownTrident)
        if (!(entity instanceof ThrownTrident trident)) return;
        if (!(entity instanceof ToolProjectile toolProj)) return;

        ItemStack stack = toolProj.getDisplayTool();
        if (stack.isEmpty()) return;

        int loyaltyLevel = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.LOYALTY, stack);
        if (loyaltyLevel <= 0) return;

        EntityDataAccessor<Byte> accessor = getLoyaltyAccessor();
        if (accessor == null) return;

        // Take max of TC modifier level and enchant level, clamped to 10.
        // Higher levels cause the projectile to fly into unloaded chunks
        // on its return path. ID_LOYALTY is also a byte, so >127 overflows.
        byte current = trident.getEntityData().get(accessor);
        int clamped = Math.min(loyaltyLevel, 10);
        if (clamped > current) {
            trident.getEntityData().set(accessor, (byte) clamped);
        }
    }

    // ── Channeling on lightning rod: block hit ──────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        if (!(event.getEntity() instanceof ThrownTrident trident)) return;
        if (!(event.getEntity() instanceof ToolProjectile toolProj)) return;

        ItemStack stack = toolProj.getDisplayTool();
        if (stack.isEmpty()) return;

        int channeling = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.CHANNELING, stack);
        if (channeling <= 0) return;

        BlockHitResult blockHit = (BlockHitResult) event.getRayTraceResult();
        BlockPos pos = blockHit.getBlockPos();

        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.isThundering()) return;

        BlockState state = serverLevel.getBlockState(pos);
        if (!state.is(Blocks.LIGHTNING_ROD)) return;
        if (!serverLevel.canSeeSky(pos.above())) return;

        // Summon lightning at the tip of the lightning rod
        BlockPos strikePos = pos.above();
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.moveTo(strikePos.getX() + 0.5, strikePos.getY(), strikePos.getZ() + 0.5);
            Entity owner = trident.getOwner();
            lightning.setCause(owner instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
            serverLevel.addFreshEntity(lightning);
        }
    }

    // ── Channeling + Impaling: on projectile hit ─────────────────────────

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();

        // Check if damage is from a thrown tool projectile
        Entity directEntity = source.getDirectEntity();
        if (!(directEntity instanceof ThrownTrident)) return;
        if (!(directEntity instanceof ToolProjectile toolProj)) return;
        if (!(source.getEntity() instanceof Player player)) return;

        ItemStack stack = toolProj.getDisplayTool();
        if (stack.isEmpty()) return;

        LivingEntity target = event.getEntity();

        // ── Impaling + damage enchants ───────────────────────────────────
        // getDamageBonus reads all damage enchants from NBT: Sharpness,
        // Smite, BoA, and Impaling (extra damage to aquatic mobs)
        float bonus = EnchantmentHelper.getDamageBonus(stack, target.getMobType());
        if (bonus > 0) {
            event.setAmount(event.getAmount() + bonus);
        }

        // ── Fire Aspect ─────────────────────────────────────────────────
        int fireAspect = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.FIRE_ASPECT, stack);
        if (fireAspect > 0) {
            target.setSecondsOnFire(fireAspect * 4);
        }

        // ── Knockback ───────────────────────────────────────────────────
        int knockback = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.KNOCKBACK, stack);
        if (knockback > 0) {
            float angle = player.getYRot() * ((float) Math.PI / 180F);
            target.knockback(knockback * 0.5F,
                    Mth.sin(angle), -Mth.cos(angle));
        }

        // ── Post-damage effects (BoA slowness, modded) ──────────────────
        EnchantmentHelper.doPostDamageEffects(player, target);

        // ── Channeling: summon lightning on hit during thunderstorm ──────
        int channeling = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.CHANNELING, stack);
        if (channeling > 0 && target.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.isThundering() && serverLevel.canSeeSky(target.blockPosition())) {
                LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (lightning != null) {
                    lightning.moveTo(target.getX(), target.getY(), target.getZ());
                    lightning.setCause(player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
                    serverLevel.addFreshEntity(lightning);
                }
            }
        }
    }
}
