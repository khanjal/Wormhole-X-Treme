# Translating

Chat messages can be read from a language file instead of the English built into the plugin. One
language serves the whole server.

**Only some messages are translatable so far** ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)):
`/wormhole compass` and building or dialling a gate by coordinates from the console. The rest are
English until they are moved over, a few at a time. Command usage and help lines come later.

## Adding a language

1. Create the folder `plugins/WormholeXTreme/lang/`. The plugin never makes it, or writes anything
   in it.
2. Take the English from
   [`src/main/resources/lang/en.yml`](../../src/main/resources/lang/en.yml), every translatable
   message, and save it there as `<code>.yml`, such as `de.yml` or `pt-br.yml`. A code is letters,
   digits, `-` and `_`, at most 16 of them.
3. Translate the text in quotes. Leave the keys on the left as they are.
4. Keep each `{0}`, `{1}` and so on: that is where a gate name, a number or a position goes. Move
   them wherever your language's word order needs them.
5. Keep each `§` colour code, around the same words. `§7` is the body grey every line returns to.
6. Set `language: <code>` in `config.yml`, or run `/wormhole config language <code>`. Write
   `language: "no"` for Norwegian, in quotes: a bare `no` is read as a switch and refused.
7. After editing the file again, run `/wormhole config -reload`.

```yaml
# plugins/WormholeXTreme/lang/de.yml
compass.set: "Kompass zeigt auf das Wurmloch {0}."
compass.none: "Keine Wurmlöcher zum Verfolgen."
```

**To share a translation**, open a pull request adding it to `src/main/resources/lang/`. A language
the plugin ships needs no file in the plugin folder at all.

## How a message is chosen

For each message, the first of these that has it:

1. `lang/<code>.yml` in the plugin folder, if you made one.
2. The plugin's own file for that language, if it ships one.
3. The plugin's English.

So a file of your own only needs the messages you want different: it can reword some of a shipped
language, or of the English with `lang/en.yml`. A file made for an older version keeps working
after an update; the new messages come from the plugin until you add them.

- **A language with neither file is English**, with one warning in the log.
- **A file that is not valid YAML is ignored**, with a warning saying why.
- **Write each message in quotes.** YAML reads a bare `yes`, `no`, `on` or `off` as a switch, and a
  message that is not text is skipped.
