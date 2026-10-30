# bStats charts

What the plugin sends is described for server owners under *Metrics* in
[the server guide](guide/SERVER.md). This page is for the maintainer: the charts as they are set
up on the [bStats dashboard](https://bstats.org/plugin/bukkit/Wormhole%20X-Treme/34269), and
what has to change there when the plugin changes. The code is
[`MetricsSupport`](../src/main/java/com/wormhole_xtreme/wormhole/plugin/MetricsSupport.java).

bStats matches a chart by its **ID**, so the ID must be spelled exactly as the plugin sends it.
A chart that is not defined on the dashboard is dropped, without an error. The title is only a
label. (My reading of how bStats behaves, not something the plugin code can show: if a chart seems
empty, check its ID first.)

## Built in

bStats adds these for every plugin; the plugin sends nothing for them.

| ID | Title |
|---|---|
| `servers` | Servers using Wormhole X-Treme |
| `players` | Players on servers using Wormhole X-Treme |
| `onlineMode` | Online mode |
| `minecraftVersion` | Minecraft Version |
| `serverSoftware` | Server Software |
| `pluginVersion` | Plugin Version |
| `coreCount` | Core count |
| `osArch` | System arch |
| `os` | Operating System |
| `location` | Server Location |
| `javaVersion` | Java Version |
| `locationMap` | Server Location (disabled) |

## Custom

| ID | Title | Type | Values |
|---|---|---|---|
| `gates` | Stargates per server | Simple Pie | a range |
| `ring_pairs` | Transport ring pairs per server | Simple Pie | a range |
| `beam_destinations` | Beam destinations per server | Simple Pie | a range |
| `mirrors` | Quantum mirrors per server | Simple Pie | a range |
| `gate_dial_spin` | Dial spin pattern - Server Default | Simple Pie | `chevron`, `top`, `lap`, `fill`, `pegasus`, `chase`, `universe`, `overshoot`, `none` |
| `gate_view` | Gate View Setting | Simple Pie | `horizon`, `behind`, `open` |
| `gate_ripple` | Gate Ripple | Simple Pie | `on`, `off` |
| `gate_sounds` | Gate Sounds | Simple Pie | `on`, `off` |
| `gate_iris_animation` | Iris Animation | Simple Pie | `sweep`, `spiral`, `rows`, `columns`, `instant`, `other` |
| `gate_shapes` | Gate Shapes | Advanced Pie | `Standard`, `StandardSignDial`, `Minimal`, `MinimalSignDial`, `Horizontal`, `HorizontalSignDial`, `Large`, `Grand`, `Massive`, `custom` |
| `portal_material` | Portal Material - per gate | Advanced Pie | `water`, `lava`, `nether_portal`, `end_gateway`, `end_portal`, `other`, `unknown` |
| `gate_material` | Gate Material - per gate | Advanced Pie | the game's block names for the frame, lower case (`obsidian`, `lapis_block`, `nether_bricks`, ...), and `unknown` |
| `integrations` | Integrations | Advanced Pie | `Vault`, `LuckPerms`, `PlaceholderAPI`, `CoreProtect`, `WorldGuard`, `dynmap`, `BlueMap`, `squaremap`, `Pl3xMap`, `none` |

- **A range** is one of `0`, `1-5`, `6-20`, `21-50`, `51-200`, `200+`.
- **Simple Pie** charts send one value per server. **Advanced Pie** charts send a name and a
  count: `gate_shapes`, `portal_material` and `gate_material` count per gate, `integrations` counts each plugin
  once, and sends `none` when there are none, since a chart with no entries is not sent. For the
  same reason `gate_shapes`, `portal_material` and `gate_material` only count servers with at least one gate, so
  their totals are lower than `servers`.
- The plugin never sends a name a server chose: a shape that does not ship with it, or a gate with
  no shape, is `custom`, a portal material outside the five is `other`, and an iris animation that
  is not one of the five is `other`. Every value is from a closed set, but not every set is short:
  `gate_material` is the frame block's own name, one of the game's (a few hundred in practice),
  however a server's material groups are called.
- `gate_material` has a larger `unknown` than `portal_material`. A gate's frame is found from the
  world the first time anything asks, and until then a gate in an unloaded chunk cannot be told
  without loading it. Groups differ in their frame (never in having to share a portal), so on a
  server with more than one group that is every gate nobody has used since it started.

## When the plugin changes

The dashboard does not follow the code. Add or edit the chart there when you:

- add a chart, in `MetricsSupport`: create it with the same ID and type (`gate_material`, an
  Advanced Pie, has to be created on the dashboard before its counts show);
- change any of the below: the Metrics section of [guide/SERVER.md](guide/SERVER.md) repeats the
  same lists, so edit it too;
- change `range()`: it sets the values of `gates`, `ring_pairs`, `beam_destinations` and `mirrors`
  together;
- add a shipped gate shape (`ShippedShapes.NAMES`), an iris animation
  (`ConfigManager.irisAnimations()`), a dial
  spin pattern (`DialSpinPattern`), a gate view (`ConfigManager.GATE_VIEWS`), a portal material
  (`PORTAL_MATERIALS`) or an integration (`INTEGRATIONS`): add the value to the chart's filter, if
  it has one, or the new value is dropped.

The dashboard's **Enable filters** option is described there as enforcing input rules. If it is
on for a chart, enter the values above as spelled, capitals included, or a value left out may be
rejected. Leave it off for `gate_material`: its values are every block the game has, so a filter
would drop most of them.
