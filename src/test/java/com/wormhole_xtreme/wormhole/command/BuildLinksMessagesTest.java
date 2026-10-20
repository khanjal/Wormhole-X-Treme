package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.bad;
import static com.wormhole_xtreme.wormhole.utils.ChatText.command;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.name;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.utils.ChatLine;
import com.wormhole_xtreme.wormhole.utils.ChatText;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * The clickable lines of {@code gate build} and {@code gate preview} keep every link, hover and click as
 * they were, with the words around them read by key (#26).
 *
 * <p>Each line is what the command sends a builder, as the components the server sends, compared with
 * the same line built here the way the code built it before the move, from {@link ChatLine} alone: lead
 * sentence, the command words a click runs or fills in, and the tail, each part in the colour it had.
 * The command stays the words a player types, in either language, and the hover on each button keeps the
 * command on its first line and what it does on the next.
 */
class BuildLinksMessagesTest extends BuildPreviewCase
{
    /** On Paper's API a Player is an Audience, and a line goes as Adventure components. */
    private static final boolean PAPER = Audience.class.isAssignableFrom(Player.class);

    /** What each button says and, as it was written, what pointing at it does. */
    private static final Map<String, String> HELP = Map.ofEntries(
        Map.entry("clear", "Takes the preview you look at away."),
        Map.entry("activate", "Dials it, or shuts it down."),
        Map.entry("iris", "Closes its iris, or opens it."),
        Map.entry("material", "Dresses it in another group, or changes one of its blocks."),
        Map.entry("needs", "Lists what it takes to build, and what is still to place."),
        Map.entry("guide", "Marks what is still to place and what is wrong, or stops."),
        Map.entry("layer", "Shows it a layer more at a time, then all of it again."),
        Map.entry("chevrons", "Draws its chevrons as frame, or in their own block again."),
        Map.entry("dhd", "Hides its DHD, or shows it."),
        Map.entry("share", "Says who else sees it, and how to show it to more."),
        Map.entry("place", "Builds it for real."));

    @BeforeEach
    void linksOn()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.CLICKABLE_CHAT, true);
    }

    private static Player sink()
    {
        final Player player = mock(Player.class);
        final Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        return player;
    }

    /** The one clickable line a player was sent, as Adventure's component or BungeeCord's. */
    private static Object sentLine(final Player player)
    {
        if (PAPER)
        {
            final ArgumentCaptor<Component> lines = ArgumentCaptor.forClass(Component.class);
            verify((Audience) player, atLeastOnce()).sendMessage(lines.capture());
            return lines.getValue();
        }
        final ArgumentCaptor<BaseComponent> lines = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player.spigot(), atLeastOnce()).sendMessage(lines.capture());
        return lines.getValue();
    }

    /** What the line would send, built the old way. */
    private static Object sentAs(final ChatLine line)
    {
        final Player sink = sink();
        line.send(sink);
        return comparable(sentLine(sink));
    }

    /** The line as equal lines are equal: Adventure's component, or BungeeCord's as the JSON it sends. */
    private static Object comparable(final Object line)
    {
        return PAPER ? line : ComponentSerializer.toString((BaseComponent) line);
    }

    /** A reader of {@code locale} whose previews are stubbed by {@code stubs}, running {@code args}, and the line sent. */
    private Object run(final String locale, final Consumer<MockedStatic<GatePreviews>> stubs, final String... args)
    {
        final Player player = reading(locale);
        final Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            stubs.accept(previews);
            run(player, args);
        }
        return comparable(sentLine(player));
    }

    /** The buttons, each a link with its command, its hover and what it fills in, as they were. */
    private static ChatLine oldButtons(final ChatLine line, final String finish, final String helpPrefix)
    {
        final String[][] labels = {{"clear", "Clear"}, {"activate", "Activate"}, {"iris", "Iris"},
            {"material", "Material"}, {"needs", "Needs"}, {"guide", "Guide"}, {"layer", "Layer"},
            {"chevrons", "Chevrons"}, {"dhd", "DHD"}, {"share", "Share"}, {"place", "Place"}};
        for (int i = 0; i < labels.length; i++)
        {
            final String action = labels[i][0];
            final boolean needsMore = "material".equals(action);
            final String typed = PREVIEW + action + (needsMore ? " " : "");
            final String help = (helpPrefix == null) ? HELP.get(action) : helpPrefix + action + ".";
            if (i > 0)
            {
                line.text(" ");
            }
            line.link(command("[" + labels[i][1] + "]"), command(action), typed,
                command(typed.trim() + (needsMore ? " ..." : "")) + "\n" + help + (needsMore ? finish : ""), needsMore);
        }
        return line;
    }

    private static ChatLine oldActionList(final String before, final String finish, final String helpPrefix)
    {
        return oldButtons(ChatLine.of(before).text(hint("<action>") + ": "), finish, helpPrefix);
    }

    /** The preview options moved, the unknown action and the usage, each ending in the buttons. */
    @Test
    void theActionListsKeepEveryButtonInEnglish()
    {
        final String finish = " Click to finish it in chat.";
        assertEquals(sentAs(oldActionList(error("Preview options moved to "), finish, null)),
            run("en", p -> { }, "gate", "build", "-guide"));
        assertEquals(sentAs(oldButtons(ChatLine.of(error("No preview action " + name("bogus") + ". Try ")), finish, null)),
            run("en", p -> { }, "gate", "preview", "bogus"));
        assertEquals(sentAs(oldActionList(info("Usage: "), finish, null)), run("en", p -> { }, "gate", "preview"));
    }

    /** The same lists for a qq builder: the lead and what each button says in qq, the commands as typed. */
    @Test
    void theActionListsKeepEveryButtonInTheReadersLanguage()
    {
        final String finish = " QQ finish.";
        assertEquals(sentAs(oldActionList(error("QQ moved "), finish, "QQ help ")),
            run("qq", p -> { }, "gate", "build", "-guide"));
        assertEquals(sentAs(oldButtons(ChatLine.of(error("QQ no action " + name("bogus") + " ")), finish, "QQ help ")),
            run("qq", p -> { }, "gate", "preview", "bogus"));
        assertEquals(sentAs(oldActionList(info("Usage: "), finish, "QQ help ")), run("qq", p -> { }, "gate", "preview"));
    }

    private static void shown(final MockedStatic<GatePreviews> previews)
    {
        previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);
    }

    /** The line after a shown preview, which is its action list. */
    @Test
    void theLineAfterAShownPreviewIsTheActionListInBothLanguages()
    {
        assertEquals(sentAs(oldActionList(info("Look at it and use "), " Click to finish it in chat.", null)),
            run("en", BuildLinksMessagesTest::shown, "gate", "build", "Standard"));
        assertEquals(sentAs(oldActionList(info("QQ look "), " QQ finish.", "QQ help ")),
            run("qq", BuildLinksMessagesTest::shown, "gate", "build", "Standard"));
    }

    private static void tooMany(final MockedStatic<GatePreviews> previews)
    {
        previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.OVER_LIMIT);
        ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5);
    }

    /** The refusals that name a command: a click runs it, and the full stop after it is plain. */
    @Test
    void theRefusalsThatNameACommandLinkItInEnglish()
    {
        assertEquals(sentAs(ChatLine.of(error("Too many preview blocks on the server. Clear one with "))
            .run(hint("clear"), PREVIEW + "clear").text(".")),
            run("en", BuildLinksMessagesTest::tooMany, "gate", "build", "Standard"));
        assertEquals(sentAs(ChatLine.of(error("Look at a preview to clear it, or use "))
            .run(hint("clear -all"), PREVIEW + "clear -all").text(".")),
            run("en", p -> { }, "gate", "preview", "clear"));
        assertEquals(sentAs(ChatLine.of(error("Placed, but no gate was found in it. "))
            .run(hint("guide"), PREVIEW + "guide").text(" shows what is wrong.")),
            run("en", p -> placeAnswers(p, GatePreviews.Outcome.NOT_FOUND), "gate", "preview", "place"));
        assertEquals(sentAs(ChatLine.of(error("Nothing placed. In the way: " + bad("stone, dirt") + ". "))
            .run(hint("guide"), PREVIEW + "guide").text(" marks them.")),
            run("en", p -> placeAnswers(p, GatePreviews.Outcome.IN_THE_WAY, "stone", "dirt"), "gate", "preview", "place"));
    }

    /** The same refusals for a qq builder: the words around the link in qq, the link and its hover as before. */
    @Test
    void theRefusalsThatNameACommandLinkItInTheReadersLanguage()
    {
        assertEquals(sentAs(ChatLine.of(error("QQ too many ")).run(hint("clear"), PREVIEW + "clear").text(".")),
            run("qq", BuildLinksMessagesTest::tooMany, "gate", "build", "Standard"));
        assertEquals(sentAs(ChatLine.of(error("QQ look to clear ")).run(hint("clear -all"), PREVIEW + "clear -all").text(".")),
            run("qq", p -> { }, "gate", "preview", "clear"));
        assertEquals(sentAs(ChatLine.of(error("QQ not found ")).run(hint("guide"), PREVIEW + "guide").text(" QQ wrong.")),
            run("qq", p -> placeAnswers(p, GatePreviews.Outcome.NOT_FOUND), "gate", "preview", "place"));
        assertEquals(sentAs(ChatLine.of(error("QQ in the way " + bad("stone | dirt") + " "))
            .run(hint("guide"), PREVIEW + "guide").text(" QQ marks.")),
            run("qq", p -> placeAnswers(p, GatePreviews.Outcome.IN_THE_WAY, "stone", "dirt"), "gate", "preview", "place"));
    }

    private static void placeAnswers(final MockedStatic<GatePreviews> previews, final GatePreviews.Outcome outcome,
        final String... inTheWay)
    {
        previews.when(() -> GatePreviews.place(any(), anyBoolean()))
            .thenReturn(new GatePreviews.Placed(outcome, List.of(inTheWay), null, null));
    }

    /** What {@code material} takes: both forms fill the chat box in rather than run, in either language. */
    @Test
    void theMaterialUsageSuggestsItsTwoFormsInBothLanguages()
    {
        final String roles = Arrays.stream(GateBlueprint.Role.values())
            .map(GateBlueprint.Role::option).map(ChatText::command)
            .collect(Collectors.joining(", "));
        final String qqRoles = String.join(" | ", Arrays.stream(GateBlueprint.Role.values())
            .map(GateBlueprint.Role::option).map(ChatText::command).toList());
        assertEquals(sentAs(ChatLine.of(info("Usage: ")).suggest(hint("material <group>"), PREVIEW + "material ")
            .text(" or ").suggest(hint("material -<role> <block>"), PREVIEW + "material -").text(". Roles: " + roles + ".")),
            run("en", p -> { }, "gate", "preview", "material", "sparkly"));
        assertEquals(sentAs(ChatLine.of(info("Usage: ")).suggest(hint("material <group>"), PREVIEW + "material ")
            .text(" QQor ").suggest(hint("material -<role> <block>"), PREVIEW + "material -")
            .text(" QQroles " + qqRoles + ".")),
            run("qq", p -> { }, "gate", "preview", "material", "sparkly"));
    }
}
