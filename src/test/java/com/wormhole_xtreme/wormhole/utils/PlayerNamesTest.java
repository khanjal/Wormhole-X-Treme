package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * A player's display name, the name over their stand-in in a window, is read through Paper's
 * component getter where the server has one and Bukkit's string getter otherwise, in legacy colour
 * codes either way.
 *
 * <p>Spigot's API, which most legs compile against, has no {@code displayName()}, so these tests
 * install a stand-in type declaring Paper's, found by reflection exactly as the server's is.
 */
class PlayerNamesTest
{
    /** Paper's getter, on a player type that has it on every API build; public, for the isolated copy to call. */
    public interface PaperDisplayed extends Player
    {
        Component displayName();
    }

    private PaperDisplayed player;

    @BeforeEach
    void setUp() throws NoSuchMethodException
    {
        player = mock(PaperDisplayed.class);
        bukkitName(player, "§cBukkit's");
        PlayerNames.lookupWith(new PlayerNames.Lookup(PaperDisplayed.class.getMethod("displayName")));
    }

    @AfterEach
    void tearDown()
    {
        PlayerNames.forget();
    }

    // The fallback's own getter, which Paper's API deprecates.
    @SuppressWarnings("deprecation")
    private static void bukkitName(final Player player, final String legacyText)
    {
        when(player.getDisplayName()).thenReturn(legacyText);
    }

    /** Paper deprecates the string getter, so a nickname plugin's colours are read from the component. */
    @Test
    void aPaperServerGivesAColouredNicknameFromItsComponent()
    {
        when(player.displayName()).thenReturn(Component.text("Sir Nick", NamedTextColor.GREEN));

        assertEquals("§aSir Nick", PlayerNames.display(player));
    }

    /** Bukkit writes a hex colour as six codes after an x; plain legacySection() would round it to the nearest named one. */
    @Test
    void aHexColouredNicknameKeepsItsHexColour()
    {
        when(player.displayName()).thenReturn(Component.text("Nick", TextColor.color(0x123456)));

        assertEquals("§x§1§2§3§4§5§6Nick", PlayerNames.display(player));
    }

    /** An empty display name is read as empty, for the caller to replace with the account name, not as Bukkit's. */
    @Test
    void anEmptyNicknameIsReadAsEmpty()
    {
        when(player.displayName()).thenReturn(Component.empty());

        assertEquals("", PlayerNames.display(player));
        verify(player, never()).getDisplayName();
    }

    @Test
    void aServerWithoutComponentNamesGivesTheString()
    {
        PlayerNames.lookupWith(null);

        assertEquals("§cBukkit's", PlayerNames.display(player));
    }

    /** A Paper that refuses the component still gives the name, the older way. */
    @Test
    void aRefusedComponentFallsBackToTheString()
    {
        when(player.displayName()).thenThrow(new IllegalStateException("refused"));

        assertEquals("§cBukkit's", PlayerNames.display(player));
    }

    /** A server's player always has a component, but a mock stubbed only the Bukkit way answers null, which Adventure refuses. */
    @Test
    void aMissingComponentFallsBackToTheString()
    {
        assertEquals("§cBukkit's", PlayerNames.display(player));
    }

    /**
     * Spigot and CraftBukkit may have no Adventure at all. Hiding {@code net.kyori} from a fresh copy
     * of the class stands in for that: even given a getter to call, it fails to link its answer, and
     * the name is still read.
     */
    @Test
    void aServerWithoutAdventureGivesTheString() throws Throwable
    {
        final IsolatedCopy copy = new IsolatedCopy(PlayerNames.class, "net.kyori.");
        final Class<?> names = copy.loadClass(PlayerNames.class.getName());
        final Class<?> lookup = copy.loadClass(PlayerNames.Lookup.class.getName());
        final Method lookupWith = names.getDeclaredMethod("lookupWith", lookup);
        final Constructor<?> made = lookup.getDeclaredConstructor(Method.class);
        final Method display = names.getMethod("display", Player.class);
        lookupWith.setAccessible(true);
        made.setAccessible(true);
        when(player.displayName()).thenReturn(Component.text("Sir Nick"));

        try
        {
            lookupWith.invoke(null, made.newInstance(PaperDisplayed.class.getMethod("displayName")));
            assertEquals("§cBukkit's", display.invoke(null, player));
            verify(player).displayName();
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }
    }
}
