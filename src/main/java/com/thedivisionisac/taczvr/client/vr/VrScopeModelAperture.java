package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Read-only model-space bounds of the geometry TACZ actually renders for an ocular stencil. Cube coordinates are
 * stored relative to their owning Bedrock bone in model units, while child transforms use the same 1/16 conversion
 * as {@link BedrockPart#translateAndRotateAndScale(PoseStack)}.
 */
record VrScopeModelAperture(Vec3 localCenter, double halfWidth, double halfHeight) {
    private static final double MODEL_UNIT = 1.0D / 16.0D;
    private static final double MINIMUM_EXTENT = 1.0E-6D;

    static Optional<VrScopeModelAperture> fromOcularPath(List<BedrockPart> ocularPath) {
        return fromPath(ocularPath);
    }

    /**
     * Reads planar bounds from the final bone and its children without mutating live model visibility.
     */
    static Optional<VrScopeModelAperture> fromPath(List<BedrockPart> path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        BedrockPart root = path.get(path.size() - 1);
        if (root == null) {
            return Optional.empty();
        }

        Bounds bounds = new Bounds();
        collect(root, new PoseStack(), false, bounds);
        if (!bounds.isUsable()) {
            return Optional.empty();
        }
        return Optional.of(new VrScopeModelAperture(
                new Vec3(
                        (bounds.minX + bounds.maxX) * 0.5D,
                        (bounds.minY + bounds.maxY) * 0.5D,
                        (bounds.minZ + bounds.maxZ) * 0.5D
                ),
                (bounds.maxX - bounds.minX) * 0.5D,
                (bounds.maxY - bounds.minY) * 0.5D
        ));
    }

    Optional<Transformed> transform(Matrix4f ocularMatrix) {
        if (ocularMatrix == null || !isFinite(this.localCenter)
                || !Double.isFinite(this.halfWidth) || !Double.isFinite(this.halfHeight)
                || this.halfWidth <= MINIMUM_EXTENT || this.halfHeight <= MINIMUM_EXTENT) {
            return Optional.empty();
        }

        Vector3f center = ocularMatrix.transformPosition(new Vector3f(
                (float) this.localCenter.x,
                (float) this.localCenter.y,
                (float) this.localCenter.z
        ));
        Vector3f right = ocularMatrix.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = ocularMatrix.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f axis = ocularMatrix.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
        double radiusX = this.halfWidth * right.length();
        double radiusY = this.halfHeight * up.length();
        Vec3 centerVector = new Vec3(center.x, center.y, center.z);
        Vec3 rightVector = new Vec3(right.x, right.y, right.z);
        Vec3 upVector = new Vec3(up.x, up.y, up.z);
        Vec3 axisVector = new Vec3(axis.x, axis.y, axis.z);
        if (!isFinite(centerVector) || !isFinite(rightVector) || !isFinite(upVector) || !isFinite(axisVector)
                || !Double.isFinite(radiusX) || !Double.isFinite(radiusY)
                || radiusX <= MINIMUM_EXTENT || radiusY <= MINIMUM_EXTENT) {
            return Optional.empty();
        }
        return Optional.of(new Transformed(
                centerVector,
                rightVector,
                upVector,
                axisVector,
                radiusX,
                radiusY
        ));
    }

    private static void collect(BedrockPart part, PoseStack poseStack, boolean applyPartTransform, Bounds bounds) {
        poseStack.pushPose();
        if (applyPartTransform) {
            part.translateAndRotateAndScale(poseStack);
        }
        Matrix4f matrix = poseStack.last().pose();
        if (part.cubes != null) {
            for (BedrockCube cube : part.cubes) {
                if (cube instanceof BedrockCubeBox box) {
                    bounds.include(matrix, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                } else if (cube instanceof BedrockCubePerFace box) {
                    bounds.include(matrix, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                }
            }
        }
        if (part.children != null) {
            for (BedrockPart child : part.children) {
                if (child != null) {
                    collect(child, poseStack, true, bounds);
                }
            }
        }
        poseStack.popPose();
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    record Transformed(
            Vec3 center,
            Vec3 right,
            Vec3 up,
            Vec3 axis,
            double radiusX,
            double radiusY
    ) {
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(
                Matrix4f matrix,
                float cubeMinX,
                float cubeMinY,
                float cubeMinZ,
                float cubeMaxX,
                float cubeMaxY,
                float cubeMaxZ
        ) {
            for (int xIndex = 0; xIndex < 2; xIndex++) {
                double x = (xIndex == 0 ? cubeMinX : cubeMaxX) * MODEL_UNIT;
                for (int yIndex = 0; yIndex < 2; yIndex++) {
                    double y = (yIndex == 0 ? cubeMinY : cubeMaxY) * MODEL_UNIT;
                    for (int zIndex = 0; zIndex < 2; zIndex++) {
                        double z = (zIndex == 0 ? cubeMinZ : cubeMaxZ) * MODEL_UNIT;
                        Vector3f point = matrix.transformPosition(new Vector3f((float) x, (float) y, (float) z));
                        this.minX = Math.min(this.minX, point.x);
                        this.minY = Math.min(this.minY, point.y);
                        this.minZ = Math.min(this.minZ, point.z);
                        this.maxX = Math.max(this.maxX, point.x);
                        this.maxY = Math.max(this.maxY, point.y);
                        this.maxZ = Math.max(this.maxZ, point.z);
                    }
                }
            }
        }

        private boolean isUsable() {
            return Double.isFinite(this.minX) && Double.isFinite(this.minY) && Double.isFinite(this.minZ)
                    && Double.isFinite(this.maxX) && Double.isFinite(this.maxY) && Double.isFinite(this.maxZ)
                    && this.maxX - this.minX > MINIMUM_EXTENT
                    && this.maxY - this.minY > MINIMUM_EXTENT;
        }
    }
}
