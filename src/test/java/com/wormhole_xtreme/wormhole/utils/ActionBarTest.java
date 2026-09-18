package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * A server that cannot show an action bar costs the player the line, never the ring trip or the
 * mirror that asked for it.
 */
class ActionBarTest
{
    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        ActionBar.forgetUnavailable();
        PluginTestSupport.remove();
    }

    @Test
    void theLineGoesAboveTheHotbar()
    {
        final Player player = mock(Player.class);
        final Player.Spigot hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);

        ActionBar.send(player, "Rings in 3");

        verify(hotbar).sendMessage(eq(ChatMessageType.ACTION_BAR),
            argThat((final BaseComponent line) -> "Rings in 3".equals(((TextComponent) line).getText())));
    }

    @Test
    void somebodyWhoHasLoggedOutIsToldNothing()
    {
        assertDoesNotThrow(() -> ActionBar.send(null, "Rings in 3"));
    }

    @Test
    void aSpigotThatRefusesTheMessageIsIgnored()
    {
        final Player player = mock(Player.class);
        when(player.spigot()).thenThrow(new IllegalStateException("refused"));

        assertDoesNotThrow(() -> ActionBar.send(player, "Rings in 3"));
    }

    /**
     * The same failure on the class the plugin really uses: said once, and nobody asked again.
     */
    @Test
    void aMissingSpigotMethodIsLoggedOnceAndNotTriedAgain() throws ReflectiveOperationException
    {
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
     * CraftBukkit has neither {@code Player.spigot()} nor BungeeCord's chat classes. Hiding
     * {@code net.md_5} from a fresh copy of the class stands in for it: the call fails to link,
     * which a {@code RuntimeException} catch does not stop, and before the fix every ring
     * countdown and mirror approach on such a server threw. Said once, not on every ring tick.
     */
    @Test
    void aServerWithoutBungeeChatIsToldOnceAndNothingBreaks() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Method send = new NoBungeeChat().loadClass(ActionBar.class.getName())
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
            send.invoke(null, player, "Rings in 3");
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }
    }

    /** Loads its own copy of {@link ActionBar}, on a classpath with no BungeeCord chat. */
    private static final class NoBungeeChat extends ClassLoader
    {
        NoBungeeChat()
        {
            super(ActionBarTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException
        {
            if (name.startsWith("net.md_5."))
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
