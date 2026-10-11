package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * {@code gate-ripple} ships off, and reads as off from a {@code config.yml} written before it existed (#579).
 *
 * <p>The ripple is new and has not been seen in a world yet, so a server has to ask for it. An
 * upgraded server's file has no line for it until the plugin writes one, and a getter that read a
 * missing setting as on would turn it on for every one of them.
 */
class GateRippleSettingTest
{
    @AfterEach
    void forgetIt()
    {
        ConfigTestSupport.clear();
    }

    @Test
    void itShipsOff()
    {
        final Setting shipped = Arrays.stream(DefaultSettings.config).filter(s -> s.getName() == ConfigKeys.GATE_RIPPLE)
            .findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, shipped.getValue(), "opt-in until the owner has seen it in a world");
    }

    @Test
    void aConfigWrittenBeforeItExistedReadsAsOff()
    {
        ConfigTestSupport.clear();

        assertFalse(ConfigManager.isGateRipple());
    }

    @Test
    void turnedOnItReadsAsOn()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, true);

        assertTrue(ConfigManager.isGateRipple());
    }
}
