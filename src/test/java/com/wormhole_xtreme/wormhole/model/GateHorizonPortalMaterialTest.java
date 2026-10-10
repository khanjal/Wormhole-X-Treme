package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * Whoever is not drawn a gate's view sees the gate's own portal material, never a stand-in (#516).
 *
 * <p>A gate's wormhole is whatever its own setting, shape or palette names: water by default, lava, a
 * nether portal, or anything a shape file says. Somebody behind a gate whose horizon has cleared for
 * its view is sent that material, laid across the opening the way the gate faces, and the viewer in
 * front drawn the view is sent the opening empty. A real gate resolves the material; the block data
 * is the real {@link MaterialUtils#drawnAcross}, only the server's {@code createBlockData} stood in for.
 */
class GateHorizonPortalMaterialTest
{
    private static final int CELLS = 9;

    private World world;
    private Player front;
    private Player behind;
    private Stargate gate;
    private final BlockData air = mock(BlockData.class);
    private final BlockData water = mock(BlockData.class);
    private MockedStatic<MaterialUtils> materials;
    private MockedStatic<StargateManager> manager;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        GateViews.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        // The gate faces east, its opening in the plane x = 20: in front is x > 20.
        front = playerAt(25.0);
        behind = playerAt(15.0);
        when(world.getPlayers()).thenReturn(List.of(front, behind));

        gate = new Stargate();
        gate.setGateName("Abydos");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.EAST);
        for (int z = 10; z <= 12; z++)
        {
            for (int y = 64; y <= 66; y++)
            {
                gate.getGatePortalBlocks().add(new Location(world, 20, y, z));
            }
        }
        gate.setGateActive(true);
        gate.setGatePortalOpen(true);

        materials = mockStatic(MaterialUtils.class, CALLS_REAL_METHODS);
        materials.when(() -> MaterialUtils.drawnAs(Material.AIR)).thenReturn(air);
        materials.when(() -> MaterialUtils.drawnAs(Material.WATER)).thenReturn(water);
        manager = mockStatic(StargateManager.class);
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate));
        manager.when(() -> StargateManager.getStargate("Abydos")).thenReturn(gate);

        // Cleared for its view, which the front player is drawn and sent.
        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("Abydos");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        manager.close();
        materials.close();
        GateViews.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Player playerAt(final double x)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, x, 64.0, 11.0));
        return player;
    }

    /** What the server would make of a material, told apart from every other. */
    private BlockData drawnAs(final Material material)
    {
        final BlockData data = mock(BlockData.class);
        materials.when(() -> MaterialUtils.drawnAs(material)).thenReturn(data);
        return data;
    }

    /**
     * Sends the front player the cleared opening, then checks both sides: the empty opening in front, the
     * gate's own material behind, from a chunk crossing and from walking round.
     */
    private void eachSideSeesItsOwn(final BlockData own) throws ReflectiveOperationException
    {
        GateViews.drawn(front.getUniqueId(), front, Set.of("gate:Abydos"));
        verify(front, times(CELLS).description("drawn the view: the opening empty")).sendBlockChange(any(Location.class), eq(air));

        StargateBlockSetup.refreshPortalVisuals(behind);
        verify(behind, times(CELLS).description("behind the gate: the gate's own portal material"))
            .sendBlockChange(argThat(at -> at.getBlockX() == 20), eq(own));
        verify(behind, never().description("never water in its place")).sendBlockChange(any(Location.class), eq(water));

        GateViews.drawn(front.getUniqueId(), front, Set.of());
        verify(front, times(CELLS).description("walked behind: the gate's own portal material again"))
            .sendBlockChange(any(Location.class), eq(own));
        final Map<UUID, Set<String>> through = PrivateStatics.of(GateViews.class, "SEES_THROUGH");
        assertEquals(new HashSet<>(), new HashSet<>(through.keySet()), "and no longer remembered as drawn the view");
    }

    @Test
    void aLavaGateIsLavaBehindIt() throws ReflectiveOperationException
    {
        gate.setGateCustom(true);
        gate.setGateCustomPortalMaterial(Material.LAVA);

        eachSideSeesItsOwn(drawnAs(Material.LAVA));
    }

    /**
     * A nether portal is laid across the opening: an east-facing gate opens along Z, and left at the
     * game's default axis it showed a sliver edge-on.
     */
    @Test
    void aNetherPortalGateIsANetherPortalAcrossItsOpeningBehindIt() throws ReflectiveOperationException
    {
        gate.setGateCustom(true);
        gate.setGateCustomPortalMaterial(Material.NETHER_PORTAL);
        final Orientable portal = mock(Orientable.class);
        when(portal.getAxes()).thenReturn(Set.of(Axis.X, Axis.Z));
        materials.when(() -> MaterialUtils.drawnAs(Material.NETHER_PORTAL)).thenReturn(portal);

        eachSideSeesItsOwn(portal);

        verify(portal, never()).setAxis(Axis.X);
        verify(portal, times(2).description("turned to lie in the opening, once a send")).setAxis(Axis.Z);
    }

    @Test
    void aGateWhoseShapeNamesItsPortalShowsThatBehindIt() throws ReflectiveOperationException
    {
        final StargateShape shape = new StargateShape();
        shape.setShapePortalMaterial(Material.END_GATEWAY);
        gate.setGateShape(shape);

        eachSideSeesItsOwn(drawnAs(Material.END_GATEWAY));
    }
}
