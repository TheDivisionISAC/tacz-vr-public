package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Registry- and NBT-only identification for optional LRTactical item renderers.
 */
public final class LrTacticalVrItemSupport {
    private static final ResourceLocation THROWABLE_ITEM = new ResourceLocation("lrtactical", "throwable");
    private static final ResourceLocation CONSUMABLE_ITEM = new ResourceLocation("lrtactical", "consumable");
    private static final ResourceLocation MELEE_ITEM = new ResourceLocation("lrtactical", "melee");

    private LrTacticalVrItemSupport() {
    }

    public static boolean isThrowable(ItemStack stack) {
        return kind(stack) == RenderKind.THROWABLE;
    }

    public static boolean isConsumable(ItemStack stack) {
        return kind(stack) == RenderKind.CONSUMABLE;
    }

    public static boolean isMelee(ItemStack stack) {
        return kind(stack) == RenderKind.MELEE;
    }

    public static boolean isRenderSupported(ItemStack stack) {
        return switch (kind(stack)) {
            case THROWABLE -> TaczVrClientConfig.ENABLE_LRTACTICAL_THROWABLE_VR_COMPAT.get();
            case CONSUMABLE -> TaczVrClientConfig.ENABLE_LRTACTICAL_CONSUMABLE_VR_RENDERING.get();
            case MELEE -> TaczVrClientConfig.ENABLE_LRTACTICAL_MELEE_VR_RENDERING.get();
            case NONE -> false;
        };
    }

    @Nullable
    public static ResourceLocation getLogicalItemId(ItemStack stack) {
        RenderKind kind = kind(stack);
        if (kind == RenderKind.NONE) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(kind.idTag, Tag.TAG_STRING)) {
            ResourceLocation logicalId = ResourceLocation.tryParse(tag.getString(kind.idTag));
            if (logicalId != null) {
                return logicalId;
            }
        }
        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    static RenderKind classify(@Nullable ResourceLocation registryId) {
        if (THROWABLE_ITEM.equals(registryId)) {
            return RenderKind.THROWABLE;
        }
        if (CONSUMABLE_ITEM.equals(registryId)) {
            return RenderKind.CONSUMABLE;
        }
        if (MELEE_ITEM.equals(registryId)) {
            return RenderKind.MELEE;
        }
        return RenderKind.NONE;
    }

    private static RenderKind kind(ItemStack stack) {
        return stack.isEmpty() ? RenderKind.NONE : classify(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    enum RenderKind {
        NONE(""),
        THROWABLE("ThrowableId"),
        CONSUMABLE("ConsumableId"),
        MELEE("MeleeWeaponId");

        private final String idTag;

        RenderKind(String idTag) {
            this.idTag = idTag;
        }
    }
}
