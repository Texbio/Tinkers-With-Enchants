package t.tinkerswithenchants.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;

/**
 * Applies vanilla Multishot enchantment to TConstruct bows and crossbows.
 *
 * <p>When a TConstruct arrow spawns from a weapon with Multishot, we clone
 * it — the original is already fully configured by TConstruct with all
 * modifiers, damage, capabilities, etc. We create additional arrows from
 * the original's saved NBT (which includes TConstruct's modifier capability),
 * rotate their velocity by ±10 degrees (matching vanilla spread), and spawn
 * them. Since they're full entity clones, they inherit all TConstruct behavior
 * and grant proper kill credit via the preserved owner.</p>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MultishotHandler {

    /**
     * NBT tag we place on cloned arrows to prevent them from being
     * cloned again (infinite recursion guard).
     */
    private static final String CLONE_TAG = "twe_multishot_clone";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // Don't clone our own clones
        CompoundTag arrowTag = arrow.getPersistentData();
        if (arrowTag.getBoolean(CLONE_TAG)) return;

        // Find the shooter and their weapon
        Entity owner = arrow.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        ItemStack weapon = findMultishotWeapon(shooter);
        if (weapon.isEmpty()) return;

        int multishot = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.MULTISHOT, weapon);
        if (multishot <= 0) return;

        // Save the original arrow's full state (includes TConstruct capabilities)
        CompoundTag originalNbt = new CompoundTag();
        arrow.save(originalNbt);

        Level level = event.getLevel();

        // Spawn 2 extra arrows per multishot level, at ±10° per pair
        for (int i = 0; i < multishot; i++) {
            float angleOffset = 10.0F * (i + 1);
            spawnClone(level, arrow, originalNbt, owner, -angleOffset);
            spawnClone(level, arrow, originalNbt, owner, angleOffset);
        }
    }

    /**
     * Creates a clone of the original arrow from its saved NBT,
     * rotates its velocity by the given angle, and spawns it.
     */
    private static void spawnClone(Level level, AbstractArrow original,
                                   CompoundTag originalNbt, Entity owner, float angleDegrees) {
        // Create a new entity of the same type
        EntityType<?> type = original.getType();
        Entity rawClone = type.create(level);
        if (!(rawClone instanceof AbstractArrow clone)) return;

        // Load full state from original (copies damage, fire, pierce, capabilities, etc.)
        clone.load(originalNbt);

        // Generate new UUID so the clone is a distinct entity
        clone.setUUID(Mth.createInsecureUUID());

        // Mark as clone to prevent recursive cloning
        clone.getPersistentData().putBoolean(CLONE_TAG, true);

        // Set position to same as original
        clone.setPos(original.getX(), original.getY(), original.getZ());

        // Rotate the velocity vector around the Y axis
        Vec3 velocity = original.getDeltaMovement();
        double angleRad = Math.toRadians(angleDegrees);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double newX = velocity.x * cos - velocity.z * sin;
        double newZ = velocity.x * sin + velocity.z * cos;
        clone.setDeltaMovement(newX, velocity.y, newZ);

        // Preserve owner for kill credit, advancements, stats
        clone.setOwner(owner);

        // Extra arrows from multishot shouldn't be pickable (vanilla behavior)
        clone.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        level.addFreshEntity(clone);
    }

    /**
     * Finds a TConstruct ranged weapon with Multishot in the shooter's hands.
     */
    private static ItemStack findMultishotWeapon(LivingEntity shooter) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (mainHand.is(TinkerTags.Items.MODIFIABLE)
                && EnchantmentHelper.getTagEnchantmentLevel(Enchantments.MULTISHOT, mainHand) > 0) {
            return mainHand;
        }
        ItemStack offHand = shooter.getOffhandItem();
        if (offHand.is(TinkerTags.Items.MODIFIABLE)
                && EnchantmentHelper.getTagEnchantmentLevel(Enchantments.MULTISHOT, offHand) > 0) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}
