package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.TaczVr;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Default-off render-thread telemetry for C3 request capture. It reports bounded aggregate accepted/duplicate counts
 * and HMD-local camera facts, but never consumes the request or enables a Vivecraft world pass.
 */
final class VrScopeRenderRequestDiagnostics {
    static final String ENABLE_PROPERTY = "taczvr.debugScopeRenderRequest";
    private static final long DEFAULT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100L);

    private final boolean enabled;
    private final long intervalNanos;
    private final Consumer<String> sink;

    @Nullable
    private SourceKey source;
    private long lastEmissionNanos = Long.MIN_VALUE;
    private long offered;
    private long accepted;
    private long duplicates;

    VrScopeRenderRequestDiagnostics() {
        this(
                Boolean.getBoolean(ENABLE_PROPERTY),
                DEFAULT_INTERVAL_NANOS,
                message -> TaczVr.LOGGER.info(message)
        );
        if (this.enabled) {
            TaczVr.LOGGER.info("TACZVR_SCOPE_REQUEST_DIAG enabled interval_ms=100 stage=capture");
        }
    }

    VrScopeRenderRequestDiagnostics(boolean enabled, long intervalNanos, Consumer<String> sink) {
        this.enabled = enabled;
        this.intervalNanos = Math.max(1L, intervalNanos);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    void record(VrScopeRenderRequest request, String renderPass, boolean wasAccepted, long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(renderPass, "renderPass");
        SourceKey nextSource = new SourceKey(
                request.dimensionId(), request.gunId(), request.scopeId(), request.viewIndex()
        );
        boolean sourceChanged = !nextSource.equals(this.source);
        boolean clockRegressed = this.lastEmissionNanos != Long.MIN_VALUE && nowNanos < this.lastEmissionNanos;
        this.offered++;
        if (wasAccepted) {
            this.accepted++;
        } else {
            this.duplicates++;
        }
        if (!sourceChanged && !clockRegressed && this.lastEmissionNanos != Long.MIN_VALUE
                && nowNanos - this.lastEmissionNanos < this.intervalNanos) {
            return;
        }

        this.source = nextSource;
        this.lastEmissionNanos = nowNanos;
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_REQUEST_DIAG frame=%d pass=%s accepted_now=%s offered_total=%d "
                        + "accepted_total=%d duplicate_total=%d dimension=%s gun=%s scope=%s view=%d "
                        + "head_x_m=%.6f head_y_m=%.6f head_z_m=%.6f magnification=%.4f aperture=%.6f",
                request.sourceFrameIndex(),
                renderPass,
                wasAccepted,
                this.offered,
                this.accepted,
                this.duplicates,
                request.dimensionId(),
                request.gunId(),
                request.scopeId(),
                request.viewIndex(),
                request.scopeViewPositionInHead().x,
                request.scopeViewPositionInHead().y,
                request.scopeViewPositionInHead().z,
                request.magnification(),
                request.apertureProgress()
        ));
    }

    void reset() {
        this.source = null;
        this.lastEmissionNanos = Long.MIN_VALUE;
        this.offered = 0L;
        this.accepted = 0L;
        this.duplicates = 0L;
    }

    private record SourceKey(
            net.minecraft.resources.ResourceLocation dimensionId,
            net.minecraft.resources.ResourceLocation gunId,
            net.minecraft.resources.ResourceLocation scopeId,
            int viewIndex
    ) {
    }
}
