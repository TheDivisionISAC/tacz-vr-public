package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.mixin.client.BedrockAttachmentModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Resolves TACZ's final animated {@code scope_pos + ocular} transform into one public HMD-centered measurement per
 * Vivecraft render frame while separately feeding each eye's uncentered measurement to its own aperture gate. The
 * second stereo eye never replaces or advances the public HMD-centered frame cache. The supplied pose already
 * includes TACZ VR's controller alignment and stabilization.
 * Vivecraft renders each eye from an eye-relative origin and basis, so the bridge-provided eye-to-HMD rigid transform
 * is applied exactly once before caching. This class only reads model nodes and matrices; it never mutates the live
 * pose or render state.
 */
public final class VrScopeAlignmentCapture {
    private static final Vector3f BEDROCK_SCOPE_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final VrScopeAlignmentFrameCache FRAME_CACHE = new VrScopeAlignmentFrameCache();
    private static final VrScopeAlignmentDiagnostics DIAGNOSTICS = new VrScopeAlignmentDiagnostics();
    private static final VrScopeAlignmentGateDiagnostics GATE_DIAGNOSTICS =
            new VrScopeAlignmentGateDiagnostics();
    private static final VrScopePerEyeGateRuntime EYE_GATE_RUNTIME = new VrScopePerEyeGateRuntime();
    private static final VrScopeRenderRequestCache RENDER_REQUEST_CACHE = new VrScopeRenderRequestCache();
    private static final VrScopeRenderRequestDiagnostics RENDER_REQUEST_DIAGNOSTICS =
            new VrScopeRenderRequestDiagnostics();
    private static final Map<BedrockAttachmentModel, Map<Integer, Optional<VrScopeModelAperture>>>
            MODEL_APERTURE_CACHE = new IdentityHashMap<>();
    private static final Map<BedrockAttachmentModel, Optional<VrScopeModelAperture>>
            MODEL_RING_CACHE = new IdentityHashMap<>();
    private static final Map<BedrockAttachmentModel, Map<Integer, Optional<VrScopeModelAperture>>>
            MODEL_DIVISION_CACHE = new IdentityHashMap<>();

    private VrScopeAlignmentCapture() {
    }

