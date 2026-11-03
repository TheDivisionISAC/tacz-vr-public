package com.thedivisionisac.taczvr.client.vr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Pure pass-list policy: own one reserved pass before the stereo pair without stealing an existing telescope. */
public final class VrScopePassOrder {
    private VrScopePassOrder() {
    }

    public static <P> Optional<List<P>> beforeEyes(List<? extends P> original, P left, P right, P scope) {
        if (original == null || original.isEmpty() || left == null || right == null || scope == null
                || !left.equals(original.get(0)) || Collections.frequency(original, left) != 1
                || Collections.frequency(original, right) != 1 || original.contains(scope)) return Optional.empty();
        List<P> result = new ArrayList<>(original.size() + 1);
        result.add(scope);
        result.addAll(original);
        return Optional.of(result);
    }
}
