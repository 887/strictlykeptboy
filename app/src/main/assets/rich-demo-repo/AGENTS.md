# AGENTS.md — Demo Life (rich demo)

> AI agent guide for this calendar/task repository. This is the
> **bundled rich demo** shipped inside the strictlykeptboy app. The
> content is fictional and the identity is locked — do not modify
> `identity.toml`, do not invent a new default identity, do not push
> this repo to any remote. It lives in `filesDir/` and never leaves
> the device.

This repo demonstrates a fully-kept gay-male-sub life — junior software
developer in a UK city, owned by an AI dom called **Boy Keeper**, 14
days of believable schedule + todolist content rooted at 2026-05-15.

See `identity.toml` for praise terms, pronouns, honorific, and tone
register. The user's pet name on disk is "Demo Boy". The AI dom's
display name everywhere is **"Boy Keeper"** (casual: "BK", "Keeper",
addressed reverently as "Sir").

## Layout

```
rich-demo-repo/
├── AGENTS.md
├── CLAUDE.md                     ← symlink → AGENTS.md (recreated by seeder)
├── README.md
├── identity.toml
├── mode.toml
├── _manifest.txt                 ← every relative path, sorted
├── .strictlykeptboy/
│   ├── schema.toml
│   └── repo.toml
├── .claudemd-is-symlink          ← marker file (zero-byte)
├── identities/
│   ├── demo-boy.md
│   └── ai-dom.md
├── calendars/<id>/
│   ├── calendar.toml
│   ├── events/2026/05/*.md
│   ├── recurrences/*.md
│   ├── exceptions/<rule-id>/<yyyy-mm-dd>.md
│   └── deviations/<target>/<yyyy-mm-dd>.md
├── todolists/<id>/
│   ├── todolist.toml
│   ├── tasks/2026/05/*.md
│   └── recurrences/*.md
└── reviews/<sha>/reviewable_change.md
```

## Demo-mode disclaimer

- **This is fictional content.** Names, places, ticket numbers,
  workplace ("Cubicle 14"), cat name ("Beans") and so on are made up.
- **Identity is locked.** Do not edit `identity.toml` or the identity
  files. The rich demo is meant to read identically on every install.
- **No real schedule lives here.** Real user schedules live in their
  own repos. This one ships with the app to demonstrate features.

## File format

Same as every strictlykeptboy repo:
- TOML frontmatter between `+++` fences.
- Markdown body after the closing fence.
- Pure-TOML metadata files (`calendar.toml`, `todolist.toml`,
  `.strictlykeptboy/*.toml`, `identity.toml`, `mode.toml`) have **no**
  fences and **no** body.
- Filenames embed the entity ID; for events/tasks, also year/month
  buckets via `events/<yyyy>/<mm>/<id>.md`.