    public static void capture(BedrockGunModel gunModel, ItemStack gunStack, PoseStack gunPose) {
        OptionalLong optionalFrameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        if (optionalFrameIndex.isEmpty()) {
            return;
        }
        long frameIndex = optionalFrameIndex.getAsLong();
        boolean frameAlreadyCaptured = FRAME_CACHE.contains(frameIndex);
        if (frameAlreadyCaptured
                && !TaczVrClientConfig.ENABLE_VR_PHYSICAL_SCOPE_GATE.get()
                && !DIAGNOSTICS.isEnabled()
                && !GATE_DIAGNOSTICS.isEnabled()) {
            return;
        }
        Optional<VrEyeToHeadTransform> eyeToHead = TaczVrClient.vivecraft().getCurrentEyeToHeadTransform();
        if (eyeToHead.isEmpty()) {
            return;
        }

        ResolvedScope scope = resolveScope(gunModel, gunStack);
        if (scope == null) {
            return;
        }
        PoseStack lensPose = copyPose(gunPose);
        for (BedrockPart part : scope.gunScopePath()) {
            part.translateAndRotateAndScale(lensPose);
        }
        // AttachmentRender applies this root correction before BedrockAttachmentModel.render.
        lensPose.translate(0.0D, -1.5D, 0.0D);
        for (BedrockPart part : scope.scopeOcularPath()) {
            part.translateAndRotateAndScale(lensPose);
        }

        Matrix4f lensMatrix = lensPose.last().pose();
        Vector3f lensViewPosition = lensMatrix.getTranslation(new Vector3f());
        Vector3f lensViewAxis = lensMatrix.transformDirection(new Vector3f(BEDROCK_SCOPE_FORWARD));
        Optional<VrScopeAlignmentSample> eyeSample = VrScopeAlignmentSample.measured(
                frameIndex,
                scope.gunId(),
                scope.scopeId(),
                scope.viewIndex(),
                new Vec3(lensViewPosition.x, lensViewPosition.y, lensViewPosition.z),
                new Vec3(lensViewAxis.x, lensViewAxis.y, lensViewAxis.z)
        );
        Vec3 lensHeadPosition = eyeToHead.get().transformPosition(
                new Vec3(lensViewPosition.x, lensViewPosition.y, lensViewPosition.z)
        );
        Vec3 lensHeadAxis = eyeToHead.get().transformDirection(
                new Vec3(lensViewAxis.x, lensViewAxis.y, lensViewAxis.z)
        );
        Optional<VrScopeAlignmentSample> sample = VrScopeAlignmentSample.measured(
                frameIndex,
                scope.gunId(),
                scope.scopeId(),
                scope.viewIndex(),
                lensHeadPosition,
                lensHeadAxis
        );
        String renderPass = TaczVrClient.vivecraft().getRenderPassName();
        long nowNanos = System.nanoTime();
        boolean physicalGateEnabled = TaczVrClientConfig.ENABLE_VR_PHYSICAL_SCOPE_GATE.get();
        if (!physicalGateEnabled) {
            RENDER_REQUEST_CACHE.clear();
        }
        Optional<VrScopeAuthorEyeBoxSample> authorEyeBox =
                physicalGateEnabled || GATE_DIAGNOSTICS.isEnabled()
                        ? measureAuthorEyeBox(scope, gunPose, lensMatrix, frameIndex)
                        : Optional.empty();
        Optional<VrScopeDivisionProjectionSample> divisionProjection =
                physicalGateEnabled || GATE_DIAGNOSTICS.isEnabled()
                        ? measureDivisionProjection(scope, gunPose, lensMatrix, frameIndex)
                        : Optional.empty();
        if (GATE_DIAGNOSTICS.isEnabled()) {
            authorEyeBox.ifPresent(candidate ->
                    GATE_DIAGNOSTICS.recordAuthorEyeBox(candidate, renderPass, nowNanos)
            );
            divisionProjection.ifPresent(candidate ->
                    GATE_DIAGNOSTICS.recordDivisionProjection(candidate, renderPass, nowNanos)
            );
        }
        eyeSample.ifPresent(candidate -> {
            if (physicalGateEnabled) {
                EYE_GATE_RUNTIME.record(
                        renderPass,
                        candidate,
                        authorEyeBox.orElse(null),
                        divisionProjection.orElse(null),
                        scope.attachmentModel(),
                        VrScopeAlignmentGateTuning.observationProfile(),
                        nowNanos
                );
            } else {
                EYE_GATE_RUNTIME.reset();
            }
            GATE_DIAGNOSTICS.recordEyeCandidate(candidate, renderPass, nowNanos);
            authorEyeBox.ifPresent(eyeBox ->
                    GATE_DIAGNOSTICS.recordNearEyeAngle(candidate, eyeBox, renderPass, nowNanos)
            );
        });
        if (physicalGateEnabled && scope.scopeViewPath() != null && !scope.scopeViewPath().isEmpty()) {
            boolean magnificationEnabled = TaczVrClientConfig.ENABLE_VR_SCOPE_MAGNIFICATION.get();
            double renderActivationProgress = magnificationEnabled ? EYE_GATE_RUNTIME.progressFor(
                    renderPass, scope.attachmentModel(), scope.gunId(), frameIndex
            ) : EYE_GATE_RUNTIME.renderActivationProgressFor(
                    renderPass,
                    scope.attachmentModel(),
                    scope.gunId(),
                    frameIndex
            );
            Minecraft minecraft = Minecraft.getInstance();
            boolean prewarm = magnificationEnabled && VrScopePrewarmGate.permits(
                    eyeSample.orElse(null), authorEyeBox.orElse(null)
            );
            if ((renderActivationProgress > 0.0D || prewarm) && minecraft.level != null && minecraft.player != null) {
                Matrix4f scopeViewMatrix = scopePathMatrix(
                        gunPose,
                        scope.gunScopePath(),
                        scope.scopeViewPath()
                );
                IGun gun = IGun.getIGunOrNull(gunStack);
                if (gun != null) {
                    Optional<MeasuredLens> lens = TaczVrClientConfig.ENABLE_VR_SCOPE_MAGNIFICATION.get()
                            ? measureLensView(scope, gunPose, lensMatrix, scopeViewMatrix) : Optional.empty();
                    // Preparation while closed is invisible. Once the unchanged physical aperture starts opening,
                    // record this eye's plane even in acquisition preview, so its first opening can use the texture.
                    if (renderActivationProgress > 0.0D) {
                        lens.ifPresent(value -> VrScopeMagnificationRuntime.recordLens(
                                renderPass, frameIndex, scope.attachmentModel(), scope.gunId(), scope.scopeId(),
                                scope.viewIndex(), value.divisionIndex(), value.view()
                        ));
                    }
                    if (TaczVrClientConfig.ENABLE_VR_SCOPE_MAGNIFICATION.get() && lens.isEmpty()) {
                        RENDER_REQUEST_CACHE.clear();
                    } else {
                        VrScopeRenderRequestCapture.capture(
                                frameIndex,
                                minecraft.level.dimension().location(),
                                scope.gunId(),
                                scope.scopeId(),
                                scope.viewIndex(),
                                scopeViewMatrix,
                                eyeToHead.get(),
                                gun.getAimingZoom(gunStack),
                                lens.map(value -> value.view().halfTan()).orElse(Math.tan(Math.toRadians(35.0D))),
                                renderActivationProgress
                        ).ifPresent(request -> {
                            boolean accepted = RENDER_REQUEST_CACHE.offer(
                                    request, minecraft.level, minecraft.player.getMainHandItem()
                            );
                            RENDER_REQUEST_DIAGNOSTICS.record(request, renderPass, accepted, nowNanos);
                        });
                    }
                }
            }
        }
        sample.ifPresent(candidate -> {
            if (frameAlreadyCaptured) {
                FRAME_CACHE.forFrame(frameIndex).ifPresent(reference ->
                        DIAGNOSTICS.recordStereoComparison(
                                reference,
                                candidate,
                                renderPass,
                                System.nanoTime()
                        )
                );
            } else if (FRAME_CACHE.accept(candidate)) {
                if (DIAGNOSTICS.isEnabled()) {
                    DIAGNOSTICS.record(candidate, renderPass, nowNanos);
                }
                GATE_DIAGNOSTICS.record(candidate, renderPass, nowNanos);
            }
        });
    }

