package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * What a far player's stand-in is, and how it is dressed (#296, step 2): a Mannequin wearing their
 * skin where the server has one (1.21.9 on), an armour stand wearing their head before it.
 *
 * <p>The plugin compiles against 1.20, so the Mannequin and every call on it are reached by name.
 * Spigot and Paper name them differently: Spigot takes a {@code PlayerProfile} and hides the label
 * with {@code setHideDescription}, Paper takes a {@code ResolvableProfile} and hides it with a null
 * description. Whichever this server has is used; one it has neither of is left as it comes.
 *
 * <p>The skin is the player's own: a disguise plugin's look is not copied.
 */
final class PlayerFigures
{
    /** The Mannequin's class name, which a server before 1.21.9 does not have. */
    static final String MANNEQUIN = "org.bukkit.entity.Mannequin";

    /** How long between looks at what a player wears and holds, however often the stand-in is placed. */
    static final long WORN_MILLIS = 1000L;

    /** The poses a mannequin is given; a player in any other shows standing. */
    private static final Set<Pose> HELD_POSES = Set.of(Pose.STANDING, Pose.SNEAKING, Pose.SWIMMING, Pose.SLEEPING,
        Pose.FALL_FLYING);

    /** The server's Mannequin and its methods, or whatever a test installed; null where there is none. */
    private static Mannequins mannequins = serversOwn();

    /** What a player's stand-in last showed, so it is changed only when the player has. */
    static final class Look
    {
        Pose pose;
        VisibleItems.Visible[] worn;
        long wornAt;

        /**
         * What a player shows now, as their stand-in is dressed.
         *
         * @param player
         *            the player
         * @param now
         *            the drawing's clock
         * @return their look
         */
        static Look of(final Player player, final long now)
        {
            final Look look = new Look();
            look.pose = figurePose(player.getPose());
            look.worn = VisibleItems.wornBy(player);
            look.wornAt = now;
            return look;
        }
    }

    /** A Mannequin type and the methods found on it, any of which is null on a server without it. */
    static final class Mannequins
    {
        final Class<? extends LivingEntity> type;
        /** Spigot's {@code setPlayerProfile(PlayerProfile)}. */
        final Method playerProfile;
        /** Paper's {@code setProfile(ResolvableProfile)}, and the factory making one from a player's profile. */
        final Method profile;
        final Method resolve;
        /** Spigot's {@code setPose(Pose)}, declared on the Mannequin; else Paper's {@code setPose(Pose, boolean)}, which keeps it. */
        final Method pose;
        final Method fixedPose;
        final Method immovable;
        /** Spigot's {@code setHideDescription(boolean)}; Paper's {@code setDescription(Component)}, where null hides it. */
        final Method hideDescription;
        final Method description;
        final Method mainHand;
        /** Spigot's {@code setModelPartShown(PlayerModelPart, boolean)}. */
        final Method modelPart;

        private Mannequins(final Class<? extends LivingEntity> type)
        {
            this.type = type;
            playerProfile = exactly(type, "setPlayerProfile", PlayerProfile.class);
            profile = (playerProfile == null) ? method(type, "setProfile", 1) : null;
            resolve = (profile == null) ? null : factory(profile.getParameterTypes()[0], "resolvableProfile");
            // Spigot's Mannequin declares setPose(Pose); Paper's inherits one from Entity that its own ticking may undo.
            pose = Arrays.stream(type.getDeclaredMethods())
                .filter(found -> "setPose".equals(found.getName())
                    && Arrays.equals(found.getParameterTypes(), new Class<?>[] { Pose.class }))
                .findFirst().orElse(null);
            fixedPose = (pose == null) ? exactly(type, "setPose", Pose.class, boolean.class) : null;
            immovable = exactly(type, "setImmovable", boolean.class);
            hideDescription = exactly(type, "setHideDescription", boolean.class);
            description = (hideDescription == null) ? method(type, "setDescription", 1) : null;
            mainHand = exactly(type, "setMainHand", MainHand.class);
            final Method part = method(type, "setModelPartShown", 2);
            modelPart = ((part != null) && part.getParameterTypes()[0].isEnum()
                && (part.getParameterTypes()[1] == boolean.class)) ? part : null;
        }

        /** The methods of a Mannequin type, or null for no type or one that is not a living entity. */
        static Mannequins of(final Class<?> type)
        {
            return ((type != null) && LivingEntity.class.isAssignableFrom(type))
                ? new Mannequins(type.asSubclass(LivingEntity.class)) : null;
        }

