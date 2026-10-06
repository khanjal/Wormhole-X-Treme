package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * A command named in chat can be clicked where the server can send a link, and reads exactly as
 * it always did where it cannot (#538).
 *
 * <p>The line in these tests is a refusal that names one command to run and one to finish
 * typing, as {@code gate preview}'s do.
 */
class ChatLineTest
{
    private static final String CLEAR = "/wormhole gate preview clear -all";

    private static final String MATERIAL = "/wormhole gate preview material ";

    /** What the line read as before links existed, and still reads as without them. */
    private static final String PLAIN = "§3:: §7Use " + ChatText.command(CLEAR) + " or "
        + ChatText.command(MATERIAL + "<group>") + ".";

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        ChatLine.forgetUnavailable();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Paper's Player is an Audience, so on Paper's API a line never reaches Spigot's chat. */
    static void assumeSpigot()
    {
        assumeFalse(Audience.class.isAssignableFrom(Player.class), "Paper sends Adventure, not BungeeCord chat");
    }

    private static ChatLine line()
    {
        return ChatLine.of("§3:: §7Use ").run(ChatText.command(CLEAR), CLEAR).text(" or ")
            .suggest(ChatText.command(MATERIAL + "<group>"), MATERIAL).text(".");
    }

    @Test
    void thePlainLineSpellsEveryCommandOutAsItAlwaysRead()
    {
        assertEquals(PLAIN, line().plain());
    }

    /** A server that turned {@code clickable-chat} off gets the line as it always read. */
    @Test
    void withClickableChatOffTheLineIsSentAsPlainText()
    {
        ConfigTestSupport.set(ConfigKeys.CLICKABLE_CHAT, false);
        final Player player = mock(Player.class);

        line().send(player);

        verify(player).sendMessage(PLAIN);
        verify(player, never()).spigot();
    }

    /**
     * On Spigot each command is a link: one runs, one fills the chat box, and the words around
     * them are not links at all.
     */
    @Test
    void onSpigotOneCommandRunsAndTheOtherIsFilledIn()
    {
        assumeSpigot();
        final Player player = mock(Player.class);
        final Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        line().send(player);

        final ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(spigot).sendMessage(sent.capture());
        verify(player, never()).sendMessage(anyString());
        final List<BaseComponent> bits = sent.getValue().getExtra();
        assertEquals(5, bits.size(), "opening, a link, \" or \", a link, the full stop");
        assertNull(bits.get(0).getClickEvent(), "the opening words are not a link");
        assertEquals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, CLEAR), bits.get(1).getClickEvent());
        assertEquals(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, MATERIAL), bits.get(3).getClickEvent(),
            "a command that needs more typed after it fills the chat box in rather than running half of it");
        final Text hover = assertInstanceOf(Text.class, bits.get(1).getHoverEvent().getContents().get(0));
        assertEquals("Click to run " + ChatText.command(CLEAR), hover.getValue());
    }

    /**
     * A colour code does not run on from one component into the next, so each piece opens in the
     * colour the last left off in: the full stop after a white command is grey, as in the plain
     * line, not the client's default white.
     */
    @Test
    void theWordsAfterACommandKeepTheBodyColour()
    {
        assumeSpigot();
        final Player player = mock(Player.class);
        final Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        line().send(player);

        final ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(spigot).sendMessage(sent.capture());
        final List<BaseComponent> bits = sent.getValue().getExtra();
        assertEquals(ChatText.BODY_COLOUR + ".", ((TextComponent) bits.get(4)).getText());
        assertEquals(ChatText.BODY_COLOUR + ChatText.command(CLEAR), ((TextComponent) bits.get(1)).getText());
    }

    /** On Paper the line goes as Adventure components, and Spigot's deprecated chat is not touched. */
    @Test
    void onPaperTheLineIsSentAsAdventureComponents()
    {
        final Player player = Audience.class.isAssignableFrom(Player.class) ? mock(Player.class)
            : mock(Player.class, withSettings().extraInterfaces(Audience.class));

        line().send(player);

        final ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify((Audience) player).sendMessage(sent.capture());
        verify(player, never()).spigot();
        verify(player, never()).sendMessage(anyString());
        final List<Component> bits = sent.getValue().children();
        assertEquals(5, bits.size(), "opening, a link, \" or \", a link, the full stop");
        assertEquals(net.kyori.adventure.text.event.ClickEvent.runCommand(CLEAR), bits.get(1).clickEvent());
        assertEquals(net.kyori.adventure.text.event.ClickEvent.suggestCommand(MATERIAL), bits.get(3).clickEvent());
        assertNull(bits.get(2).clickEvent(), "the words between commands are not a link");
    }

    /** A Spigot that refuses the components still gets the words to the player. */
    @Test
    void aSpigotThatRefusesTheLineGetsItPlain()
    {
        assumeSpigot();
        final Player player = mock(Player.class);
        when(player.spigot()).thenThrow(new IllegalStateException("refused"));

        line().send(player);

        verify(player).sendMessage(PLAIN);
    }

    /**
     * CraftBukkit has neither Adventure nor BungeeCord's chat. Hiding both from a fresh copy of
     * the class stands in for it: each fails to link inside the guard, the player gets the
     * command spelled out, and the server is told once rather than on every line.
     */
    @Test
    void aServerWithNeitherChatApiGetsThePlainLineAndIsToldOnce() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Class<?> isolated = new NoChatApi().loadClass(ChatLine.class.getName());
        final Method of = isolated.getMethod("of", String.class);
        final Method run = isolated.getMethod("run", String.class, String.class);
        final Method send = isolated.getMethod("send", Player.class);
        final Player player = mock(Player.class);

        for (int i = 0; i < 2; i++)
        {
            final Object chat = run.invoke(of.invoke(null, "Use "), ChatText.command(CLEAR), CLEAR);
            assertDoesNotThrow(() -> invoke(send, chat, player));
        }

        verify(player, times(2)).sendMessage("Use " + ChatText.command(CLEAR));
        verify(plugin, times(1)).prettyLog(eq(Level.INFO), contains("neither Paper's nor Spigot's chat"));
    }

    /**
     * Calls a method, unwrapping what it threw so the test sees the real error.
     *
     * @throws Throwable
     *             whatever the method threw
     */
    private static void invoke(final Method method, final Object target, final Object argument) throws Throwable
    {
        try
        {
            method.invoke(target, argument);
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }
    }

    /** Loads its own copy of {@link ChatLine}, on a classpath with no Adventure and no BungeeCord chat. */
    private static final class NoChatApi extends ClassLoader
    {
        NoChatApi()
        {
            super(ChatLineTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException
        {
            if (name.startsWith("net.md_5.") || name.startsWith("net.kyori."))
            {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(ChatLine.class.getName()))
            {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name))
            {
                final Class<?> found = findLoadedClass(name);
                final Class<?> loaded = (found != null) ? found : define(name);
                if (resolve)
                {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

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