    public static Optional<VrScopeAlignmentSample> latest() {
        OptionalLong frameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        return frameIndex.isPresent()
                ? FRAME_CACHE.forFrame(frameIndex.getAsLong())
                : Optional.empty();
    }

    /**
     * Returns one multiplier for the current eye's scope-model render. Disabled and non-VR paths preserve TACZ's
     * authored value; enabled paths fail closed when the current pass, frame, gun, or model does not match that eye's
     * captured sample. Texture preparation never changes this established opening or its animation timing.
     */
    public static float scopeRadiusScale(BedrockAttachmentModel scopeModel, ItemStack gunStack) {
        if (!ownsScopeOcularOpening()) {
            EYE_GATE_RUNTIME.reset();
            return 1.0F;
        }
        OptionalLong optionalFrameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (optionalFrameIndex.isEmpty() || gun == null) {
            EYE_GATE_RUNTIME.reset();
            return 0.0F;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) {
            EYE_GATE_RUNTIME.reset();
            return 0.0F;
        }
        double progress = EYE_GATE_RUNTIME.progressFor(
                TaczVrClient.vivecraft().getRenderPassName(),
                scopeModel,
                gunId,
                optionalFrameIndex.getAsLong()
        );
        return (float) Math.max(0.0D, Math.min(1.0D, progress));
    }

