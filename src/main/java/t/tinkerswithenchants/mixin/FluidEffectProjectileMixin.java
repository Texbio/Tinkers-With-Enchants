package t.tinkerswithenchants.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectManager;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;
import slimeknights.tconstruct.tools.entity.FluidEffectProjectile;
import t.tinkerswithenchants.handler.SwasherEnchantHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adds vanilla-{@code AbstractArrow}-style piercing semantics to TC's
 * {@link FluidEffectProjectile}. The vanilla swasher's spit projectile
 * detects at most one entity per tick and then teleports its position
 * forward by its full velocity, which means closely-spaced mobs in the
 * same per-tick segment all get skipped except for the first.
 *
 * <p>This mixin injects right before the projectile's per-tick position
 * update. It traces the same segment the natural tick already traced,
 * finds <em>all</em> remaining unpierced entities in path order, and
 * applies the projectile's fluid effect to each — until the projectile's
 * fluid budget runs out (which discards it) or all entities are
 * processed. The result mirrors how a vanilla piercing arrow tears
 * through a row of mobs in one tick.</p>
 *
 * <h3>Throttle</h3>
 * Multi-hit work scales with active spit count and entities in segment,
 * so under farm-scale loads it could compound into TPS loss. When the
 * server's active {@link FluidEffectProjectile} count exceeds
 * {@link SwasherEnchantHandler#MULTIHIT_THRESHOLD}, this injection
 * becomes a no-op and the projectile reverts to vanilla single-hit-
 * per-tick behavior. Normal play is unaffected; only mob-farm-scale
 * fluid spam degrades, and only to the original behavior, not worse.
 *
 * <h3>Pierced-list bookkeeping</h3>
 * Hit entities are added to the projectile's {@code twe_pierced_ids}
 * persistent NBT list (the same list used by
 * {@link SwasherEnchantHandler#onProjectileImpact}). Subsequent ticks
 * with the projectile still inside an already-hit entity's bbox skip
 * the duplicate impact via that same listener.
 */
@Mixin(FluidEffectProjectile.class)
public abstract class FluidEffectProjectileMixin {

    @Shadow(remap = false) public abstract FluidStack getFluid();
    @Shadow(remap = false) private float power;

    /**
     * Inject right before the per-tick position update {@code if (!isRemoved())}
     * branch. By this point the natural tick has already processed at most one
     * entity hit; we use the remaining segment to apply hits to all other
     * unpierced entities in path order.
     */
    @SuppressWarnings("removal") // FluidEffectContext.Entity(...) deprecated in 3.11+ in favor
                                  // of builder(), but keeping the constructor for 3.9 compat.
    @Inject(
        method = "tick()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isRemoved()Z",
            ordinal = 0
        )
    )
    private void twe$multiHitPiercingPass(CallbackInfo ci) {
        FluidEffectProjectile self = (FluidEffectProjectile) (Object) this;
        Level level = self.level();

        // Server-only — clients run their own physics for prediction but
        // don't apply gameplay effects.
        if (level.isClientSide) return;
        if (self.isRemoved()) return;

        FluidStack fluid = self.getFluid();
        if (fluid.isEmpty()) return;

        // Throttle: under heavy spit load, fall back to vanilla single-hit-
        // per-tick to avoid compounding O(spits × entities-in-segment) work.
        if (SwasherEnchantHandler.ACTIVE_SPIT_COUNT.get() > SwasherEnchantHandler.MULTIHIT_THRESHOLD) {
            return;
        }

        // Trace the same segment the natural tick used: from current pos to
        // current pos + velocity. (Position hasn't been updated yet at this
        // injection point.)
        Vec3 startPos = self.position();
        Vec3 velocity = self.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0e-6) return;
        Vec3 endPos = startPos.add(velocity);

        // Build the set of already-pierced entity IDs once.
        CompoundTag data = self.getPersistentData();
        ListTag pierced = data.getList(SwasherEnchantHandler.PIERCED_TAG, Tag.TAG_INT);
        Set<Integer> piercedIds = new HashSet<>(pierced.size() * 2);
        for (int i = 0; i < pierced.size(); i++) {
            piercedIds.add(pierced.getInt(i));
        }

        // Find candidate entities in the segment's AABB. We replicate the
        // basic predicate from Projectile.canHitEntity (alive, pickable,
        // not the owner) without shadowing the protected method, then
        // confirm actual segment intersection.
        Entity ownerEntity = self.getOwner();
        AABB box = self.getBoundingBox().expandTowards(velocity).inflate(1.0);
        List<EntityHitResult> hits = new ArrayList<>();
        for (Entity e : level.getEntities(self, box)) {
            if (e == ownerEntity) continue;
            if (!e.isAlive() || !e.isPickable() || e.isSpectator()) continue;
            if (piercedIds.contains(e.getId())) continue;

            // Confirm the entity's bbox actually intersects the segment.
            // The 0.3 inflation matches the same tolerance vanilla projectile
            // hit-detection uses for "near miss" generosity.
            AABB entityBox = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> intersection = entityBox.clip(startPos, endPos);
            if (intersection.isEmpty()) continue;
            hits.add(new EntityHitResult(e, intersection.get()));
        }

        if (hits.isEmpty()) return;

        // Process in path order so each hit's fluid consumption happens in
        // the correct sequence (matters when fluid runs out mid-segment).
        hits.sort(Comparator.comparingDouble(h -> h.getLocation().distanceToSqr(startPos)));

        FluidEffects recipe = FluidEffectManager.INSTANCE.find(fluid.getFluid());
        if (!recipe.hasEntityEffects()) return;

        // Apply each hit — replicates the entity-effect arm of onHitEntity
        // without going through Projectile.onHit's dispatch (we want only
        // the entity-effect logic, not knockback or block dispatch).
        for (EntityHitResult hit : hits) {
            if (self.isRemoved()) break;
            FluidStack current = self.getFluid();
            if (current.isEmpty()) break;

            // Add to the live ListTag in advance so the impact-event
            // listener's subsequent-tick re-hit guard sees it.
            pierced.add(IntTag.valueOf(hit.getEntity().getId()));

            LivingEntity owner = ownerEntity instanceof LivingEntity le ? le : null;
            FluidEffectContext.Entity context = new FluidEffectContext.Entity(
                level, owner, self, hit.getEntity(), hit.getLocation()
            );
            int consumed = recipe.applyToEntity(current, this.power, context, IFluidHandler.FluidAction.EXECUTE);

            // Mutate fluid amount in place — same FluidStack reference is
            // stored in entityData, and Mojang's SynchedEntityData.set()
            // would short-circuit on equals() mismatch.
            int remaining = current.getAmount() - consumed;
            if (remaining <= 0) {
                current.setAmount(0);
                self.discard();
                break;
            } else {
                current.setAmount(remaining);
            }
        }

        // Persist the updated pierced list. Even though `pierced` is the
        // same reference returned by getList (and we mutated it directly),
        // an explicit put() ensures the tag is registered if it wasn't
        // already present in the compound.
        data.put(SwasherEnchantHandler.PIERCED_TAG, pierced);
    }
}
