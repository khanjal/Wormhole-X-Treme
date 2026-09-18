package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

/**
 * A server that cannot show an action bar costs the player the line, never the ring trip or the
 * mirror that asked for it.
 */
class ActionBarTest
{
    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        PluginTestSupport.remove();
    }

    @Test
    void aSpigotThatRefusesTheMessageIsIgnored()
    {
        final Player player = mock(Player.class);
        when(player.spigot()).thenThrow(new IllegalStateException("refused"));

        assertDoesNotThrow(() -> ActionBar.send(player, "Rings in 3"));
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
                final Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : define(name);
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
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class"))
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