    /**
     * Returns the current eye's corrected TACZ circle center on its fixed {@code z=-90} plane. Invalid render-pass,
     * frame, model, gun, or geometry facts fail closed.
     */
    public static Optional<VrScopeCircleCenter> scopeCircleCenter(
            BedrockAttachmentModel scopeModel,
            ItemStack gunStack
    ) {
        if (!ownsScopeOcularOpening()) {
            return Optional.empty();
        }
        OptionalLong optionalFrameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        IGun gun = IGun.getIGunOrNull(gunStack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(gunStack);
        if (optionalFrameIndex.isEmpty() || gunId == null) {
            return Optional.empty();
        }
        VrScopePerEyeGateRuntime.OptionalCenter center = EYE_GATE_RUNTIME.circleCenterFor(
                TaczVrClient.vivecraft().getRenderPassName(),
                scopeModel,
                gunId,
                optionalFrameIndex.getAsLong()
        );
        return center.present()
                ? Optional.of(new VrScopeCircleCenter(center.x(), center.y()))
                : Optional.empty();
    }

    /**
     * Records the exact radius modifier handed to TACZ's scope model at the final render application point. This is
     * an opt-in diagnostic only: normal rendering avoids attachment lookup and no value is ever changed here.
     */
    public static void recordAppliedScopeRadius(
            BedrockAttachmentModel scopeModel,
            ItemStack gunStack,
            float physicalProgress,
            float authorRadiusModifier,
            float appliedRadiusModifier
    ) {
        if (!GATE_DIAGNOSTICS.isEnabled()) {
            return;
        }
        OptionalLong optionalFrameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        AppliedScopeIdentity identity = resolveAppliedScopeIdentity(scopeModel, gunStack);
        if (optionalFrameIndex.isEmpty() || identity == null) {
            return;
        }
        GATE_DIAGNOSTICS.recordAppliedAperture(
                optionalFrameIndex.getAsLong(),
                identity.gunId(),
                identity.scopeId(),
                identity.viewIndex(),
                TaczVrClient.vivecraft().getRenderPassName(),
                physicalProgress,
                authorRadiusModifier,
                appliedRadiusModifier,
                System.nanoTime()
        );
    }

    public static boolean ownsScopeOcularOpening() {
        return TaczVrClientConfig.ENABLE_VR_PHYSICAL_SCOPE_GATE.get()
                && TaczVrClient.vivecraft().isVrActive();
    }

    public static void resetTransientState() {
        VrScopeMagnificationRuntime.reset();
        FRAME_CACHE.reset();
        DIAGNOSTICS.reset();
        GATE_DIAGNOSTICS.reset();
        EYE_GATE_RUNTIME.reset();
        RENDER_REQUEST_CACHE.reset();
        RENDER_REQUEST_DIAGNOSTICS.reset();
        MODEL_APERTURE_CACHE.clear();
        MODEL_RING_CACHE.clear();
        MODEL_DIVISION_CACHE.clear();
        VrScopeDivisionRenderType.reset();
        VrScopeLateDivisionRenderer.reset();
    }

    /**
     * Resource reload callbacks are allowed to run outside the client thread. All scope caches are render-thread
     * owned, so marshal the reset instead of racing an eye pass with model replacement.
     */
    public static void resetForResourceReload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            resetTransientState();
        } else {
            minecraft.execute(VrScopeAlignmentCapture::resetTransientState);
        }
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Optional<VrScopeAuthorEyeBoxSample> measureAuthorEyeBox(
            ResolvedScope scope,
            PoseStack gunPose,
            Matrix4f ocularMatrix,
            long frameIndex
    ) {
        if (scope.scopeViewPath() == null || scope.scopeViewPath().isEmpty()) {
            return Optional.empty();
        }
        Optional<VrScopeModelAperture.Transformed> aperture =
                localAperture(scope)
                        .flatMap(candidate -> candidate.transform(ocularMatrix));
        if (aperture.isEmpty()) {
            return Optional.empty();
        }

        PoseStack authorEyePose = copyPose(gunPose);
        for (BedrockPart part : scope.gunScopePath()) {
            part.translateAndRotateAndScale(authorEyePose);
        }
        authorEyePose.translate(0.0D, -1.5D, 0.0D);
        for (BedrockPart part : scope.scopeViewPath()) {
            part.translateAndRotateAndScale(authorEyePose);
        }
        Matrix4f authorEyeMatrix = authorEyePose.last().pose();
        Vector3f authorEyePosition = authorEyeMatrix.getTranslation(new Vector3f());
        Vector3f authorViewAxis = authorEyeMatrix.transformDirection(new Vector3f(BEDROCK_SCOPE_FORWARD));
        return VrScopeAuthorEyeBoxSample.measured(
                frameIndex,
                scope.gunId(),
                scope.scopeId(),
                scope.viewIndex(),
                aperture.get(),
                new Vec3(authorEyePosition.x, authorEyePosition.y, authorEyePosition.z),
                new Vec3(authorViewAxis.x, authorViewAxis.y, authorViewAxis.z)
        );
    }

