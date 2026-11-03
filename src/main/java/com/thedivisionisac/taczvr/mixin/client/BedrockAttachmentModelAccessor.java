package com.thedivisionisac.taczvr.mixin.client;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Version-locked TACZ 1.1.8 accessor used only while wrapping an attachment-model render call. The caller must
 * restore the author's radius modifier in a {@code finally} block; this interface owns no persistent render state.
 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public interface BedrockAttachmentModelAccessor {
    @Accessor("isScope")
    boolean taczvr$isScope();

    @Accessor("isSight")
    boolean taczvr$isSight();

    @Accessor("scopeViewRadiusModifier")
    float taczvr$getScopeViewRadiusModifier();

    @Accessor("scopeViewRadiusModifier")
    void taczvr$setScopeViewRadiusModifier(float modifier);

    /**
     * TACZ uses these exact paths to draw the ocular stencil. Physical alignment must use the same model nodes rather
     * than {@code scope_view}, whose authored camera offset may be far in front of the real rear lens.
     */
    @Accessor("ocularNodePaths")
    List<List<BedrockPart>> taczvr$getOcularNodePaths();

    /**
     * Returns the authored rear ring path used as the preferred projection plane.
     */
    @Accessor("ocularRingPath")
    @Nullable
    List<BedrockPart> taczvr$getOcularRingPath();

    /**
     * Returns the ordered division paths paired with TACZ's ocular paths.
     */
    @Accessor("divisionNodePaths")
    List<List<BedrockPart>> taczvr$getDivisionNodePaths();

    @Invoker("renderOcularStencil")
    void taczvr$invokeRenderOcularStencil(
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            boolean scopeOnly
    );

    @Invoker("renderOcularAndDivision")
    void taczvr$invokeRenderOcularAndDivision(
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            boolean scopeOnly
    );
}
