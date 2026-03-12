package t.tinkerswithenchants.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import slimeknights.tconstruct.library.client.armor.AbstractArmorModel;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModel;
import slimeknights.tconstruct.library.client.armor.MultilayerArmorModel;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;

/**
 * Fixes enchantment glint on TConstruct multi-layer armor.
 *
 * <p>TConstruct renders armor in multiple texture layers and only applies
 * the glint to the FIRST layer, then sets {@code armorGlint = false}.
 * This results in partial glint coverage.
 *
 * <p>This mixin simply removes the {@code armorGlint = false} line so
 * all layers render with glint through the normal vanilla
 * {@code ItemRenderer.getArmorFoilBuffer()} path.
 */
@Mixin(MultilayerArmorModel.class)
public abstract class ArmorGlintMixin extends AbstractArmorModel {

    @Shadow(remap = false) protected ItemStack armorStack;
    @Shadow(remap = false) protected ArmorModel model;
    @Shadow(remap = false) protected RegistryAccess registryAccess;

    /**
     * @author TinkersWithEnchants
     * @reason Apply glint to ALL armor texture layers, not just the first.
     *         Original had {@code armorGlint = false} after first layer.
     */
    @Overwrite(remap = false)
    public void renderToBuffer(PoseStack matrices, VertexConsumer bufferIn,
                                int packedLightIn, int packedOverlayIn,
                                float red, float green, float blue, float alpha) {
        if (this.base != null && buffer != null) {
            for (ArmorTextureSupplier textureSupplier : model.layers()) {
                ArmorTexture texture = textureSupplier.getArmorTexture(
                        armorStack, textureType, registryAccess);
                if (texture != ArmorTexture.EMPTY) {
                    // hasGlint passed directly — never set to false
                    texture.renderTexture(base, matrices, buffer,
                            packedLightIn, packedOverlayIn,
                            red, green, blue, alpha, hasGlint);
                }
                if (hasWings) {
                    texture = textureSupplier.getArmorTexture(
                            armorStack, TextureType.WINGS, registryAccess);
                    if (texture != ArmorTexture.EMPTY) {
                        renderWings(matrices, packedLightIn, packedOverlayIn,
                                texture, red, green, blue, alpha, hasGlint);
                    }
                }
            }
        }
    }
}
