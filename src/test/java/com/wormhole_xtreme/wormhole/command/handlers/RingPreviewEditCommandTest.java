package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingPreview;
import com.wormhole_xtreme.wormhole.model.ring.RingPreviews;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * An edit redrawing a standing ring preview, and trying a value before saving it (#332, step 2).
 *
 * <p>What the viewer's client is showing is read back from the block changes sent to them,
 * position by position, with each material's block data tagged so a slab swap or a new light is
 * seen as what it is. Three players stand about: the owner, who edits and tries, a second
 * viewer of the same end, and a third at the other end, so "every viewer of that end, and not
 * the other end" is checked on the real send path.
 */
class RingPreviewEditCommandTest
{
    private static final String WORLD = "world";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final long TIMEOUT = 20L * 60L * 5L;
    private static final String PREVIEW = "preview";
    private static final String LIGHT = "light";
    private static final String BRICK_SLAB = "brick_slab";
    private static final String SEA_LANTERN = "sea_lantern";
    private static final String IRON_BLOCK = "iron_block";

    private final HeldScheduler held = new HeldScheduler();
    private final Map<BlockData, Material> tags = new IdentityHashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<RingYamlManager> yaml;
    private World world;
    private BlockData real;
    private Player owner;
    private Player second;
    private Player third;
    private RingPair pair;
    private Ring endA;
    private Ring endB;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();
        RingManager.clear();
        RingPreviews.clearAll();

        real = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        final Map<Material, BlockData> made = new EnumMap<>(Material.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call ->
        {
            final Material material = call.getArgument(0, Material.class);
            return made.computeIfAbsent(material, key ->
            {
                final BlockData data = mock(BlockData.class);
                tags.put(data, key);
                return data;
            });
        });
        yaml = mockStatic(RingYamlManager.class);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(3));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(2));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.GOLD_BLOCK);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.CONCURRENT);
        config.when(ConfigManager::getRingSoundRing).thenReturn("");
        config.when(ConfigManager::getRingSoundOpen).thenReturn("");
        config.when(ConfigManager::getRingSoundFlash).thenReturn("");

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(Boolean.valueOf(call.getArgument(1, Integer.class) >= 64));
            when(block.getBlockData()).thenReturn(real);
            return block;
        });

        owner = player(UUID.fromString(OWNER), false, 0, 0);
        second = player(UUID.randomUUID(), true, 0, 0);
        third = player(UUID.randomUUID(), true, 200, 200);

        endA = ring(0, 0);
        endB = ring(200, 200);
        pair = new RingPair("aaaa0001", WORLD, endA, endB);
        pair.setOwner(OWNER);
        pair.setOwnerName("Justin");
        RingManager.addPair(pair, 4);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RingPreviews.clearAll();
        RingManager.clear();
        config.close();
        yaml.close();
        bukkit.close();
        held.remove();
        PluginTestSupport.remove();
    }

    private Player player(final UUID id, final boolean admin, final int x, final int z)
    {
        final Player made = mock(Player.class);
        when(made.getWorld()).thenReturn(world);
        when(made.getUniqueId()).thenReturn(id);
        when(made.getName()).thenReturn("P" + id.toString().substring(0, 4));
        when(made.isOnline()).thenReturn(Boolean.TRUE);
        when(made.hasPermission(anyString())).thenReturn(false);
        when(made.hasPermission("wormhole.ring.admin")).thenReturn(admin);
        when(made.getLocation()).thenReturn(new Location(world, x, 65, z));
        return made;
    }

    private static Ring ring(final int x, final int z)
    {
        final Ring made = new Ring(x, 64, z, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        made.setFlashMaterial(Material.GOLD_BLOCK);
        made.setStyle(RingStyle.CONCURRENT);
        return made;
    }

    private static void run(final Player who, final String... words)
    {
        final String[] args = new String[words.length + 1];
        args[0] = "ring";
        System.arraycopy(words, 0, args, 1, words.length);
        new RingCommand().execute(who, args);
    }

    private boolean moving()
    {
        for (final Player who : new Player[] { owner, second, third })
        {
            final RingPreview shown = RingPreviews.of(who);
            if ((shown != null) && ((shown.getState() == RingPreview.State.DEPLOYING)
                || (shown.getState() == RingPreview.State.LIGHTING)
                || (shown.getState() == RingPreview.State.COLLAPSING)))
            {
                return true;
            }
        }
        return false;
    }

    /** Lets every animation finish, leaving the timeouts alone. */
    private void settle()
    {
        for (int guard = 0; moving() && (guard < 1000); guard++)
        {
            held.runNextOtherThan(TIMEOUT);
        }
        assertFalse(moving());
    }

    /** What the viewer's client shows after the sends so far, by position. */
    private Map<String, Material> screen(final Player who)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(who, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final Map<String, Material> shown = new HashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            shown.put(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ(),
                tags.get(what.getAllValues().get(i)));
        }
        return shown;
    }

    /** Whether a material was sent to the viewer at any point, even if something went over it. */
    private boolean everSent(final Player who, final Material material)
    {
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(who, atLeastOnce()).sendBlockChange(any(Location.class), what.capture());
        return what.getAllValues().stream().anyMatch(data -> tags.get(data) == material);
    }

    private boolean shows(final Player who, final Material material)
    {
        return screen(who).containsValue(material);
    }

    /** Both of the first end's viewers and the other end's viewer have the stack up. */
    private void everyoneDeploys()
    {
        run(owner, PREVIEW, "deploy");
        run(second, PREVIEW, "deploy");
        run(third, PREVIEW, "deploy");
        settle();
        assertTrue(shows(owner, Material.STONE_SLAB));
        assertTrue(shows(third, Material.STONE_SLAB));
    }

    /** A new slab swaps into every viewer's standing stack, and the other end's viewer is not sent a thing. */
    @Test
    void editingTheRingSlabRedrawsEveryViewerOfThatEndAndNobodyElse()
    {
        everyoneDeploys();
        clearInvocations(third);

        run(owner, "edit", "ring", BRICK_SLAB);

        assertTrue(shows(owner, Material.BRICK_SLAB));
        assertTrue(shows(second, Material.BRICK_SLAB), "a second viewer of the end, not only the editor");
        assertFalse(shows(second, Material.STONE_SLAB), "no old slab left standing");
        verify(third, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** Naming the pair edits both ends, so both ends' viewers are redrawn. */
    @Test
    void editingAPairByIdRedrawsTheViewersOfBothEnds()
    {
        everyoneDeploys();

        run(owner, "edit", "aaaa0001", "ring", BRICK_SLAB);

        assertTrue(shows(second, Material.BRICK_SLAB));
        assertTrue(shows(third, Material.BRICK_SLAB));
    }

    /** Putting a ring back to normal is an edit too, so a standing stack goes back to the slab it was laid in. */
    @Test
    void resetRedrawsAStandingStack()
    {
        endA.setBuiltMaterial(Material.STONE_SLAB);
        endA.setRingMaterial(Material.BRICK_SLAB);
        run(owner, PREVIEW, "deploy");
        settle();
        assertTrue(shows(owner, Material.BRICK_SLAB));

        run(owner, "edit", "reset");

        assertTrue(shows(owner, Material.STONE_SLAB));
        assertFalse(shows(owner, Material.BRICK_SLAB));
    }

    /** A new pad light shows on the pad straight away. */
    @Test
    void editingTheLightShowsOnThePad()
    {
        run(owner, PREVIEW);
        run(second, PREVIEW);
        assertTrue(shows(owner, Material.GLOWSTONE));

        run(owner, "edit", "light", SEA_LANTERN);

        assertTrue(shows(owner, Material.SEA_LANTERN));
        assertTrue(shows(second, Material.SEA_LANTERN));
    }

    /** A new flash is not drawn now; the next run of the light uses it. */
    @Test
    void editingTheFlashIsUsedByTheNextLightAndNotDrawnNow()
    {
        everyoneDeploys();
        clearInvocations(owner, second);

        run(owner, "edit", "flash", IRON_BLOCK);

        verify(owner, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        run(owner, PREVIEW, LIGHT);
        run(second, PREVIEW, LIGHT);
        settle();
        assertTrue(everSent(owner, Material.IRON_BLOCK));
        assertTrue(everSent(second, Material.IRON_BLOCK));
        assertFalse(everSent(owner, Material.GOLD_BLOCK), "the old flash is not used");
    }

    /** A new style leaves the standing stack alone and shapes the next deploy. */
    @Test
    void editingTheStyleWaitsForTheNextDeploy()
    {
        run(owner, PREVIEW, "deploy");
        final int fast = stepsToStand(owner);
        run(owner, PREVIEW, "collapse");
        settle();
        run(owner, PREVIEW, "deploy");
        settle();
        clearInvocations(owner);

        run(owner, "edit", "style", "slow");

        verify(owner, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        run(owner, PREVIEW, "collapse");
        settle();
        run(owner, PREVIEW, "deploy");
        assertNotEquals(fast, stepsToStand(owner), "the next deploy runs in the new style");
    }

    private int stepsToStand(final Player who)
    {
        int steps = 0;
        while ((RingPreviews.of(who).getState() != RingPreview.State.DEPLOYED) && (steps < 1000))
        {
            held.runNextOtherThan(TIMEOUT);
            steps++;
        }
        return steps;
    }

    /** A trial slab is drawn for its player alone and the stored ring keeps its slab. */
    @Test
    void aTrialSlabIsDrawnForOnePlayerAndNotStored()
    {
        run(second, PREVIEW, "deploy");
        settle();

        run(owner, PREVIEW, "ring", BRICK_SLAB);
        run(owner, PREVIEW, "deploy");
        settle();

        assertTrue(shows(owner, Material.BRICK_SLAB));
        assertFalse(shows(second, Material.BRICK_SLAB), "nobody else's drawing changed");
        assertEquals(Material.STONE_SLAB, endA.getRingMaterial());
        verify(owner).sendMessage(plainContains("nothing is stored"));
    }

    /** Trying a slab on a stack that already stands swaps it at once. */
    @Test
    void aTrialSlabSwapsIntoAStandingStack()
    {
        run(owner, PREVIEW, "deploy");
        settle();

        run(owner, PREVIEW, "ring", BRICK_SLAB);

        assertTrue(shows(owner, Material.BRICK_SLAB));
        assertEquals(Material.STONE_SLAB, endA.getRingMaterial());
    }

    /** A trial light lights the pad, and is not stored. */
    @Test
    void aTrialLightShowsOnThePadAndIsNotStored()
    {
        run(owner, PREVIEW, LIGHT, SEA_LANTERN);

        assertTrue(shows(owner, Material.SEA_LANTERN), "the preview started and lit the pad in the trial light");
        assertEquals(Material.GLOWSTONE, endA.getLightMaterial());
    }

    /** A trial flash is what the next light runs in, and is not stored. */
    @Test
    void aTrialFlashIsUsedByLightAndNotStored()
    {
        run(owner, PREVIEW, "flash", IRON_BLOCK);
        run(owner, PREVIEW, "deploy");
        settle();
        run(owner, PREVIEW, LIGHT);
        settle();

        assertTrue(everSent(owner, Material.IRON_BLOCK));
        assertFalse(everSent(owner, Material.GOLD_BLOCK));
        assertEquals(Material.GOLD_BLOCK, endA.getFlashMaterial());
    }

    /** A trial style shapes the next deploy for its player, and is not stored. */
    @Test
    void aTrialStyleShapesTheDeployAndIsNotStored()
    {
        run(owner, PREVIEW, "deploy");
        final int fast = stepsToStand(owner);
        run(owner, PREVIEW, "collapse");
        settle();

        run(owner, PREVIEW, "style", "slow");
        run(owner, PREVIEW, "deploy");

        assertNotEquals(fast, stepsToStand(owner));
        assertEquals(RingStyle.CONCURRENT, endA.getStyle());
    }

    /** A value that edit refuses is refused with edit's message, and nothing is drawn or kept. */
    @Test
    void aValueEditWouldRefuseIsRefusedWithEditsMessage()
    {
        run(owner, PREVIEW, "ring", "stone");
        run(owner, PREVIEW, LIGHT, "not_a_block");
        run(owner, PREVIEW, "flash", "not_a_block");
        run(owner, PREVIEW, "style", "sideways");

        verify(owner).sendMessage(plainContains("has to be a slab"));
        verify(owner, times(2)).sendMessage(plainContains("not_a_block is not a block"));
        verify(owner).sendMessage(plainContains("Style is"));
        assertNull(RingPreviews.of(owner), "a refused value does not even light the pad");
        verify(owner, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** A field with no value is a usage error rather than a trial, and plain light still runs the light. */
    @Test
    void aFieldWithNoValueShowsTheUsageLine()
    {
        run(owner, PREVIEW, "ring");

        verify(owner).sendMessage(plainContains("/wormhole ring preview"));
        assertNull(RingPreviews.of(owner));
    }

    /** Somebody who may not manage the pair cannot try a value on it. */
    @Test
    void aStrangerCannotTryAValue()
    {
        pair.setOwner(UUID.randomUUID().toString());
        final String refusal = ChatText.noPermission(owner);

        run(owner, PREVIEW, "ring", BRICK_SLAB);

        verify(owner).sendMessage(refusal);
        assertNull(RingPreviews.of(owner));
    }

    /** Saving stores every trial value through edit, redraws the other viewers, and the preview carries on. */
    @Test
    void saveStoresWhatIsShowingThroughTheEditPath()
    {
        run(second, PREVIEW, "deploy");
        run(owner, PREVIEW, "deploy");
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        run(owner, PREVIEW, LIGHT, SEA_LANTERN);
        run(owner, PREVIEW, "flash", IRON_BLOCK);
        run(owner, PREVIEW, "style", "slow");
        settle();
        assertFalse(shows(second, Material.BRICK_SLAB));

        run(owner, PREVIEW, "-save");

        assertEquals(Material.BRICK_SLAB, endA.getRingMaterial());
        assertEquals(Material.SEA_LANTERN, endA.getLightMaterial());
        assertEquals(Material.IRON_BLOCK, endA.getFlashMaterial());
        assertEquals(RingStyle.SEQUENTIAL, endA.getStyle());
        assertEquals(Material.STONE_SLAB, endB.getRingMaterial(), "only the end that was previewed");
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD), times(4));
        assertTrue(shows(second, Material.BRICK_SLAB), "another viewer sees it as an edit would show it");
        verify(owner).sendMessage(plainContains("Ring material"));
        final RingPreview carried = RingPreviews.of(owner);
        assertFalse(carried.hasTrial(), "the trial is now the stored value");
        assertTrue(shows(owner, Material.BRICK_SLAB));
    }

    /** Saving needs the permission edit needs, checked when it is asked for. */
    @Test
    void saveIsRefusedOnceThePairIsNoLongerTheirs()
    {
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        pair.setOwner(UUID.randomUUID().toString());
        final String refusal = ChatText.noPermission(owner);

        run(owner, PREVIEW, "-save");

        verify(owner).sendMessage(refusal);
        assertEquals(Material.STONE_SLAB, endA.getRingMaterial());
        assertTrue(RingPreviews.of(owner).hasTrial(), "nothing was stored or dropped");
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /** Saving with nothing tried, or with no preview, says so. */
    @Test
    void saveWithNothingToSaveSaysSo()
    {
        run(owner, PREVIEW, "-save");
        run(owner, PREVIEW);
        run(owner, PREVIEW, "-save");

        verify(owner).sendMessage(plainContains("no ring preview showing"));
        verify(owner).sendMessage(plainContains("not trying anything to save"));
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /** Clearing takes the trial values away with the drawing. */
    @Test
    void clearDiscardsTheTrialValues()
    {
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        run(owner, PREVIEW, "-clear");

        run(owner, PREVIEW, "deploy");
        settle();

        assertFalse(RingPreviews.of(owner).hasTrial());
        assertTrue(shows(owner, Material.STONE_SLAB));
        assertFalse(shows(owner, Material.BRICK_SLAB));
        assertEquals(Material.STONE_SLAB, endA.getRingMaterial());
    }

    private RingPair otherPair()
    {
        final RingPair other = new RingPair("bbbb0002", WORLD, ring(400, 400), ring(600, 600));
        other.setOwner(OWNER);
        other.setOwnerName("Justin");
        RingManager.addPair(other, 4);
        return other;
    }

    private void walkTo(final Player who, final int x, final int z)
    {
        when(who.getLocation()).thenReturn(new Location(world, x, 65, z));
    }

    /** An edit does not take a field back from the viewer who is trying a value on it. */
    @Test
    void anEditDoesNotOverrideATrialOnThatField()
    {
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        run(owner, PREVIEW, "deploy");
        run(second, PREVIEW, "deploy");
        settle();

        run(second, "edit", "ring", "smooth_stone_slab");

        assertTrue(shows(second, Material.SMOOTH_STONE_SLAB), "the edit shows on a viewer with no trial");
        assertTrue(shows(owner, Material.BRICK_SLAB), "the trial stays");
        assertFalse(shows(owner, Material.SMOOTH_STONE_SLAB));
    }

    /** A slab edited while the rings rise: every later frame is in the new slab, and no old one is left. */
    @Test
    void anEditWhileDeployingFinishesInTheNewSlab()
    {
        run(owner, PREVIEW, "deploy");
        held.runNextOtherThan(TIMEOUT);
        clearInvocations(owner);

        run(owner, "edit", "ring", BRICK_SLAB);
        settle();

        assertTrue(everSent(owner, Material.BRICK_SLAB));
        assertFalse(everSent(owner, Material.STONE_SLAB), "no frame after the edit is in the old slab");
        assertFalse(shows(owner, Material.STONE_SLAB));
    }

    /** The same while the light runs through the stack. */
    @Test
    void anEditWhileLightingFinishesInTheNewSlab()
    {
        run(owner, PREVIEW, "deploy");
        settle();
        run(owner, PREVIEW, LIGHT);
        held.runNextOtherThan(TIMEOUT);
        clearInvocations(owner);

        run(owner, "edit", "ring", BRICK_SLAB);
        settle();

        assertTrue(everSent(owner, Material.BRICK_SLAB));
        assertFalse(everSent(owner, Material.STONE_SLAB));
        assertFalse(shows(owner, Material.STONE_SLAB));
    }

    /** The same while the rings come home, and the next deploy is in the new slab too. */
    @Test
    void anEditWhileCollapsingFinishesInTheNewSlab()
    {
        run(owner, PREVIEW, "deploy");
        settle();
        run(owner, PREVIEW, "collapse");
        held.runNextOtherThan(TIMEOUT);
        clearInvocations(owner);

        run(owner, "edit", "ring", BRICK_SLAB);
        settle();

        assertFalse(everSent(owner, Material.STONE_SLAB), "the frames after the edit are not in the old slab");
        assertFalse(shows(owner, Material.STONE_SLAB));
        run(owner, PREVIEW, "deploy");
        settle();
        assertTrue(shows(owner, Material.BRICK_SLAB));
        assertFalse(shows(owner, Material.STONE_SLAB));
    }

    /** The form that names a pair: it lights that pair's nearer end and tries the value on it. */
    @Test
    void aTrialCanNameThePairById()
    {
        run(owner, PREVIEW, "aaaa0001", "ring", BRICK_SLAB);

        assertEquals(endA, RingPreviews.of(owner).getRing());
        assertEquals(Material.BRICK_SLAB, RingPreviews.of(owner).getTrialRing());
    }

    /** -save with an id that is not the standing preview's pair writes nothing. */
    @Test
    void saveNamingAnotherPairIsRefused()
    {
        otherPair();
        run(owner, PREVIEW, "ring", BRICK_SLAB);

        run(owner, PREVIEW, "bbbb0002", "-save");

        verify(owner).sendMessage(plainContains("You are previewing aaaa0001"));
        assertEquals(Material.STONE_SLAB, endA.getRingMaterial());
        assertTrue(RingPreviews.of(owner).hasTrial());
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /** -save naming the standing preview's own pair is the same as plain -save. */
    @Test
    void saveNamingThePreviewsOwnPairSaves()
    {
        run(owner, PREVIEW, "ring", BRICK_SLAB);

        run(owner, PREVIEW, "aaaa0001", "-save");

        assertEquals(Material.BRICK_SLAB, endA.getRingMaterial());
    }

    /** A trial goes to the standing preview from wherever the player has walked to. */
    @Test
    void aTrialReachesTheStandingPreviewAfterSteppingOffThePad()
    {
        run(owner, PREVIEW);
        final RingPreview standing = RingPreviews.of(owner);
        walkTo(owner, 900, 900);

        run(owner, PREVIEW, "ring", BRICK_SLAB);

        assertSame(standing, RingPreviews.of(owner));
        assertEquals(Material.BRICK_SLAB, standing.getTrialRing());
    }

    /** Being nearer another end, or standing in another ring, neither replaces the preview nor drops trials. */
    @Test
    void aTrialNeverReplacesThePreviewOrDropsTrialsNearAnotherEnd()
    {
        otherPair();
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        final RingPreview standing = RingPreviews.of(owner);

        walkTo(owner, 200, 200);
        run(owner, PREVIEW, "flash", IRON_BLOCK);
        walkTo(owner, 400, 400);
        run(owner, PREVIEW, LIGHT, SEA_LANTERN);

        assertSame(standing, RingPreviews.of(owner));
        assertEquals(endA, standing.getRing());
        assertEquals(Material.BRICK_SLAB, standing.getTrialRing(), "the earlier trial is kept");
        assertEquals(Material.IRON_BLOCK, standing.getTrialFlash());
        assertEquals(Material.SEA_LANTERN, standing.getTrialLight());
    }

    /** A trial naming another pair from the standing preview is refused and changes nothing. */
    @Test
    void aTrialNamingAnotherPairIsRefused()
    {
        otherPair();
        run(owner, PREVIEW, "ring", BRICK_SLAB);
        final RingPreview standing = RingPreviews.of(owner);

        run(owner, PREVIEW, "bbbb0002", "flash", IRON_BLOCK);

        verify(owner).sendMessage(plainContains("You are previewing aaaa0001"));
        assertSame(standing, RingPreviews.of(owner));
        assertNull(standing.getTrialFlash());
        assertEquals(Material.BRICK_SLAB, standing.getTrialRing());
    }
}
