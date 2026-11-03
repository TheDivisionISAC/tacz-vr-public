package com.thedivisionisac.taczvr.client.control;

import com.thedivisionisac.taczvr.config.VrStabilizationLevel;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Radial-menu slider for the bounded user-facing stabilization level.
 */
public final class VrStabilizationSlider extends AbstractSliderButton {
    public VrStabilizationSlider(int x, int y, int width) {
        super(
                x,
                y,
                width,
                20,
                Component.empty(),
                VrStabilizationLevel.toSliderValue(VrGunRadialActions.currentStabilizationLevel())
        );
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int level = VrStabilizationLevel.fromSliderValue(this.value);
        this.setMessage(Component.translatable("menu.taczvr.stabilization", level));
    }

    @Override
    protected void applyValue() {
        VrGunRadialActions.setStabilizationLevel(VrStabilizationLevel.fromSliderValue(this.value));
    }
}
