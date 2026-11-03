package com.thedivisionisac.taczvr.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the priority contract for INVOKE injection into Vivecraft's merged hand method.
 * Reads class metadata without initializing client classes; actual transformation is a separate runtime gate.
 */
class HandMixinPriorityTest {
    @Test
    void handWrapperPriorityExceedsLockedVivecraftMethodOwner() throws IOException {
        int owner = mixinPriority("org/vivecraft/mixin/client_vr/renderer/ItemInHandRendererVRMixin.class");
        int wrapper = mixinPriority(
                "com/thedivisionisac/taczvr/mixin/client/ItemInHandRendererPostHandMixin.class");
        assertEquals(999, owner, "Re-audit merged-method injection when the upstream priority changes");
        assertTrue(wrapper > owner,
                "INVOKE injection requires priority above the owner of the merged target method");
    }

    private static int mixinPriority(String resource) throws IOException {
        try (InputStream input = HandMixinPriorityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing locked class resource: " + resource);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertNotNull(node.invisibleAnnotations, "Missing class annotations: " + resource);
            AnnotationNode mixin = node.invisibleAnnotations.stream()
                    .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing @Mixin: " + resource));
            for (int index = 0; mixin.values != null && index < mixin.values.size(); index += 2) {
                if ("priority".equals(mixin.values.get(index))) {
                    return (Integer) mixin.values.get(index + 1);
                }
            }
            return 1000;
        }
    }
}
