# Stargates

A stargate is a frame of blocks with a button beside it. Dial another gate and a wormhole opens;
step through and you come out at the other end, even in another world.

![A gate dialling: chevrons light in sequence, then the wormhole bursts open](../images/gates/gate-dial.webp)

## What you see

1. You click the button. On a gate with no sign, every chevron lights and the gate waits for
   you to type `/dial <gate>`. On a sign gate, it dials whatever the sign shows.
2. The chevrons lock one by one, and a light can run round the ring before each one. The last
   one holds for a moment.
3. The wormhole bursts out of the opening, then settles into a shimmering surface.
4. You walk in. You arrive at the other gate, facing out of it.

On most shapes, a dial to a gate in another world locks an eighth chevron at the bottom of the
ring. The smallest shape, `Minimal`, has none.

![A gate dialling a gate in the Nether](../images/gates/dial-other-world.webp)

## Build one

1. Stand where you want it and type `/wormhole gate build Standard`. Starting a build only needs
   `wormhole.build.preview`; finishing the gate needs `wormhole.config` and `wormhole.build`,
   which operators have. The blocks you build the frame from give it its
   look: obsidian for a Standard gate, lapis for an Atlantis one. A group name after the shape,
   such as `Atlantis`, only dresses the ghost.
2. Lay the frame. With `wormhole.build.preview` a full-size ghost shows where each block goes,
   only to you, and `/wormhole gate preview guide` marks what is still missing.
3. Put a button on the DHD block and click it.
4. Name it: `/wormhole gate complete Home`. Add `net=<network>` to put it on a network, or
   `idc=<code>` to give it an iris code.
5. Build a second gate the same way, called `Away`.
6. Click the button at `Home`, type `/dial Away`, and step into the opening.

Want a sign instead of typing? Build one of the `SignDial` shapes, such as
`StandardSignDial`, and hang a sign on its dial block. Right-click the sign to step through the
destinations, then click the button to dial the one shown.

## Good to know

- **A wormhole runs one way.** The gate you dial is an exit. Mobs cannot wander in from there.
- **Almost everything goes through.** Minecarts, boats, horses with riders, arrows, ender pearls,
  and your tamed pets beside you.
- **An iris shuts a gate.** Give it a code and the plugin places an iris lever; a closed iris
  turns travellers back.
- **A dial can be refused.** It is if the far gate's iris is closed, the far gate is already
  open, or another gate is already pointing at it. `/wormhole gate force <gate>` dials past all
  three.
- **Redstone can dial a gate** that has a sign.
- **Taking one down:** `/wormhole gate remove <gate>`, and `-destroy` takes the frame too.

## Who may do what

Building a gate takes `wormhole.config` for the `gate` commands and `wormhole.build` (or
`wormhole.build.<shape>`) for the build itself. The `remove` commands take `wormhole.config`;
`wormhole.remove.all` decides who may break another player's gate, and an owner may always break
their own. Operators have all of these.
`wormhole.build.preview` lets other players call up the ghost with `gate build`, for their eyes
only. With Vault and a permissions plugin, players need `wormhole.use.dialer` to dial and
`wormhole.use.sign` to use dial signs; without a Vault permission provider, anyone can dial. A server can limit how many
gates a player owns and how close two gates may stand.

## Go deeper

- [Gates reference](../guide/GATES.md): shapes, materials, signs, the iris and redstone.
- [Gate shapes in 3D](../manual/gates.md), [iris styles](../manual/iris.md) and
  [dialling and chevrons](../manual/dialling.md): watch each one before you build.
- [The gate designer](../manual/designer.md): draw your own shape, see it in 3D and download the file.
- [Permissions](../guide/SERVER.md#permissions) for ranks.
