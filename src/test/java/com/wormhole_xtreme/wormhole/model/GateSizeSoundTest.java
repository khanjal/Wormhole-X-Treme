package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * A bigger gate sounds deeper and louder, and a smaller one lighter, within limits; {@code Standard}
 * sounds exactly as the configured sounds are written.
 */
class GateSizeSoundTest
{
    private static final float EPSILON = 0.0001f;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static Stargate3DShape shape(final String name) throws Exception
    {
        return new Stargate3DShape(Files.readAllLines(Paths.get("src/main/resources/shapes/gate", name + ".shape"))
            .toArray(new String[0]));
    }

    /** Standard's size changes nothing, so every configured sound plays as written. */
    @Test
    void standardSoundsAsConfigured() throws Exception
    {
        for (final String name : new String[] { "Standard", "StandardSignDial", "Horizontal" })
        {
            final double scale = GateSounds.scaleOf(shape(name));
            assertEquals(1.0, scale, EPSILON, name);
            assertEquals(1.0f, GateSounds.sizePitch(scale), EPSILON, name);
            assertEquals(1.0f, GateSounds.sizeVolume(scale), EPSILON, name);
        }
    }

    /** Large is deeper and louder than Standard, and the biggest gates are at the limit. */
    @Test
    void biggerGatesSoundDeeperAndLouderUpToALimit() throws Exception
    {
        final double large = GateSounds.scaleOf(shape("Large"));
        assertTrue(GateSounds.sizePitch(large) < 1.0f && GateSounds.sizePitch(large) > GateSounds.BIG_PITCH_LIMIT);
        assertTrue(GateSounds.sizeVolume(large) > 1.0f && GateSounds.sizeVolume(large) < GateSounds.BIG_VOLUME_LIMIT);
        for (final String name : new String[] { "Grand", "Massive" })
        {
            final double scale = GateSounds.scaleOf(shape(name));
            // At the limit, or within a hair of it (Grand's 22 lands just short of it on volume).
            assertEquals(GateSounds.BIG_PITCH_LIMIT, GateSounds.sizePitch(scale), 0.02f, name);
            assertEquals(GateSounds.BIG_VOLUME_LIMIT, GateSounds.sizeVolume(scale), 0.02f, name);
        }
    }

    /** The smallest gate is lighter and quieter, to its own limit. */
    @Test
    void theSmallestGateSoundsLighter() throws Exception
    {
        final double scale = GateSounds.scaleOf(shape("Minimal"));
        assertEquals(GateSounds.SMALL_PITCH_LIMIT, GateSounds.sizePitch(scale), EPSILON);
        assertEquals(GateSounds.SMALL_VOLUME_LIMIT, GateSounds.sizeVolume(scale), EPSILON);
    }

    /** The deepest sound a gate makes, the kawoosh, stays within what Minecraft will play. */
    @Test
    void theDeepestKawooshIsStillPlayable()
    {
        assertTrue(GateSounds.KAWOOSH_PITCH * GateSounds.BIG_PITCH_LIMIT >= 0.5f);
    }

    /** A shape can say how big it sounds; anything that is not a positive number falls back to its width. */
    @Test
    void aShapeCanSetItsOwnSoundScale() throws Exception
    {
        final java.util.List<String> lines = new java.util.ArrayList<>(
            Files.readAllLines(Paths.get("src/main/resources/shapes/gate/Standard.shape")));
        lines.add("SOUND_SCALE = 2.5;");
        assertEquals(2.5, new Stargate3DShape(lines.toArray(new String[0])).getShapeSoundScale(), EPSILON);

        lines.set(lines.size() - 1, "SOUND_SCALE=loud");
        assertEquals(1.0, new Stargate3DShape(lines.toArray(new String[0])).getShapeSoundScale(), EPSILON);
    }

    /** A Massive gate's kawoosh reaches the world deeper and louder than the configured one. */
    @Test
    void aMassiveGatesKawooshIsPlayedDeeperAndLouder() throws Exception
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateShape(shape("Massive"));
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 0));

        GateSounds.kawoosh(gate);

        final ArgumentCaptor<Float> volume = ArgumentCaptor.forClass(Float.class);
        final ArgumentCaptor<Float> pitch = ArgumentCaptor.forClass(Float.class);
        verify(world).playSound(any(Location.class), anyString(), eq(SoundCategory.BLOCKS), volume.capture(),
            pitch.capture());
        assertEquals(ConfigManager.getGateSoundVolume() * GateSounds.BIG_VOLUME_LIMIT, volume.getValue(), EPSILON);
        assertEquals(GateSounds.KAWOOSH_PITCH * GateSounds.BIG_PITCH_LIMIT, pitch.getValue(), EPSILON);
    }
}
