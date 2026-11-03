package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.config.VrHandDisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional radial actions for LRTactical items. Reflection is deliberately limited to the public renderer
 * state-machine accessor so the addon keeps no compile-time dependency on LRTactical classes.
 */
public final class LrTacticalVrRadialActions {
    private static final Set<Class<?>> LOGGED_UNSUPPORTED_RENDERERS = new HashSet<>();

    private LrTacticalVrRadialActions() {
    }

    public static void inspect() {
        LocalPlayer player = getPlayer();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        Object renderer = IClientItemExtensions.of(stack).getCustomRenderer();
        if (triggerInspect(renderer, stack)) {
            return;
        }
        if (renderer != null && LOGGED_UNSUPPORTED_RENDERERS.add(renderer.getClass())) {
            TaczVr.LOGGER.warn(
                    "LRTactical VR inspect unavailable for renderer {}",
                    renderer.getClass().getName()
            );
        }
    }

    public static void drop() {
        LocalPlayer player = getPlayer();
        if (player != null) {
            player.drop(false);
        }
    }

    public static void cycleHandDisplayMode() {
        if (getPlayer() == null) {
            return;
        }
        VrHandDisplayMode next = TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get().next();
        TaczVrClientConfig.VR_HAND_DISPLAY_MODE.set(next);
        VrGunHandVisibility.resetTransientState();
    }

    static boolean triggerInspect(Object renderer, ItemStack stack) {
        if (renderer == null) {
            return false;
        }
        try {
            Method getStateMachine = renderer.getClass().getMethod("getStateMachine", ItemStack.class);
            Object stateMachine = getStateMachine.invoke(renderer, stack);
            if (stateMachine instanceof LuaAnimationStateMachine<?> luaStateMachine) {
                luaStateMachine.trigger(GunAnimationConstant.INPUT_INSPECT);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (LOGGED_UNSUPPORTED_RENDERERS.add(renderer.getClass())) {
                TaczVr.LOGGER.warn(
                        "Failed to trigger LRTactical inspect through renderer {}",
                        renderer.getClass().getName(),
                        exception
                );
            }
        }
        return false;
    }

    private static LocalPlayer getPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null
                || player.isSpectator()
                || !LrTacticalVrItemSupport.isRenderSupported(player.getMainHandItem())) {
            return null;
        }
        return player;
    }
}
