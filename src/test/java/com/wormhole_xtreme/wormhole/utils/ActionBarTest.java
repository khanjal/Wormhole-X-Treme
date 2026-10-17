package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * A server that cannot show an action bar costs the player the line, never the ring trip or the
 * mirror that asked for it.
 */
class ActionBarTest
{
    private static final String LINE = "§3:: §7Rings in 3";

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        ActionBar.forgetUnavailable();
        PluginTestSupport.remove();
    }

    /** Paper's Player is an Audience, so on Paper's API the line never reaches Spigot's bar. */
    private static void assumeSpigot()
    {
        assumeFalse(ActionBarLines.PAPER, "Paper sends Adventure, not BungeeCord chat");
    }

    /** @return a player that is an Adventure audience, as every player on Paper is */
    private static Player paperPlayer()
    {
        return ActionBarLines.PAPER ? mock(Player.class)
            : mock(Player.class, withSettings().extraInterfaces(Audience.class));
    }

    /** A player who is not an Audience, as on Spigot, gets Spigot's bar. */
    @Test
    void theLineGoesAboveTheHotbar()
    {
        assumeSpigot();
        final Player player = mock(Player.class);
        final Player.Spigot hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);

        ActionBar.send(player, "Rings in 3");

        verify(hotbar).sendMessage(eq(ChatMessageType.ACTION_BAR),
            argThat((final BaseComponent line) -> "Rings in 3".equals(((TextComponent) line).getText())));
    }

    /**
     * Paper deprecates Spigot's action bar, so a Paper player gets Adventure's, colours intact, and
     * Spigot's is not touched.
     */
    @Test
    void aPaperPlayerGetsAdventuresActionBar()
    {
        final Player player = paperPlayer();

        ActionBar.send(player, LINE);

        final ArgumentCaptor<Component> said = ArgumentCaptor.forClass(Component.class);
        verify((Audience) player).sendActionBar(said.capture());
        assertEquals(LegacyComponentSerializer.legacySection().deserialize(LINE), said.getValue(),
            "the line's colour codes read as colours, not sent as text");
        verify(player, never()).spigot();
    }

    /** One refusal from Paper costs that line, not a second try through Spigot's bar. */
    @Test
    void aPaperThatRefusesTheMessageIsIgnored()
    {
        final Player player = paperPlayer();
        doThrow(new IllegalStateException("refused")).when((Audience) player).sendActionBar(any(Component.class));

        assertDoesNotThrow(() -> ActionBar.send(player, LINE));
        verify(player, never()).spigot();
    }

    /**
     * Spigot has no Adventure. Hiding {@code net.kyori} from a fresh copy of the class stands in
     * for it: the Paper bar fails to link, and the line still goes out through Spigot's, every
     * time, with nothing logged as missing.
     */
    @Test
    void aServerWithoutAdventureStillGetsSpigotsBar() throws Throwable
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Method send = new Without("net.kyori.").loadClass(ActionBar.class.getName())
            .getMethod("send", Player.class, String.class);
        final Player player = paperPlayer();
        final Player.Spigot hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);

        invoke(send, player);
        invoke(send, player);

        verify(hotbar, times(2)).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent.class));
        verify((Audience) player, never()).sendActionBar(any(Component.class));
        verify(plugin, never()).prettyLog(any(Level.class), any(String.class));
    }

    /** An Adventure that fails to link on the first line is not tried on the next: Spigot's bar carries both. */
    @Test
    void aPaperBarThatCannotLinkFallsBackToSpigotAndIsNotTriedAgain()
    {
        final Player player = paperPlayer();
        final Player.Spigot hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);
        doThrow(new NoClassDefFoundError("net/kyori/adventure/text/Component")).when((Audience) player)
            .sendActionBar(any(Component.class));

        ActionBar.send(player, LINE);
        ActionBar.send(player, LINE);

        verify((Audience) player, times(1)).sendActionBar(any(Component.class));
        verify(hotbar, times(2)).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent.class));
    }

    @Test
    void somebodyWhoHasLoggedOutIsToldNothing()
    {
        assertDoesNotThrow(() -> ActionBar.send(null, "Rings in 3"));
    }

    @Test
    void aSpigotThatRefusesTheMessageIsIgnored()
    {
        assumeSpigot();
        final Player player = mock(Player.class);
        when(player.spigot()).thenThrow(new IllegalStateException("refused"));

        assertDoesNotThrow(() -> ActionBar.send(player, "Rings in 3"));
        verify(player).spigot();
    }

    /**
     * The same failure on the class the plugin really uses: said once, and nobody asked again.
     */
    @Test
    void aMissingSpigotMethodIsLoggedOnceAndNotTriedAgain() throws ReflectiveOperationException
    {
        assumeSpigot();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Player first = mock(Player.class);
        when(first.spigot()).thenThrow(new NoSuchMethodError("Player.spigot()"));
        final Player second = mock(Player.class);

        assertDoesNotThrow(() -> ActionBar.send(first, "Rings in 3"));
        assertDoesNotThrow(() -> ActionBar.send(second, "Rings in 2"));

        verify(plugin, times(1)).prettyLog(eq(Level.INFO), contains("no Spigot action bar"));
        verify(second, never()).spigot();
    }

    /**
     * CraftBukkit has neither Adventure, {@code Player.spigot()} nor BungeeCord's chat classes.
     * Hiding {@code net.kyori} and {@code net.md_5} from a fresh copy of the class stands in for
     * it: the call fails to link, which a {@code RuntimeException} catch does not stop, and before
     * the fix every ring countdown and mirror approach on such a server threw. Said once, not on
     * every ring tick.
     */
    @Test
    void aServerWithoutBungeeChatIsToldOnceAndNothingBreaks() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Method send = new Without("net.md_5.", "net.kyori.").loadClass(ActionBar.class.getName())
            .getMethod("send", Player.class, String.class);
        final Player player = mock(Player.class);

        assertDoesNotThrow(() -> invoke(send, player));
        assertDoesNotThrow(() -> invoke(send, player));
        verify(plugin, times(1)).prettyLog(eq(Level.INFO), contains("no Spigot action bar"));
    }

    /**
     * Calls a static method, unwrapping what it threw so the assertion sees the real error.
     *
     * @param send
     *            {@code ActionBar.send} from the isolated copy
     * @param player
     *            who to tell
     * @throws Throwable
     *             whatever {@code send} threw
     */
    private static void invoke(final Method send, final Player player) throws Throwable
    {
        try
        {
            send.invoke(null, player, LINE);
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }
    }

    /** Loads its own copy of {@link ActionBar}, on a classpath without the packages named. */
    private static final class Without extends ClassLoader
    {
        private final List<String> hidden;

        /**
         * @param hidden
         *            package prefixes, each ending in a dot
         */
        Without(final String... hidden)
        {
            super(ActionBarTest.class.getClassLoader());
            this.hidden = List.of(hidden);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException
        {
            if (hidden.stream().anyMatch(name::startsWith))
            {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(ActionBar.class.getName()))
            {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name))
            {
                final Class<?> found = findLoadedClass(name);
                final Class<?> loaded = found != null ? found : define(name);
                if (resolve)
                {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        /**
         * @param name
         *            {@code ActionBar} or one of its nested classes
         * @return that class, defined here rather than by the parent
         * @throws ClassNotFoundException
         *             if its bytes cannot be read
         */
        private Class<?> define(final String name) throws ClassNotFoundException
        {
            try (final InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class"))
            {
                if (in == null)
                {
                    throw new ClassNotFoundException(name);
                }
                final byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            catch (final IOException unreadable)
            {
                throw new ClassNotFoundException(name, unreadable);
            }
        }
    }
}
