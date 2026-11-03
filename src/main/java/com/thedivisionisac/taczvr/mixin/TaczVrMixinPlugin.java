package com.thedivisionisac.taczvr.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Prevents client integration Mixins from resolving optional Vivecraft or LRTactical classes when their owning mod is
 * absent. Common networking and server Mixins remain active on both physical sides.
 */
public final class TaczVrMixinPlugin implements IMixinConfigPlugin {
    private static final String CLIENT_MIXIN_PREFIX = "com.thedivisionisac.taczvr.mixin.client.";
    private static final String CLIENT_LRTACTICAL_MIXIN_PREFIX = CLIENT_MIXIN_PREFIX + "lrtactical.";
    private static final String COMMON_LRTACTICAL_MIXIN_PREFIX =
            "com.thedivisionisac.taczvr.mixin.common.lrtactical.";
    private final boolean vivecraftLoaded;
    private final boolean lrTacticalLoaded;
    private final boolean oculusLoaded;

    public TaczVrMixinPlugin() {
        this.vivecraftLoaded = LoadingModList.get().getModFileById("vivecraft") != null;
        this.lrTacticalLoaded = LoadingModList.get().getModFileById("lrtactical") != null;
        this.oculusLoaded = LoadingModList.get().getModFileById("oculus") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApplyOptionalMixin(mixinClassName, this.vivecraftLoaded, this.lrTacticalLoaded, this.oculusLoaded);
    }

    static boolean shouldApplyOptionalMixin(String mixinClassName, boolean vivecraftLoaded,
                                            boolean lrTacticalLoaded) {
        return shouldApplyOptionalMixin(mixinClassName, vivecraftLoaded, lrTacticalLoaded, false);
    }

    static boolean shouldApplyOptionalMixin(String mixinClassName, boolean vivecraftLoaded,
                                            boolean lrTacticalLoaded, boolean oculusLoaded) {
        if (mixinClassName.equals(CLIENT_MIXIN_PREFIX + "OculusScopePipelineMixin")) {
            return vivecraftLoaded && oculusLoaded;
        }
        if (mixinClassName.startsWith(COMMON_LRTACTICAL_MIXIN_PREFIX)) {
            return lrTacticalLoaded;
        }
        if (!mixinClassName.startsWith(CLIENT_MIXIN_PREFIX)) {
            return true;
        }
        if (!vivecraftLoaded) {
            return false;
        }
        return !mixinClassName.startsWith(CLIENT_LRTACTICAL_MIXIN_PREFIX) || lrTacticalLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
