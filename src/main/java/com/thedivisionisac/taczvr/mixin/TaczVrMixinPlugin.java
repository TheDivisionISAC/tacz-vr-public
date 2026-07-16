package com.thedivisionisac.taczvr.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Prevents client integration Mixins from resolving Vivecraft classes when the optional client mod is absent.
 * Common networking and server Mixins remain active on both physical sides.
 */
public final class TaczVrMixinPlugin implements IMixinConfigPlugin {
    private static final String CLIENT_MIXIN_PREFIX = "com.thedivisionisac.taczvr.mixin.client.";
    private final boolean vivecraftLoaded;

    public TaczVrMixinPlugin() {
        this.vivecraftLoaded = LoadingModList.get().getModFileById("vivecraft") != null;
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
        return !mixinClassName.startsWith(CLIENT_MIXIN_PREFIX) || this.vivecraftLoaded;
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