        /** A public method by name and number of parameters, for a type this API cannot name; null where there is none. */
        private static Method method(final Class<?> on, final String name, final int parameters)
        {
            return Arrays.stream(on.getMethods())
                .filter(found -> found.getName().equals(name) && (found.getParameterCount() == parameters))
                .findFirst().orElse(null);
        }

        /** A static one-parameter factory by name, or null where there is none. */
        private static Method factory(final Class<?> on, final String name)
        {
            return Arrays.stream(on.getMethods())
                .filter(found -> found.getName().equals(name) && (found.getParameterCount() == 1)
                    && Modifier.isStatic(found.getModifiers()))
                .findFirst().orElse(null);
        }
    }

    /** Static state only. */
    private PlayerFigures()
    {
    }

    /** @return what a player's stand-in is spawned as: the Mannequin where the server has one, else an armour stand */
    static Class<? extends LivingEntity> kind()
    {
        return (mannequins != null) ? mannequins.type : ArmorStand.class;
    }

    /**
     * Uses this type as the Mannequin, for a test: the API on the compile path is older than it.
     *
     * @param type
     *            a living entity type with the Mannequin's methods, or null for a server without one
     */
    static void mannequinWith(final Class<?> type)
    {
        mannequins = Mannequins.of(type);
    }

    /** @return the Mannequin's methods as found, for a test; null where there is no Mannequin */
    static Mannequins mannequins()
    {
        return mannequins;
    }

    /** Goes back to the server's own Mannequin, after a test. */
    static void mannequinFromServer()
    {
        mannequins = serversOwn();
    }

    /**
     * Makes a player's stand-in look like them: on a Mannequin their skin, pose and main hand with its
     * label hidden, on an armour stand their head, arms and no base plate; and their name above it,
     * as the viewer would see it over the player. What they wear and hold is already on it.
     *
     * @param copy
     *            the stand-in, not yet in the world
     * @param original
     *            the player
     * @param viewer
     *            who is shown it; null to name it regardless
     */
    static void dress(final Entity copy, final Player original, final Player viewer)
    {
        if (nameTagShown(viewer, original))
        {
            copy.setCustomName(shownName(original));
            copy.setCustomNameVisible(true);
        }
        if (copy instanceof ArmorStand stand)
        {
            standUp(stand, original);
        }
        else if (isMannequin(copy))
        {
            dressMannequin(mannequins, copy, original);
        }
    }

    /**
     * The name shown over a player's stand-in: their display name without its colours, so a nickname
     * shows as the nickname; their name where that is empty.
     *
     * @param player
     *            the player
     * @return the name to show
     */
    static String shownName(final Player player)
    {
        final String plain = ChatColor.stripColor(player.getDisplayName()).trim();
        return plain.isEmpty() ? player.getName() : plain;
    }

    /**
     * Whether a viewer would see this player's name tag, by the player's team on the viewer's
     * scoreboard: hidden where the team hides it from everyone, or from this viewer's side.
     *
     * @param viewer
     *            who looks; null for no one in particular
     * @param player
     *            whose name it is
     * @return true if the stand-in may carry the name
     */
    static boolean nameTagShown(final Player viewer, final Player player)
    {
        if (viewer == null)
        {
            return true;
        }
        try
        {
            final Scoreboard board = viewer.getScoreboard();
            final Team team = board.getEntryTeam(player.getName());
            if (team == null)
            {
                return true;
            }
            final Team.OptionStatus status = team.getOption(Team.Option.NAME_TAG_VISIBILITY);
            if (status == Team.OptionStatus.NEVER)
            {
                return false;
            }
            if ((status == Team.OptionStatus.FOR_OWN_TEAM) || (status == Team.OptionStatus.FOR_OTHER_TEAMS))
            {
                final boolean sameTeam = team.equals(board.getEntryTeam(viewer.getName()));
                return (status == Team.OptionStatus.FOR_OWN_TEAM) == sameTeam;
            }
            return true;
        }
        catch (final RuntimeException | LinkageError unknown)
        {
            // Left nameless rather than named against a rule that could not be read.
            return false;
        }
    }

    /**
     * Keeps a player's stand-in in step as it is placed: the pose whenever it changes, and what they
     * wear and hold once every {@link #WORN_MILLIS}, put on only when what shows of it has changed.
     *
     * @param copy
     *            the stand-in
     * @param original
     *            the player
     * @param look
     *            what it shows now, updated
     * @param now
     *            the drawing's clock
     */
    static void keepInStep(final Entity copy, final Player original, final Look look, final long now)
    {
        final Pose pose = figurePose(original.getPose());
        if (pose != look.pose)
        {
            look.pose = pose;
            if (isMannequin(copy))
            {
                pose(mannequins, copy, pose);
            }
        }
        if ((now - look.wornAt) >= WORN_MILLIS)
        {
            look.wornAt = now;
            final VisibleItems.Visible[] worn = VisibleItems.wornBy(original);
            if (!Arrays.equals(worn, look.worn))
            {
                look.worn = worn;
                if (copy instanceof LivingEntity living)
                {
                    VisibleItems.putOn(living, worn, copy instanceof ArmorStand);
                }
            }
        }
    }

