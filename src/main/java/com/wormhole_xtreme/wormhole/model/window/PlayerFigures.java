package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * What a far player's stand-in is, and how it is dressed (#296, step 2): a Mannequin wearing their
 * skin where the server has one (1.21.9 on), an armour stand wearing their head before it.
 *
 * <p>The plugin compiles against 1.20, so the Mannequin and every call on it are reached by name.
 * Spigot and Paper name them differently: Spigot takes a {@code PlayerProfile} and hides the label
 * with {@code setHideDescription}, Paper takes a {@code ResolvableProfile} and hides it with a null
 * description. Whichever this server has is used; one it has neither of is left as it comes.
 */
final class PlayerFigures
{
    /** The Mannequin's class name, which a server before 1.21.9 does not have. */
    static final String MANNEQUIN = "org.bukkit.entity.Mannequin";

    /** How many follows pass between looks at what a player wears and holds: once a second. */
    static final int WORN_EVERY = 10;

    /** The poses a mannequin is given; a player in any other shows standing. */
    private static final Set<Pose> HELD_POSES = Set.of(Pose.STANDING, Pose.SNEAKING, Pose.SWIMMING, Pose.SLEEPING,
        Pose.FALL_FLYING);

    /** Makes a player's head to wear; replaceable for a test, which has no item factory. */
    static Function<Player, ItemStack> heads = PlayerFigures::headOf;

    /** The server's Mannequin and its methods, or whatever a test installed; null where there is none. */
    private static Mannequins mannequins = Mannequins.of(classNamed(MANNEQUIN));

    /** What a player's stand-in last showed, so it is changed only when the player has. */
    static final class Look
    {
        Pose pose;
        ItemStack[] worn;
        int sinceWorn;

