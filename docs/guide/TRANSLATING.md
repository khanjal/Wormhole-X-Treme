# Translating

Each player sees chat messages in their own client's language, where the plugin has it, and in
English where it does not. The console and the log use `server-language`.

**Only some messages are translatable so far** ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)):
`/wormhole compass` and building or dialling a gate by coordinates from the console. The rest are
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
differently. The console, command blocks and the log start at step 3. A file of your own only needs
the messages you want different, so it can reword some of a shipped language, or of English with
`lang/en.yml`. A file made for an older version keeps working after an update; the new messages
come from the plugin until you add them.

## Adding a language

1. Create the folder `plugins/WormholeXTreme/lang/`. The plugin never makes it, or writes anything
   in it.
2. Take the English from
   [`src/main/resources/lang/en.yml`](../../src/main/resources/lang/en.yml), every translatable
   message, and save it there named for the client language, such as `de.yml`, `de_de.yml` or
   `pt_br.yml`: lower case, letters and `_`, as the client's language list names them.
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

To put the console and the log in that language too, set `server-language: de` in `config.yml`, or
run `/wormhole config server-language de`. Write `server-language: "no"` for Norwegian, in quotes: a
bare `no` is read as a switch and refused.

**To share a translation**, open a pull request adding it to `src/main/resources/lang/`. A language
the plugin ships needs no file in the plugin folder at all.

## When something is wrong

- **A `server-language` with no file is English**, with one warning in the log.
- **A file that is not valid YAML is ignored**, with a warning saying why.
- **Write each message in quotes.** YAML reads a bare `yes`, `no`, `on` or `off` as a switch, and a
  message that is not text is skipped.