    private static Optional<VrScopeModelAperture> localAperture(ResolvedScope scope) {
        Map<Integer, Optional<VrScopeModelAperture>> byView =
                MODEL_APERTURE_CACHE.computeIfAbsent(scope.attachmentModel(), ignored -> new HashMap<>());
        return byView.computeIfAbsent(
                scope.viewIndex(),
                ignored -> VrScopeModelAperture.fromOcularPath(scope.scopeOcularPath())
        );
    }

    private static Optional<VrScopeDivisionProjectionSample> measureDivisionProjection(
            ResolvedScope scope,
            PoseStack gunPose,
            Matrix4f ocularMatrix,
            long frameIndex
    ) {
        if (scope.scopeViewPath() == null || scope.scopeViewPath().isEmpty()) {
            return Optional.empty();
        }
        BedrockAttachmentModelAccessor accessor = (BedrockAttachmentModelAccessor) scope.attachmentModel();
        List<BedrockPart> divisionPath = selectOcularPath(
                accessor.taczvr$getDivisionNodePaths(),
                scope.viewIndex()
        );
        if (divisionPath == null) {
            return Optional.empty();
        }

        Optional<VrScopeModelAperture.Transformed> ocular = localAperture(scope)
                .flatMap(candidate -> candidate.transform(ocularMatrix));
        Optional<VrScopeModelAperture.Transformed> division = localDivision(scope, divisionPath)
                .flatMap(candidate -> candidate.transform(scopePathMatrix(gunPose, scope.gunScopePath(), divisionPath)));
        if (ocular.isEmpty() || division.isEmpty()) {
            return Optional.empty();
        }

        List<BedrockPart> ringPath = accessor.taczvr$getOcularRingPath();
        Optional<VrScopeModelAperture.Transformed> ring = localRing(scope, ringPath)
                .flatMap(candidate -> candidate.transform(scopePathMatrix(gunPose, scope.gunScopePath(), ringPath)));
        boolean ocularFallback = ring.isEmpty();
        VrScopeModelAperture.Transformed projectionPlane = ring.orElse(ocular.get());
        Matrix4f authorMatrix = scopePathMatrix(gunPose, scope.gunScopePath(), scope.scopeViewPath());
        Vector3f authorPosition = authorMatrix.getTranslation(new Vector3f());
        Vector3f ocularPivot = ocularMatrix.getTranslation(new Vector3f());
        return VrScopeDivisionProjectionSample.measured(
                frameIndex,
                scope.gunId(),
                scope.scopeId(),
                scope.viewIndex(),
                ocular.get(),
                projectionPlane,
                division.get(),
                new Vec3(authorPosition.x, authorPosition.y, authorPosition.z),
                ocularFallback,
                new Vec3(ocularPivot.x, ocularPivot.y, ocularPivot.z),
                new Matrix4f(RenderSystem.getModelViewMatrix())
        );
    }

    private static Optional<MeasuredLens> measureLensView(ResolvedScope scope, PoseStack gunPose,
                                                         Matrix4f ocularMatrix, Matrix4f scopeMatrix) {
        BedrockAttachmentModelAccessor accessor = (BedrockAttachmentModelAccessor) scope.attachmentModel();
        List<List<BedrockPart>> divisionPaths = accessor.taczvr$getDivisionNodePaths();
        int divisionIndex = selectOcularPathIndex(divisionPaths, scope.viewIndex());
        int ocularIndex = selectOcularPathIndex(accessor.taczvr$getOcularNodePaths(), scope.viewIndex());
        // TACZ display.views selects a camera, not a stencil layer. ELCAN/Contender use view 2 while their only
        // ocular/division pair is layer 0. Preserve the actual paired path index instead of comparing it to view.
        if (divisionIndex < 0 || divisionIndex != ocularIndex) return Optional.empty();
        List<BedrockPart> divisionPath = divisionPaths.get(divisionIndex);
        Optional<VrScopeModelAperture.Transformed> ocular = localAperture(scope)
                .flatMap(value -> value.transform(ocularMatrix));
        Optional<VrScopeModelAperture.Transformed> division = localDivision(scope, divisionPath)
                .flatMap(value -> value.transform(scopePathMatrix(gunPose, scope.gunScopePath(), divisionPath)));
        return ocular.isPresent() && division.isPresent()
                ? VrScopeLensView.measured(new Matrix4f(RenderSystem.getModelViewMatrix()), scopeMatrix,
                        ocular.get(), division.get()).map(view -> new MeasuredLens(divisionIndex, view)) : Optional.empty();
    }

