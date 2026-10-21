# Translating

Each player sees chat messages in their own client's language, where the plugin has it, and in
English where it does not. Messages to the console, command blocks and any other sender that is
not a player use `server-language` (later, the log and text the server puts in the world will too).

**Only some messages are translatable so far** ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)):
`/wormhole compass`, building or dialling a gate by coordinates from the console, and building,
dialling and travelling through a gate: `/dial`, the DHD, the dial sign, the iris, the toll, the
cooldown and the region refusals; what transport rings tell the people standing in them;
building, listing, editing and removing rings: `/wormhole ring create`, `cancel`, `list`, `remove`,
`edit`, `allow`, `deny`, `owner`, `build` and `fire`, with every reason a circle is refused; and
beaming: what a beam tells its traveller, its cooldown and cost, and `/wormhole beam to`, `list`,
`place` and `admin`; what a mirror says when somebody clicks it or looks at it; and looking after
gates: `/wormhole gate edit`, `gate shapes`, `gate import`, `custom`, the portal, iris and light
material commands and `wooshdepth`; building by hand: `/wormhole gate build`, everything
`gate preview` says and what pointing at each of its buttons shows, and `/wormhole complete`; and the settings: what `/wormhole config` says when it sets,
refuses or lists a setting, the `config -reload` report, `/wormhole cooldown` and the two timeout
commands, and the notice of a newer release an admin gets on joining; and the small admin commands: `redstone`, `owner`, `idc`, `remove`, `list`, `force`, `go`, `refresh`, `restrict` and `freya`; and the first half of `/wormhole mirror`: `create` (except the coordinate-reading refusals), `set -start`, `set -capture` and `remove`. Setting names and the values
a setting takes stay as they are. The rest are
English until they are moved over, a few at a time. Command usage and help lines come later, and
**sign text stays English for now**: a sign is read by everyone who walks past, whatever their
language.

## How a message is chosen

A client set to German reports `de_de`. For each message, the first of these that has it:

1. `de_de`: your `lang/de_de.yml`, then the plugin's own `de_de` file.
2. `de`: your `lang/de.yml`, then the plugin's own `de` file.
3. `server-language`, the same way.
4. English.

So a `de.yml` serves every German client, and a `de_at.yml` need only hold what Austrian German says
differently. The console, command blocks and other non-player senders start at step 3. A file of your own only needs
the messages you want different, so it can reword some of a shipped language, or of English with
`lang/en.yml`. A file made for an older version keeps working after an update; the new messages
come from the plugin until you add them.

## Adding a language

1. Run `/wormhole lang export de`, naming the client language: `de`, `de_de`, `pt_br`. It writes
   `plugins/WormholeXTreme/lang/de.yml` with every translatable message in English, from the jar you
   run. A code is lower-case letters and `_`, at most 16, as the client names it; capitals are read as
   lower case and a `-` as `_`, as for `server-language`, so `pt-BR` writes `pt_br.yml`.
2. Or copy [`src/main/resources/lang/en.yml`](../../src/main/resources/lang/en.yml) into that folder
   yourself, renamed, from the [release tag](https://github.com/khanjal/Wormhole-X-Treme/tags) you
   run, such as `v1.12.0`: the file on `main` can hold messages your jar does not have yet. A file
   named with a digit, a `-` or a leading or trailing `_` is not read.
3. Translate the text in quotes. Leave the keys on the left as they are.
4. Keep each `{0}`, `{1}` and so on: that is where a gate name, a number or a position goes. Move
   them wherever your language's word order needs them.
5. Keep each `§` colour code, around the same words. `§7` is the body grey every line returns to.
6. Run `/wormhole config -reload`. Players with that client language see it from their next message.

```yaml
# plugins/WormholeXTreme/lang/de.yml
compass.set: "Kompass zeigt auf das Wurmloch {0}."
compass.none: "Keine Wurmlöcher zum Verfolgen."
```

To send the console and command blocks that language too, set `server-language: de` in
`config.yml`, or run `/wormhole config server-language de`. A `-` is read as `_` and capitals as
lower case, so `pt-BR` is `pt_br`. Write `server-language: "no"` for Norwegian, in quotes: a bare `no`
is read as a switch, so startup uses English and says so in the log, and `config -reload` refuses it.

**To share a translation**, open a pull request adding it to `src/main/resources/lang/`. A language
the plugin ships needs no file in the plugin folder at all.

## Rewording the English

`/wormhole lang export` with no code writes `lang/en.yml` with every message commented out, as
`# compass.none: "No wormholes to track."`. Remove the `# ` from the ones to reword and change their
text, then run `/wormhole config -reload`. The lines left commented keep following the plugin, so an
update that rewords or adds a message still reaches them. A language the plugin ships exports the
same way, its own messages commented out.

`lang export` needs `wormhole.config` and works from the console. It never replaces a file: if
`lang/<code>.yml` is there it says so and changes nothing. It is the only thing that writes to
`lang/`; the plugin never makes the folder on its own.

## When something is wrong

- **A `server-language` with no file is English**, with one warning in the log.
- **A file that is not valid YAML is ignored**, with a warning saying why.
- **Write each message in quotes.** YAML reads a bare `yes`, `no`, `on` or `off` as a switch, and a
  message that is not text is skipped.
