package com.thedivisionisac.taczvr.server.shot;

import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrShotInaccuracyPolicyTest {
    @Test
    void vrPistolAlwaysUsesAimInaccuracy() {
        for (InaccuracyType original : InaccuracyType.values()) {
            assertEquals(
                    InaccuracyType.AIM,
                    VrShotInaccuracyPolicy.select(
                            original,
                            InaccuracyType.MOVE,
                            true,
                            true,
                            "pistol",
                            false
                    )
            );
        }
    }

    @Test
    void snappedVrLongGunUsesAimInaccuracy() {
        assertEquals(
                InaccuracyType.AIM,
                VrShotInaccuracyPolicy.select(
                        InaccuracyType.STAND,
                        InaccuracyType.STAND,
                        true,
                        true,
                        "rifle",
                        true
                )
        );
    }

    @Test
    void unsnappedVrLongGunUsesHipStateEvenWhileAdsIsActive() {
        assertEquals(
                InaccuracyType.MOVE,
                VrShotInaccuracyPolicy.select(
                        InaccuracyType.AIM,
                        InaccuracyType.MOVE,
                        true,
                        true,
                        "rifle",
                        false
                )
        );
    }

    @Test
    void desktopPlayerKeepsTaczInaccuracyState() {
        assertEquals(
                InaccuracyType.AIM,
                VrShotInaccuracyPolicy.select(
                        InaccuracyType.AIM,
                        InaccuracyType.STAND,
                        true,
                        false,
                        "rifle",
                        false
                )
        );
    }

    @Test
    void disabledPolicyKeepsTaczInaccuracyState() {
        assertEquals(
                InaccuracyType.AIM,
                VrShotInaccuracyPolicy.select(
                        InaccuracyType.AIM,
                        InaccuracyType.STAND,
                        false,
                        true,
                        "rifle",
                        false
                )
        );
    }
}
