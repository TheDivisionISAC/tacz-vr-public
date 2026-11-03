package com.thedivisionisac.taczvr.client.compat.lrtactical;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LrTacticalVrItemSupportTest {
    @Test
    void classifiesOnlyTheThreeAuditedLrTacticalBaseItems() {
        assertEquals(
                LrTacticalVrItemSupport.RenderKind.THROWABLE,
                LrTacticalVrItemSupport.classify(new ResourceLocation("lrtactical", "throwable"))
        );
        assertEquals(
                LrTacticalVrItemSupport.RenderKind.CONSUMABLE,
                LrTacticalVrItemSupport.classify(new ResourceLocation("lrtactical", "consumable"))
        );
        assertEquals(
                LrTacticalVrItemSupport.RenderKind.MELEE,
                LrTacticalVrItemSupport.classify(new ResourceLocation("lrtactical", "melee"))
        );
        assertEquals(
                LrTacticalVrItemSupport.RenderKind.NONE,
                LrTacticalVrItemSupport.classify(new ResourceLocation("lrtactical", "flash_shield"))
        );
        assertEquals(LrTacticalVrItemSupport.RenderKind.NONE, LrTacticalVrItemSupport.classify(null));
    }
}
