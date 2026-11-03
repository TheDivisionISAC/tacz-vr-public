package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.vr.VrScopeMagnificationRuntime;
import com.thedivisionisac.taczvr.client.vr.VrScopeRenderRequest;
import com.thedivisionisac.taczvr.client.vr.VrScopeShaderPassState;
import com.thedivisionisac.taczvr.client.vr.VrScopeShaderPolicy;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import net.minecraftforge.fml.ModList;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4fc;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;
import org.vivecraft.mod_compat_vr.iris.extensions.PipelineManagerExtension;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opt-in Oculus 1.8.0 adapter over Vivecraft 1.3.13's existing per-pass Iris lifecycle. Requires global per-pass
 * shader mode and independently owned shadow/SSBO state, rather than changing first-eye semantics. Reflection is
 * resolved once against a locked class shape; optional Oculus types never enter the addon classpath/API surface.
 * Frame-local proofs are discarded every scheduler frame and revalidated across shader/dimension replacement.
 */
public final class OculusScopeBridge {
    private static final VrScopeShaderPassState PASSES = new VrScopeShaderPassState();
    private static boolean beginObserved;
    private static boolean finalObserved;
    private static Schema schema;
    private static Object scopePipeline;
    private static Object leftPipeline;
    private static Object rightPipeline;
    private static float expectedScale;
    private static boolean shaderFrame;
    private static final boolean DEBUG = Boolean.getBoolean("taczvr.debugScopeMagnification");
    private static final long[] LAST_DIAGNOSTIC = new long[3];

    private OculusScopeBridge() {
    }

    /** Null means allowed. No-shader Oculus still uses ordinary composition; active shaders use final-eye only. */
    public static String prepareFrame(long frame) {
        clearFrame(frame);
        String version = ModList.get().getModContainerById("oculus")
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
        VrScopeShaderPolicy.Decision decision = VrScopeShaderPolicy.decide(IrisHelper.isShaderActive(),
                TaczVrClientConfig.ENABLE_VR_SCOPE_OCULUS_COMPAT.get(), "1.8.0".equals(version));
        if (decision == VrScopeShaderPolicy.Decision.ORDINARY) return null;
        if (decision == VrScopeShaderPolicy.Decision.COMPAT_DISABLED) return "oculus_compat_disabled";
        if (decision == VrScopeShaderPolicy.Decision.VERSION_UNSUPPORTED) return "oculus_version_unsupported";
        if (!ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization || !ShadersHelper.isSlowMode()) {
            return "oculus_requires_per_pass_shader_mode";
        }
        return prepareShaderPipelines(frame);
    }

    /** Clear stale pipeline references even when the main feature is disabled or the player is outside a world. */
    public static void clearFrame(long frame) {
        shaderFrame = false;
        scopePipeline = leftPipeline = rightPipeline = null;
        expectedScale = 0;
        PASSES.reset(frame, null, null, null);
    }

    private static String prepareShaderPipelines(long frame) {
        if (!beginObserved || !finalObserved) return "oculus_lifecycle_hooks_pending";
        var target = ClientDataHolderVR.getInstance().vrRenderer.telescopeFramebufferR;
        if (target == null || target.width <= 0 || target.width != target.height) return "oculus_scope_target_not_square";
        try {
            if (schema == null) schema = new Schema();
            Object manager = IrisHelper.getPipelineManager();
            if (!(manager instanceof PipelineManagerExtension pipelines)) return "oculus_vr_pipeline_api_missing";
            // Toggling slow mode does not rebuild already shared shadow samplers. The manager retains this marker
            // until a reload constructs all VR pipelines under per-pass mode, even if a child has null local fields.
            if (pipelines.vivecraft$getShadowRenderTargets() != null) return "oculus_shared_buffers_reload_shaders";
            scopePipeline = pipelines.vivecraft$getVRPipeline(RenderPass.SCOPER);
            leftPipeline = pipelines.vivecraft$getVRPipeline(RenderPass.LEFT);
            rightPipeline = pipelines.vivecraft$getVRPipeline(RenderPass.RIGHT);
            if (!schema.isPipeline(scopePipeline) || !schema.isPipeline(leftPipeline) || !schema.isPipeline(rightPipeline)
                    || scopePipeline == leftPipeline || scopePipeline == rightPipeline || leftPipeline == rightPipeline) {
                return "oculus_pipelines_not_ready";
            }
            Object shadow = schema.shadowTargets.get(scopePipeline);
            if (schema.shadowRenderer.get(scopePipeline) != null && shadow == null) return "oculus_scope_shadow_missing";
            if (shared(shadow, schema.shadowTargets) || shared(schema.ssbos.get(scopePipeline), schema.ssbos)) {
                return "oculus_shared_buffers_reload_shaders";
            }
            if (sharedImages()) return "oculus_shared_images_reload_shaders";
            PASSES.reset(frame, scopePipeline, leftPipeline, rightPipeline);
            shaderFrame = true;
            VrScopeMagnificationRuntime.configureShaderFrame(OculusScopeBridge::finalEyeReady);
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            VrScopeMagnificationRuntime.fail(error);
            return "oculus_adapter_failure";
        }
    }

    private static boolean shared(Object value, Field field) throws IllegalAccessException {
        Object left = field.get(leftPipeline), right = field.get(rightPipeline);
        return value != null && (value == left || value == right) || left != null && left == right;
    }

    private static boolean sharedImages() throws IllegalAccessException {
        var scope = (java.util.Set<?>) schema.images.get(scopePipeline);
        var left = (java.util.Set<?>) schema.images.get(leftPipeline);
        var right = (java.util.Set<?>) schema.images.get(rightPipeline);
        return overlaps(scope, left) || overlaps(scope, right) || overlaps(left, right);
    }