        /** What a player shows now, as their stand-in was just dressed. */
        static Look of(final Player player)
        {
            final Look look = new Look();
            look.pose = figurePose(player.getPose());
            look.worn = wornBy(player);
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
        /** Spigot's {@code setModelPartShown}, and the player's own {@code isModelPartShown}. */
        final Method modelPart;
        final Method partShown;

        private Mannequins(final Class<? extends LivingEntity> type)
        {
            this.type = type;
            playerProfile = method(type, "setPlayerProfile", 1);
            profile = (playerProfile == null) ? method(type, "setProfile", 1) : null;
            resolve = (profile == null) ? null : factory(profile.getParameterTypes()[0], "resolvableProfile");
            // Spigot's Mannequin declares setPose(Pose); Paper's inherits one from Entity that its own ticking may undo.
            pose = Arrays.stream(type.getDeclaredMethods())
                .filter(found -> "setPose".equals(found.getName()) && (found.getParameterCount() == 1))
                .findFirst().orElse(null);
            fixedPose = (pose == null) ? method(type, "setPose", 2) : null;
            immovable = method(type, "setImmovable", 1);
            hideDescription = method(type, "setHideDescription", 1);
            description = (hideDescription == null) ? method(type, "setDescription", 1) : null;
            mainHand = method(type, "setMainHand", 1);
            modelPart = method(type, "setModelPartShown", 2);
            partShown = method(Player.class, "isModelPartShown", 1);
        }

        /** The methods of a Mannequin type, or null for no type or one that is not a living entity. */
        static Mannequins of(final Class<?> type)
        {
            return ((type != null) && LivingEntity.class.isAssignableFrom(type))
                ? new Mannequins(type.asSubclass(LivingEntity.class)) : null;
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
        mannequins = Mannequins.of(classNamed(MANNEQUIN));
    }

    /**
     * Makes a player's stand-in look like them: their name above it always, and on a Mannequin their
     * skin, pose and main hand with its label hidden, or on an armour stand their head, arms and no
     * base plate. What they wear and hold is already on it.
     *
     * @param copy
     *            the stand-in, not yet in the world
     * @param original
     *            the player
     */
    static void dress(final Entity copy, final Player original)
    {
        copy.setCustomName(original.getName());
        copy.setCustomNameVisible(true);
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
     * Keeps a player's stand-in in step as it follows them: the pose whenever it changes, and what they
     * wear and hold once every {@link #WORN_EVERY} follows, only when that has changed.
     *
     * @param copy
     *            the stand-in
     * @param original
     *            the player
     * @param look
     *            what it shows now, updated
     */
    static void keepInStep(final Entity copy, final Player original, final Look look)
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
        look.sinceWorn++;
        if (look.sinceWorn >= WORN_EVERY)
        {
            look.sinceWorn = 0;
            final ItemStack[] worn = wornBy(original);
            if (!Arrays.equals(worn, look.worn))
            {
                look.worn = worn;
                putOn(copy, worn);
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

    /**
     * What a player wears and holds.
     *
     * @param original
     *            the player
     * @return helmet, chestplate, leggings, boots, main hand and off hand; empty where there is no equipment
     */
    static ItemStack[] wornBy(final LivingEntity original)
    {
        final EntityEquipment from = original.getEquipment();
        if (from == null)
        {
            return new ItemStack[0];
        }
        return new ItemStack[] { from.getHelmet(), from.getChestplate(), from.getLeggings(), from.getBoots(),
            from.getItemInMainHand(), from.getItemInOffHand() };
    }

    /** Puts on a stand-in what its player now wears and holds; an armour stand keeps the head it wears. */
    private static void putOn(final Entity copy, final ItemStack[] worn)
    {
        final EntityEquipment to = (copy instanceof LivingEntity living) ? living.getEquipment() : null;
        if ((to == null) || (worn.length < 6))
        {
            return;
        }
        if (!(copy instanceof ArmorStand))
        {
            to.setHelmet(worn[0]);
        }
        to.setChestplate(worn[1]);
        to.setLeggings(worn[2]);
        to.setBoots(worn[3]);
        to.setItemInMainHand(worn[4]);
        to.setItemInOffHand(worn[5]);
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
        final ItemStack head = headFor(original);
        if ((worn != null) && (head != null))
        {
            worn.setHelmet(head);
        }
    }

    /** The player's head, or null if it could not be made: the stand-in is shown without it. */
    private static ItemStack headFor(final Player original)
    {
        try
        {
            return heads.apply(original);
        }
        catch (final RuntimeException | LinkageError noHead)
        {
            return null;
        }
    }

    /** A player head wearing this player's skin, which an online player's profile carries. */
    private static ItemStack headOf(final Player player)
    {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull)
        {
            skull.setOwnerProfile(player.getPlayerProfile());
            head.setItemMeta(skull);
        }
        return head;
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
        if (with.playerProfile != null)
        {
            call(with.playerProfile, copy, original.getPlayerProfile());
        }
        else if ((with.profile != null) && (with.resolve != null))
        {
            call(with.profile, copy, answer(with.resolve, null, original.getPlayerProfile()));
        }
        call(with.mainHand, copy, original.getMainHand());
        pose(with, copy, figurePose(original.getPose()));
        skinLayers(with, copy, original);
    }

    /** Shows the skin's layers (hat, jacket, sleeves, trousers, cape) as the player shows them, where the server can say. */
    private static void skinLayers(final Mannequins with, final Entity copy, final Player original)
    {
        if ((with.modelPart == null) || (with.partShown == null))
        {
            return;
        }
        final Object[] parts = with.modelPart.getParameterTypes()[0].getEnumConstants();
        if (parts != null)
        {
            Arrays.stream(parts).forEach(part ->
            {
                final Object shown = answer(with.partShown, original, part);
                if (shown instanceof Boolean)
                {
                    call(with.modelPart, copy, part, shown);
                }
            });
        }
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

    /** A public method by name and number of parameters, or null where there is none. */
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

    /** A class by name, or null where this server has none. */
    private static Class<?> classNamed(final String name)
    {
        try
        {
            return Class.forName(name);
        }
        catch (final ClassNotFoundException | LinkageError absent)
        {
            return null;
        }
    }
}