    /** Current client-thread identity, shared by scheduling and composition; never reads a tracking provider. */
    public static Optional<LiveScope> currentScope() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return Optional.empty();
        ItemStack item = minecraft.player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(item);
        if (gun == null) return Optional.empty();
        ResourceLocation scopeId = gun.getAttachmentId(item, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) scopeId = gun.getBuiltInAttachmentId(item, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) return Optional.empty();
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
        if (index == null || !index.isScope() || index.getAttachmentModel() == null) return Optional.empty();
        AppliedScopeIdentity identity = resolveAppliedScopeIdentity(index.getAttachmentModel(), item);
        double magnification = gun.getAimingZoom(item);
        if (identity == null || identity.viewIndex() < 0 || !Double.isFinite(magnification) || magnification <= 0) {
            return Optional.empty();
        }
        return Optional.of(new LiveScope(minecraft.level, item, minecraft.level.dimension().location(),
                identity.gunId(), identity.scopeId(), identity.viewIndex(), magnification, index.getAttachmentModel()));
    }

    public static Optional<VrScopeRenderRequest> claimRenderRequest(long frame, LiveScope live) {
        return RENDER_REQUEST_CACHE.claim(frame, live.world(), live.item(), live.dimension(), live.gunId(),
                live.scopeId(), live.viewIndex(), live.magnification());
    }

    /** Render-thread-only opaque lifetime identities with immutable logical ids for the current held scope. */
    public record LiveScope(Object world, ItemStack item, ResourceLocation dimension, ResourceLocation gunId,
                            ResourceLocation scopeId, int viewIndex, double magnification, BedrockAttachmentModel model) {
        public boolean matches(VrScopeRenderRequest request) {
            return request != null && dimension.equals(request.dimensionId()) && gunId.equals(request.gunId())
                    && scopeId.equals(request.scopeId()) && viewIndex == request.viewIndex()
                    && Double.compare(magnification, request.magnification()) == 0;
        }
    }

    private static Optional<VrScopeModelAperture> localRing(
            ResolvedScope scope,
            List<BedrockPart> ringPath
    ) {
        if (ringPath == null || ringPath.isEmpty()) {
            return Optional.empty();
        }
        return MODEL_RING_CACHE.computeIfAbsent(
                scope.attachmentModel(),
                ignored -> VrScopeModelAperture.fromPath(ringPath)
        );
    }

    private static Optional<VrScopeModelAperture> localDivision(
            ResolvedScope scope,
            List<BedrockPart> divisionPath
    ) {
        Map<Integer, Optional<VrScopeModelAperture>> byView =
                MODEL_DIVISION_CACHE.computeIfAbsent(scope.attachmentModel(), ignored -> new HashMap<>());
        return byView.computeIfAbsent(
                scope.viewIndex(),
                ignored -> VrScopeModelAperture.fromPath(divisionPath)
        );
    }

    private static Matrix4f scopePathMatrix(
            PoseStack gunPose,
            List<BedrockPart> gunScopePath,
            List<BedrockPart> attachmentPath
    ) {
        PoseStack pose = copyPose(gunPose);
        for (BedrockPart part : gunScopePath) {
            part.translateAndRotateAndScale(pose);
        }
        pose.translate(0.0D, -1.5D, 0.0D);
        for (BedrockPart part : attachmentPath) {
            part.translateAndRotateAndScale(pose);
        }
        return new Matrix4f(pose.last().pose());
    }

    private static ResolvedScope resolveScope(BedrockGunModel gunModel, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        List<BedrockPart> gunScopePath = gunModel.getScopePosPath();
        if (gun == null || gunScopePath == null || gunScopePath.isEmpty()) {
            return null;
        }

        ResourceLocation scopeId = gun.getAttachmentId(gunStack, AttachmentType.SCOPE);
        boolean builtIn = DefaultAssets.isEmptyAttachmentId(scopeId);
        if (builtIn) {
            scopeId = gun.getBuiltInAttachmentId(gunStack, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return null;
        }

        ClientAttachmentIndex attachmentIndex = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
        if (attachmentIndex == null || !attachmentIndex.isScope()) {
            return null;
        }
        BedrockAttachmentModel attachmentModel = attachmentIndex.getAttachmentModel();
        int[] views = attachmentIndex.getViews();
        if (attachmentModel == null || views == null || views.length == 0) {
            return null;
        }

        CompoundTag scopeTag = gun.getAttachmentTag(gunStack, AttachmentType.SCOPE);
        int zoomNumber = builtIn ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        int viewIndex = views[Math.floorMod(zoomNumber, views.length)] - 1;
        List<List<BedrockPart>> ocularNodePaths =
                ((BedrockAttachmentModelAccessor) attachmentModel).taczvr$getOcularNodePaths();
        List<BedrockPart> scopeOcularPath = selectOcularPath(ocularNodePaths, viewIndex);
        if (scopeOcularPath == null) {
            return null;
        }
        List<BedrockPart> scopeViewPath = viewIndex >= 0
                ? attachmentModel.getScopeViewPath(viewIndex)
                : null;
        return new ResolvedScope(
                gun.getGunId(gunStack),
                scopeId,
                viewIndex,
                attachmentModel,
                gunScopePath,
                scopeOcularPath,
                scopeViewPath
        );
    }

    private static AppliedScopeIdentity resolveAppliedScopeIdentity(
            BedrockAttachmentModel expectedScopeModel,
            ItemStack gunStack
    ) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return null;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) {
            return null;
        }

        ResourceLocation scopeId = gun.getAttachmentId(gunStack, AttachmentType.SCOPE);
        boolean builtIn = DefaultAssets.isEmptyAttachmentId(scopeId);
        if (builtIn) {
            scopeId = gun.getBuiltInAttachmentId(gunStack, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return null;
        }

        ClientAttachmentIndex attachmentIndex = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
        if (attachmentIndex == null
                || !attachmentIndex.isScope()
                || attachmentIndex.getAttachmentModel() != expectedScopeModel) {
            return null;
        }
        int[] views = attachmentIndex.getViews();
        if (views == null || views.length == 0) {
            return null;
        }
        CompoundTag scopeTag = gun.getAttachmentTag(gunStack, AttachmentType.SCOPE);
        int zoomNumber = builtIn ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        int viewIndex = views[Math.floorMod(zoomNumber, views.length)] - 1;
        return new AppliedScopeIdentity(gunId, scopeId, viewIndex);
    }

    static List<BedrockPart> selectOcularPath(List<List<BedrockPart>> paths, int viewIndex) {
        int index = selectOcularPathIndex(paths, viewIndex);
        return index < 0 ? null : paths.get(index);
    }

    /** Returns the actual authored stencil layer; a camera view number may alias its first available path. */
    static int selectOcularPathIndex(List<List<BedrockPart>> paths, int viewIndex) {
        if (paths == null || paths.isEmpty()) {
            return -1;
        }
        if (viewIndex >= 0 && viewIndex < paths.size()) {
            List<BedrockPart> selected = paths.get(viewIndex);
            if (selected != null && !selected.isEmpty()) {
                return viewIndex;
            }
        }
        for (int index = 0; index < paths.size(); index++) {
            List<BedrockPart> fallback = paths.get(index);
            if (fallback != null && !fallback.isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private record MeasuredLens(int divisionIndex, VrScopeLensView view) {
    }

    private record ResolvedScope(
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            BedrockAttachmentModel attachmentModel,
            List<BedrockPart> gunScopePath,
            List<BedrockPart> scopeOcularPath,
            List<BedrockPart> scopeViewPath
    ) {
    }

    private record AppliedScopeIdentity(ResourceLocation gunId, ResourceLocation scopeId, int viewIndex) {
    }

    public record VrScopeCircleCenter(double x, double y) {
    }
}