    private static boolean overlaps(java.util.Set<?> a, java.util.Set<?> b) {
        for (Object first : a) for (Object second : b) if (first == second) return true;
        return false;
    }

    public static void expectProjection(VrScopeRenderRequest request) {
        expectedScale = (float) (request.magnification() / request.halfFovTangent());
    }

    /** Called by an optional thin Mixin, including ordinary eye renders before magnification has been armed. */
    public static void beginPipeline(Object pipeline) {
        VrScopeMagnificationRuntime.registerShaderReset(OculusScopeBridge::reset);
        beginObserved = true;
        if (!shaderFrame) return;
        VrScopeShaderPassState.Pass pass = currentPass();
        if (pass != null) PASSES.begin(frame(), pass, pipeline);
    }

    private static void reset() {
        shaderFrame = beginObserved = finalObserved = false;
        scopePipeline = leftPipeline = rightPipeline = null;
        expectedScale = 0;
        PASSES.reset(Long.MIN_VALUE, null, null, null);
        java.util.Arrays.fill(LAST_DIAGNOSTIC, 0L);
    }

    /** Final color conversion has returned; early G-buffer completion alone is deliberately insufficient. */
    public static void finishPipeline(Object pipeline) {
        finalObserved = true;
        if (!shaderFrame) return;
        VrScopeShaderPassState.Pass pass = currentPass();
        if (pass == null) return;
        try {
            boolean current = IrisHelper.getPipeline().orElse(null) == pipeline;
            boolean override = schema.overrides(pipeline);
            boolean plain = plainShaderReady();
            boolean valid = current && !override && plain;
            float projectionX = Float.NaN, projectionY = Float.NaN;
            if (pass == VrScopeShaderPassState.Pass.SCOPE) {
                Matrix4fc projection = (Matrix4fc) schema.projection.invoke(schema.capturedState);
                if (projection != null) { projectionX = projection.m00(); projectionY = projection.m11(); }
                valid &= expectedScale > 0 && projection != null
                        && near(projection.m00(), expectedScale) && near(projection.m11(), expectedScale);
            }
            boolean complete = PASSES.finish(frame(), pass, pipeline, valid);
            long now = System.nanoTime();
            if (DEBUG && (LAST_DIAGNOSTIC[pass.ordinal()] == 0
                    || now - LAST_DIAGNOSTIC[pass.ordinal()] > 1_000_000_000L)) {
                LAST_DIAGNOSTIC[pass.ordinal()] = now;
                TaczVr.LOGGER.info("TACZVR_SCOPE_OCULUS stage=final pass={} frame={} valid={} complete={} "
                                + "pipeline_match={} override={} plain_shader={} projection_x={} projection_y={} expected_scale={}",
                        pass, frame(), valid, complete, current, override, plain, projectionX, projectionY, expectedScale);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            VrScopeMagnificationRuntime.fail(error);
        }
    }

    private static boolean near(float actual, float expected) {
        return Float.isFinite(actual) && Math.abs(actual - expected) <= Math.max(1.0E-4F, Math.abs(expected) * 1.0E-4F);
    }

    public static boolean scopeComplete() {
        return !shaderFrame || PASSES.complete(frame(), VrScopeShaderPassState.Pass.SCOPE, scopePipeline);
    }

    private static boolean finalEyeReady() {
        VrScopeShaderPassState.Pass pass = currentPass();
        if (!shaderFrame || pass == null || pass == VrScopeShaderPassState.Pass.SCOPE) return false;
        Object expected = pass == VrScopeShaderPassState.Pass.LEFT ? leftPipeline : rightPipeline;
        try {
            return PASSES.complete(frame(), pass, expected) && IrisHelper.getPipeline().orElse(null) == expected
                    && !schema.overrides(expected) && plainShaderReady();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            VrScopeMagnificationRuntime.fail(error);
            return false;
        }
    }

    /** Iris also overrides shader getters during sky/shadow phases independently of its main-world flag. */
    private static boolean plainShaderReady() {
        ShaderInstance shader = GameRenderer.getPositionTexShader();
        return !IrisHelper.isRenderingShadows() && shader != null && shader.getClass() == ShaderInstance.class;
    }

    private static long frame() { return ClientDataHolderVR.getInstance().frameIndex; }

    private static VrScopeShaderPassState.Pass currentPass() {
        return switch (ClientDataHolderVR.getInstance().currentPass) {
            case SCOPER -> VrScopeShaderPassState.Pass.SCOPE;
            case LEFT -> VrScopeShaderPassState.Pass.LEFT;
            case RIGHT -> VrScopeShaderPassState.Pass.RIGHT;
            default -> null;
        };
    }

    private static final class Schema {
        private final Class<?> pipeline = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
        private final Field shadowTargets = field("shadowRenderTargets");
        private final Field shadowRenderer = field("shadowRenderer");
        private final Field ssbos = field("shaderStorageBufferHolder");
        private final Field images = field("customImages");
        private final Method override = pipeline.getMethod("shouldOverrideShaders");
        private final Class<?> captured = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
        private final Object capturedState = captured.getField("INSTANCE").get(null);
        private final Method projection = captured.getMethod("getGbufferProjection");

        private Schema() throws ReflectiveOperationException {
        }

        private Field field(String name) throws NoSuchFieldException {
            Field result = pipeline.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }

        private boolean isPipeline(Object value) { return pipeline.isInstance(value); }
        private boolean overrides(Object value) throws ReflectiveOperationException { return (boolean) override.invoke(value); }
    }
}
