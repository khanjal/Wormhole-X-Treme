# Translating

Chat messages can be read from a language file instead of the English built into the plugin. One
language serves the whole server.

**Only some messages are translatable so far** ([#26](https://github.com/khanjal/Wormhole-X-Treme/issues/26)):
`/wormhole compass` and building or dialling a gate by coordinates from the console. The rest are
English until they are moved over, a few at a time. Command usage and help lines come later.

## Making a translation

1. Start the server once. It writes `plugins/WormholeXTreme/lang/en.yml`, every translatable
   message in English.
2. Copy it beside itself as `lang/<code>.yml`, such as `de.yml` or `pt-br.yml`. A code is letters,
   digits, `-` and `_`, at most 16 of them.
3. Translate the text in quotes. Leave the keys on the left as they are.
4. Keep each `{0}`, `{1}` and so on: that is where a gate name, a number or a position goes. Move
   them wherever your language's word order needs them.
5. Keep each `§` colour code, around the same words. `§7` is the body grey every line returns to.
6. Set `language: <code>` in `config.yml`, or run `/wormhole config language <code>`.
7. After editing the file again, run `/wormhole config -reload`.

```yaml
# lang/de.yml
compass.set: "Kompass zeigt auf das Wurmloch {0}."
compass.none: "Keine Wurmlöcher zum Verfolgen."
```

## What happens when something is missing

- **A message your file leaves out is sent in English.** A file made for an older version keeps
  working after an update; the new messages are English until you add them.
- **A language with no file is English**, with one warning in the log.
- **A file that is not valid YAML is ignored**, with a warning saying why, and messages are English.
- **Write each message in quotes.** YAML reads a bare `yes`, `no`, `on` or `off` as a switch, and a
  message that is not text is skipped and sent in English. The same goes for the code itself:
  write `language: "no"` for Norwegian.

`lang/en.yml` is read too, so editing it changes the English. It is never overwritten, so after an
update it can hold older wording than the plugin's; delete it and restart to get the current one.
