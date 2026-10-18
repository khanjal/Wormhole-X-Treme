package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorPackets;
import com.wormhole_xtreme.wormhole.utils.EntityNames;
import com.wormhole_xtreme.wormhole.utils.PlayerLocales;
import com.wormhole_xtreme.wormhole.utils.PlayerNames;
import com.wormhole_xtreme.wormhole.utils.PluginVersion;
import com.wormhole_xtreme.wormhole.utils.ShownNames;
import com.wormhole_xtreme.wormhole.utils.SignLines;
import com.wormhole_xtreme.wormhole.utils.SignText;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * The Paper methods the plugin reaches reflectively are really there, under the names and types looked up.
 *
 * <p>Only runs in the CI job built against paper-api. A misspelt lookup fails quietly on a real
 * server, so nothing else would notice.
 */
@EnabledIfSystemProperty(named = "server.api", matches = "paper")
class PaperApiTest
{
    /** Paper loads the chunks held for a view's creatures off the main thread, asking for each held chunk not to be generated. */
    @Test
    void theChunkHoldsFindPapersAsyncChunkLoad() throws NoSuchMethodException
    {
        assertTrue(FarChunkHolds.loadsAsync(), "held chunks would load on the main thread on Paper");
        assertEquals(CompletableFuture.class,
            World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class).getReturnType());
    }

    /** Where Paper has a Mannequin (1.21.9 on), a far player's stand-in finds Paper's own names for its skin, label and pose. */
    @Test
    void aPlayersMannequinFindsPapersNames()
    {
        PlayerFigures.mannequinFromServer();
        final PlayerFigures.Mannequins found = PlayerFigures.mannequins();
        if (found != null)
        {
            assertNotNull(found.profile, "setProfile(ResolvableProfile)");
            assertNotNull(found.resolve, "ResolvableProfile.resolvableProfile(PlayerProfile)");
            assertNotNull(found.description, "setDescription(Component)");
            assertNotNull(found.fixedPose, "Entity.setPose(Pose, boolean)");
        }
    }

    /** The banner, log tag and update check read the version from Paper's plugin meta, not the description it deprecates. */
    @Test
    void thePluginVersionComesFromPapersPluginMeta() throws ReflectiveOperationException
    {
        final Plugin plugin = mock(Plugin.class);
        final Method getMeta = Plugin.class.getMethod("getPluginMeta");
        final Object meta = mock(getMeta.getReturnType());
        when(getMeta.invoke(plugin)).thenReturn(meta);
        when(getMeta.getReturnType().getMethod("getVersion").invoke(meta)).thenReturn("9.9.9");

        assertEquals("9.9.9", PluginVersion.of(plugin), "the version would be read from the deprecated description");
    }

    /** Freya and the stand-ins are named through Paper's {@code customName}, not the string setter it deprecates. */
    @Test
    void anEntityIsNamedThroughPapersComponentName() throws ReflectiveOperationException
    {
        final Entity named = mock(Entity.class);
        final Entity original = mock(Entity.class);
        final Entity copy = mock(Entity.class);
        ShownNames.give(original, "§cBob");

        EntityNames.set(named, "§bFreya");
        EntityNames.copy(original, copy);

        assertEquals("§bFreya", ShownNames.of(named));
        assertEquals("§cBob", ShownNames.of(copy));
        for (final Entity entity : List.of(named, copy))
        {
            assertTrue(mockingDetails(entity).getInvocations().stream()
                .noneMatch(call -> "setCustomName".equals(call.getMethod().getName())),
                "the name would go through the deprecated string setter on Paper");
        }
    }

    /** A far player's stand-in shows their display name read through Paper's {@code displayName()}, not the string getter it deprecates. */
    @Test
    void aPlayersDisplayNameIsReadThroughPapersComponent() throws ReflectiveOperationException
    {
        final Player player = mock(Player.class);
        when(Player.class.getMethod("displayName").invoke(player))
            .thenReturn(LegacyComponentSerializer.legacySection().deserialize("§aSir Nick"));

        assertEquals("§aSir Nick", PlayerNames.display(player));
        assertTrue(mockingDetails(player).getInvocations().stream()
            .noneMatch(call -> "getDisplayName".equals(call.getMethod().getName())),
            "the display name would be read through the deprecated string getter on Paper");
    }

    /** A player's client language is read through Paper's {@code locale()}, not the getLocale it deprecates (#26). */
    @Test
    void aPlayersLocaleIsReadThroughPapersLocale() throws ReflectiveOperationException
    {
        final Player player = mock(Player.class);
        when(Player.class.getMethod("locale").invoke(player)).thenReturn(Locale.GERMANY);

        assertEquals("de_DE", PlayerLocales.of(player));
        assertTrue(mockingDetails(player).getInvocations().stream()
            .noneMatch(call -> "getLocale".equals(call.getMethod().getName())),
            "the locale would be read through the deprecated string getter on Paper");
    }

    /** Gate and dial signs are written and read through Paper's {@code line} and {@code lines}, not the string methods it deprecates. */
    @Test
    void aSignIsWrittenAndReadThroughPapersComponentLines() throws ReflectiveOperationException
    {
        final SignSide side = mock(SignSide.class);
        final String[] held = { "§3-Alpha-", "Beta", "", "" };
        SignLines.hold(side, held);
        when(SignSide.class.getMethod("line", int.class).invoke(side, 0))
            .thenReturn(LegacyComponentSerializer.legacySection().deserialize("§3-Alpha-"));

        SignText.set(side, 2, "§2Beta");

        assertEquals("§2Beta", SignLines.writtenTo(side)[2]);
        assertEquals("§3-Alpha-", SignText.get(side, 0));
        assertArrayEquals(held, SignText.lines(side));
        assertTrue(mockingDetails(side).getInvocations().stream()
            .noneMatch(call -> call.getMethod().getName().matches("setLine|getLine|getLines")),
            "the sign would go through the deprecated string methods on Paper");
    }

    /** Not on 1.20, which has no such method and where a window rightly goes without. */
    @Test
    @DisabledIfSystemProperty(named = "paper.api.version", matches = "1\\.20-.*")
    void aWindowFindsSendBlockUpdate()
    {
        assertTrue(MirrorPackets.available(), "a window's banner would stay in front of its view on Paper");
    }
}
