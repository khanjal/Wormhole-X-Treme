package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Stream;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.SignStyle;

/**
 * Why {@code /wormhole config <setting> <value>} refuses a value, read by key from {@code lang/} (#26).
 *
 * <p>Each refusal goes through the real command twice: to an English admin, against the
 * concatenation {@code ParsedSetting} built before the move, copied from it, so a reworded
 * template, a swapped argument or a list joined differently shows as a changed line; and to an
 * admin whose client reads the test language qq, so a refusal built in the server's language
 * instead of the reader's shows as English. The setting's name and what was typed stay as they
 * are in both, and so do the words a setting takes.
 */
class SettingRefusalMessagesTest
{
    private static final String LOUD = "loud";

    private static final String BANANA = "banana";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** One kind of refusal: the setting, what is typed, the old English, and the qq. */
    record Refusal(ConfigKeys key, String typed, String english, String qq)
    {
        @Override
        public String toString()
        {
            return key.name();
        }
    }

    static Stream<Refusal> refusals()
    {
        final List<String> colours = SignStyle.colorNames();
        final List<String> animations = ConfigManager.irisAnimations();
        return Stream.of(
            refusal(ConfigKeys.GATE_SOUNDS_ENABLED, LOUD, "GATE_SOUNDS_ENABLED is true or false, not \"loud\".",
                "QQ GATE_SOUNDS_ENABLED true/false not \"loud\"."),
            refusal(ConfigKeys.RING_REACH, "far", "RING_REACH is a number, not \"far\".",
                "QQ RING_REACH number not \"far\"."),
            refusal(ConfigKeys.GATE_SOUND_VOLUME, LOUD, "GATE_SOUND_VOLUME is a number, not \"loud\".",
                "QQ GATE_SOUND_VOLUME number not \"loud\"."),
            refusal(ConfigKeys.GATE_DIAL_SPIN, BANANA, ConfigKeys.GATE_DIAL_SPIN
                + " is CHEVRON, TOP, LAP, FILL, PEGASUS, CHASE, UNIVERSE, OVERSHOOT or NONE, not \"banana\".",
                "QQ GATE_DIAL_SPIN spin CHEVRON TOP LAP FILL PEGASUS CHASE UNIVERSE OVERSHOOT NONE not \"banana\"."),
            refusal(ConfigKeys.GATE_IRIS_ANIMATION, "sideways", ConfigKeys.GATE_IRIS_ANIMATION + " is "
                + String.join(", ", animations) + ", not \"sideways\".",
                "QQ GATE_IRIS_ANIMATION one of " + String.join(" | ", animations) + " not \"sideways\"."),
            refusal(ConfigKeys.GATE_VIEW, "window", ConfigKeys.GATE_VIEW + " is "
                + String.join(", ", ConfigManager.GATE_VIEWS) + ", not \"window\".",
                "QQ GATE_VIEW one of horizon | behind | open not \"window\"."),
            refusal(ConfigKeys.SIGN_COLOR_NETWORK, "PINK", ConfigKeys.SIGN_COLOR_NETWORK + " is "
                + String.join(", ", colours) + ", not \"PINK\".",
                "QQ SIGN_COLOR_NETWORK one of " + String.join(" | ", colours) + " not \"PINK\"."),
            refusal(ConfigKeys.LOG_LEVEL, LOUD, ConfigKeys.LOG_LEVEL + " is SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, "
                + "ALL or OFF, not \"" + LOUD + "\".",
                "QQ LOG_LEVEL level SEVERE WARNING INFO CONFIG FINE FINER FINEST ALL OFF not \"loud\"."),
            refusal(ConfigKeys.SERVER_LANGUAGE, "en gb",
                ConfigKeys.SERVER_LANGUAGE + " is a language code such as en, de or pt_br, not \"" + "en gb" + "\".",
                "QQ SERVER_LANGUAGE language en de pt_br not \"en gb\"."),
            refusal(ConfigKeys.RING_DEFAULT_ACCESS, "secret", ConfigKeys.RING_DEFAULT_ACCESS
                + " is PUBLIC or PRIVATE, not \"" + "secret" + "\".",
                "QQ RING_DEFAULT_ACCESS PUBLIC/PRIVATE not \"secret\"."),
            refusal(ConfigKeys.RING_DEFAULT_STYLE, BANANA, ConfigKeys.RING_DEFAULT_STYLE
                + " is CONCURRENT or SEQUENTIAL, not \"" + "banana" + "\". Fast and slow work too.",
                "QQ RING_DEFAULT_STYLE style CONCURRENT/SEQUENTIAL not \"banana\" Fast/slow."),
            refusal(ConfigKeys.RING_DEFAULT_LIGHT, BANANA, ConfigKeys.RING_DEFAULT_LIGHT
                + " is the name of a block, not \"" + "banana" + "\".",
                "QQ RING_DEFAULT_LIGHT block not \"banana\"."));
    }

    private static Refusal refusal(final ConfigKeys key, final String typed, final String english, final String qq)
    {
        return new Refusal(key, typed, english, qq);
    }

    private static void set(final Player player, final ConfigKeys key, final String typed)
    {
        final String[] words = typed.split(" ");
        final String[] args = new String[words.length + 2];
        args[0] = "config";
        args[1] = key.name();
        System.arraycopy(words, 0, args, 2, words.length);
        new ConfigCommand().execute(player, args);
    }

    /** An English admin reads each refusal as the code built it before the move. */
    @ParameterizedTest
    @MethodSource("refusals")
    void eachRefusalReadsAsItDidBefore(final Refusal refusal)
    {
        final Player player = reading("en");
        set(player, refusal.key(), refusal.typed());

        verify(player).sendMessage(ChatText.error(refusal.english()));
    }

    /** A qq admin reads each refusal in qq, the setting, the words it takes and what was typed unchanged. */
    @ParameterizedTest
    @MethodSource("refusals")
    void eachRefusalIsInTheReadersLanguage(final Refusal refusal)
    {
        final Player player = reading("qq");
        set(player, refusal.key(), refusal.typed());

        verify(player).sendMessage(ChatText.error(refusal.qq()));
    }

    /**
     * {@code applySetting} refuses in the reader's language too, though the command asks
     * {@code settingRefusal} first and never reaches it with a refused value.
     */
    @Test
    void applySettingRefusesInTheReadersLanguage()
    {
        assertEquals("QQ GATE_SOUNDS_ENABLED true/false not \"loud\".",
            ConfigManager.applySetting(reading("qq"), "gate-sounds-enabled", LOUD));
        assertEquals("GATE_SOUNDS_ENABLED is true or false, not \"loud\".",
            ConfigManager.applySetting(reading("en"), "gate-sounds-enabled", LOUD));
    }
}
