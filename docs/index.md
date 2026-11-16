# Wormhole X-Treme

<div style="text-align:center" markdown>

![A gate dialling: the chevrons light one by one, then the wormhole bursts open](images/gates/gate-dial.webp){ width="420" }

**Build a gate. Dial a friend's base. Step through.**
Stargate-style travel for your Minecraft server, with transport rings, beaming and quantum mirrors
for shorter hops.

[Get started](start.md){ .md-button .md-button--primary }
[Download on Modrinth](https://modrinth.com/plugin/wormhole-x-treme){ .md-button }
[Hangar](https://hangar.papermc.io/khanjal/Wormhole-X-Treme){ .md-button }
[SpigotMC](https://www.spigotmc.org/resources/wormhole-x-treme.138936/){ .md-button }
[See the gates in 3D](manual/gates.md){ .md-button }

</div>

## Four ways to get somewhere

<div class="grid cards" markdown>

-   **Stargates**

    ---

    ![A gate dialling a gate in the Nether](images/gates/dial-other-world.webp)

    Lay a frame of blocks and press the button. The chevrons light up one by one, then the
    wormhole opens. Step in and you come out at any other gate, even in another world.

    [Build a gate](guide/GATES.md)

-   **Transport rings**

    ---

    ![A pair of transport rings firing](images/rings/ring-cycle.webp)

    Lay a circle of slabs, then another one somewhere else. Walk into one and a stack of rings
    rises around you, light runs through it, and you arrive at its partner.

    [Build rings](guide/RINGS.md)

-   **Beaming**

    ---

    ![A player leaving in a column of light](images/beams/beam-up.webp)

    Nothing to build. Save a place where you stand, then beam to it from anywhere. You leave in
    a column of light.

    [Try beaming](guide/BEAMS.md)

-   **Quantum mirrors**

    ---

    ![A mirror showing the room it hangs in, then another world](images/mirrors/mirror-look.webp)

    Hang a banner on a wall and it shows the room it is in. Right-click to choose where it opens
    onto, punch it, and you step through.

    [Hang a mirror](guide/MIRRORS.md)

</div>

## Run it on your server in three steps

1. **Drop the jar into `plugins/`** and restart the server. Nothing else is needed.
2. **Build a gate.** Type `/wormhole gate build Standard`, lay the frame, click the button on the
   DHD, and name it with `/wormhole gate complete Home`.
3. **Build a second one and dial.** Click the first gate's button, type `/dial Away`, and walk
   into the opening.

The full walk-through is in [Getting started](start.md).

## What you get

- Minecarts, boats, horses with their riders, and anything else that wanders in all travel through
  a gate.
- Six gate shapes, from a small ring to a huge one. A gate takes its look from the blocks you
  build it with: the same `Standard` shape in obsidian or in lapis gives two different-looking
  gates.
- Mirrors that show a room, in real blocks.
- Every setting changes in game with `/wormhole config`, with no reload.
- Every sound is a setting, resource pack sounds included.
- It works with or without a permissions plugin, and charges through Vault only if you want it to.
- Gates are plain YAML files, and `/wormhole gate import` brings in gates from older forks.

## Compatibility

Minecraft 1.20 to 26.3, on Spigot, Paper and CraftBukkit (Purpur is best effort; Folia is not
supported). The jar needs Java 17 or later, and your Minecraft version may ask for more. The
[server owner's guide](guide/SERVER.md#compatibility) has the details.

---

<div class="grid cards" markdown>

-   **[Server owner's guide](guide/SERVER.md)**

    Install, configure, set permissions.

-   **Features**

    [Gates](guide/GATES.md) · [Rings](guide/RINGS.md) · [Beaming](guide/BEAMS.md) ·
    [Mirrors](guide/MIRRORS.md)

-   **For developers**

    [Gate internals](GATES.md) · [API](API.md) · [Development](DEVELOPMENT.md)

</div>