    /**
     * The pose a player's stand-in takes.
     *
     * @param pose
     *            the player's
     * @return standing, sneaking, swimming, sleeping or gliding as they are; standing for anything else
     */
    static Pose figurePose(final Pose pose)
    {
        return ((pose != null) && HELD_POSES.contains(pose)) ? pose : Pose.STANDING;
    }

    /** An armour stand standing as a person: seen, with arms and no base plate, full size, solid, wearing the head. */
    private static void standUp(final ArmorStand stand, final Player original)
    {
        stand.setVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setSmall(false);
        stand.setMarker(false);
        final EntityEquipment worn = stand.getEquipment();
        final ItemStack head = VisibleItems.itemFor(
            new VisibleItems.Visible(Material.PLAYER_HEAD, false, null, null, original.getPlayerProfile()));
        if ((worn != null) && (head != null))
        {
            worn.setHelmet(head);
        }
    }

    /** Whether a stand-in is this server's Mannequin. */
    private static boolean isMannequin(final Entity copy)
    {
        return (mannequins != null) && mannequins.type.isInstance(copy);
    }

    /** Dresses a Mannequin as the player, by whichever of the methods this server has. */
    private static void dressMannequin(final Mannequins with, final Entity copy, final Player original)
    {
        call(with.immovable, copy, true);
        if (with.hideDescription != null)
        {
            call(with.hideDescription, copy, true);
        }
        else
        {
            call(with.description, copy, (Object) null);
        }
        // The skin alone, on a profile of its own, never the player's name and id; none leaves the default skin.
        final PlayerProfile skin = VisibleItems.skinOnly(original.getPlayerProfile());
        if (skin != null)
        {
            if (with.playerProfile != null)
            {
                call(with.playerProfile, copy, skin);
            }
            else if ((with.profile != null) && (with.resolve != null))
            {
                call(with.profile, copy, answer(with.resolve, null, skin));
            }
        }
        call(with.mainHand, copy, original.getMainHand());
        pose(with, copy, figurePose(original.getPose()));
        skinLayers(with, copy, original);
    }

    /**
     * Shows the skin's layers (hat, jacket, sleeves, trousers, cape) as the player shows them, where
     * the server can say: Spigot's player answers {@code isModelPartShown}, which is looked up on the
     * player itself.
     */
    private static void skinLayers(final Mannequins with, final Entity copy, final Player original)
    {
        if (with.modelPart == null)
        {
            return;
        }
        final Class<?> partType = with.modelPart.getParameterTypes()[0];
        final Method shown = exactly(original.getClass(), "isModelPartShown", partType);
        if ((shown == null) || (shown.getReturnType() != boolean.class))
        {
            return;
        }
        Arrays.stream(partType.getEnumConstants()).forEach(part ->
        {
            final Object on = answer(shown, original, part);
            if (on instanceof Boolean)
            {
                call(with.modelPart, copy, part, on);
            }
        });
    }

    /** Sets a Mannequin's pose, kept where the server can keep it. */
    private static void pose(final Mannequins with, final Entity copy, final Pose pose)
    {
        if (with.fixedPose != null)
        {
            call(with.fixedPose, copy, pose, true);
        }
        else
        {
            call(with.pose, copy, pose);
        }
    }

    /** Calls a method by reflection, if the server has it; a failure is logged once and leaves the look as it was. */
    private static void call(final Method method, final Object target, final Object... arguments)
    {
        if (method != null)
        {
            answer(method, target, arguments);
        }
    }

    /** The same, for what it answers; null on a failure. */
    private static Object answer(final Method method, final Object target, final Object... arguments)
    {
        try
        {
            return method.invoke(target, arguments);
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError failed)
        {
            StandIns.failedOnce("Could not dress a far player's stand-in", failed);
            return null;
        }
    }

    /** A public method by name and exact parameter types, or null where there is none. */
    private static Method exactly(final Class<?> on, final String name, final Class<?>... parameters)
    {
        try
        {
            return on.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }

    /** The server's own Mannequin, or null where it has none or its methods cannot be read. */
    private static Mannequins serversOwn()
    {
        try
        {
            return Mannequins.of(Class.forName(MANNEQUIN));
        }
        catch (final ClassNotFoundException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }
}
