# strictlykeptboy — household / travel / vacation templates + calendar supersedence (DRAFT)

## Status: ✅ INTEGRATED — see main.md Phases AAA / BBB / CCC / DDD + decisions D.75..D.87 + deep-dives RV-P/Q, DM-W/X/Y/Z/AA, NS-Z, CLI-U, TW-J/K/L, UI-OO..VV, SH-K/L (shared-schedules Phase YY+OO extensions)

## Framing

This draft extends six existing seams (phases HV-A..HV-J cover the
original four; HV-K..HV-O extend coverage to ADHD body-doubling
anchors, medication + menstrual management, event attachments, and a
multi-reminder + briefing surface; HV-P..HV-R extend coverage further
to a leisure / scheduled-play atomic template, a free-vs-strictly-kept
mode contract with review-feed + AI-dom-agent persona + cross-mode
migration paths, and a praise/pronouns/tone `identity.toml` surface
plumbed through a new LW Screen 3.5 — all 18 phases comprehensive,
ADHD-brain exhaustive, kink-positive *furry-cute-subby-want-to-be-a-
good-boy* register, NOT authoritarian D/s, NOT military):

- **Phase XX (atomic activities + inverted habits)** — adds six new atomic-template families:
  `atomic-household.toml`, `atomic-travel-prep.toml`, `atomic-flight-day.toml`,
  `atomic-vacation-daily.toml`, `atomic-adhd-anchors.toml`,
  `atomic-medication.toml`, `atomic-menstrual-cycle.toml`. Same TOML schema as the AT-D / AT-E / AT-F
  templates already integrated; same inverted-default semantics (the user
  acts only on deviation; the schedule self-completes otherwise).
- **Phase E (resolver)** — adds a *supersedence pass* layered on top of the
  existing overlay-priority resolution. A vacation calendar can pause the
  routine calendars for a date range without deleting their content. Also
  adds an *off-schedule detection pass* keyed on per-calendar
  `baseline_cadence` (HV-N).
- **Phase K (lifestyle wizard)** — sister wizard: a smaller, in-app
  multi-screen *quick-vacation wizard* lives at Settings → "+ Plan a trip"
  and as a calendar-level "+ overlay from template" entry-point. It is
  *not* the first-launch wizard; it reuses the bat-mascot sticker beats
  but operates on an already-running app.
- **Phase M (notifications)** — extends the single-reminder model into a
  multi-reminder schema with heads-up / pre-event / at-start /
  post-event-checkin / morning-briefing / evening-briefing / all-day-banner
  reminder kinds, plus notification stacking and a system `cal-briefings`
  calendar that auto-generates morning + evening briefing bodies (HV-N).
- **Phase P (import/export) + DM event schema** — extends event-file
  frontmatter with an `attachments` array (links / QR / files / barcodes /
  vcards / locations), Git-LFS aware, privacy-aware (HV-M).
- **Phase XX (atomic activities + inverted habits) — second seam** —
  adds `atomic-leisure.toml` (HV-P): downtime is *also* scheduled, per
  user explicit direction. Leisure-on-the-schedule is the architecture
  that lets master/dom see at-a-glance whether the boy is available,
  and lets the boy himself say yes to free time without guilt because
  it is ON the schedule. Inverted-habit defaults apply: default state
  = done = took the rest; deviation = "I skipped my downtime to grind
  work" is its own slob-drift mode the schedule catches.
- **Phase S (settings) + Phase YY (cross-repo feedback) + Phase OO
  (cross-repo state) + new mode contract** — HV-Q introduces the
  free-vs-strictly-kept mode toggle, the per-commit review-feed
  written to `reviews/<commit-sha>/`, the AI-dom-agent persona system
  (dom-Claude as a first-class participant with git RW access), and
  the locked migration paths between free / kept-by-AI / kept-by-
  human / self-keep. Toxic-dom safety affordances are non-negotiable:
  the boy always retains write access and can transition out
  unilaterally with a 24h cooling-off confirmation.
- **Phase K (lifestyle wizard) + new Screen 3.5** — HV-R inserts a
  *praise + pronouns + honorific + tone register + emoji density*
  wizard screen between Alignment (LW-D) and Lifestyle (LW-E),
  writing per-repo `identity.toml` at calendar-repo root. Re-editable
  in Settings → Identity with live-preview. Locked separation:
  personal-preference content lives in `identity.toml`;
  architectural guidance stays in `AGENTS.md` / `CLAUDE.md` (which
  carry only a single reference line to identity.toml).

Style register: kink-positive *furry-cute-subby-want-to-be-a-good-boy*,
NOT authoritarian D/s, NOT military. Every entry carries a
`neutral_title` so the neutral-mode toggle (K-3 / D.55) renders the same
template with non-kink-coded copy without losing the entry. ADHD-brain
comprehensiveness: enumerate every granular task; never collapse two
actions into one to keep the list "clean".

Vacation philosophy (LOCKED): *vacation is the lazy-slob risk zone. The
vacation-daily template's job is to prevent slob-drift without imposing
the full home routine. The good-boy stays a good-boy by doing the
bare-essentials on schedule.* The vacation calendar SUPERSEDES the
routine calendars (work / university / kink-routine) and runs its own
relaxed-but-mandatory self-care anchors. Medication / critical health /
pet-care calendars do **not** get superseded.

---

## Phase HV-A — Household chores atomic template

New template file `templates/atomic-household.toml`. Same TOML schema as
`atomic-self-care.toml` (AT-D). All entries support `neutral_title`,
`tags`, optional `subbeat` blocks, optional `kink_variant_of` for
cute-coded alternates.

- [ ] **HV-A.1** Path: `templates/atomic-household.toml`. Schema header:
      ```toml
      schema_version = 1
      template_id = "atomic-household"
      display_name = "Atomic household"
      category = "household"
      neutral_safe = true
      ```
- [ ] **HV-A.2** Trash variants. Region defaults are EU-DE-style (user
      adjusts in wizard); all weekly unless noted. LOCKED entries:
      ```toml
      [[entry]]
      id = "trash-common"
      title = "good boy takes out the trash"
      neutral_title = "Take out general trash"
      duration_minutes = 5
      sticker_id = "trash-bag"
      category = "household"
      default_cadence = "weekly"
      default_times = ["19:00"]
      default_weekday = "sun"
      tags = ["household"]

      [[entry]]
      id = "trash-recyclables"
      title = "good boy sorts recyclables"
      neutral_title = "Take out recyclables (plastic/metal)"
      duration_minutes = 8
      sticker_id = "recycle"
      default_cadence = "biweekly"
      default_weekday = "tue"
      tags = ["household"]

      [[entry]]
      id = "trash-paper"
      title = "good boy bundles paper"
      neutral_title = "Take out paper/cardboard"
      duration_minutes = 10
      sticker_id = "paper-bundle"
      default_cadence = "biweekly"
      tags = ["household"]

      [[entry]]
      id = "trash-glass"
      title = "good boy hauls glass to the bottle bank"
      neutral_title = "Take glass to recycling bank"
      duration_minutes = 15
      sticker_id = "bottle"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "trash-organic"
      title = "good boy empties the bio bin"
      neutral_title = "Take out organic/bio waste"
      duration_minutes = 5
      sticker_id = "compost"
      default_cadence = "twice-weekly"
      default_times = ["19:00"]
      tags = ["household"]

      [[entry]]
      id = "trash-batteries"
      title = "battery-drop run"
      neutral_title = "Drop batteries at collection point"
      duration_minutes = 10
      sticker_id = "battery"
      default_cadence = "quarterly"
      tags = ["household"]

      [[entry]]
      id = "trash-ewaste"
      title = "e-waste haul"
      neutral_title = "Drop electronics/e-waste at recycling"
      duration_minutes = 20
      sticker_id = "ewaste"
      default_cadence = "biannual"
      tags = ["household"]

      [[entry]]
      id = "trash-medication"
      title = "expired-meds drop"
      neutral_title = "Return expired medication to pharmacy"
      duration_minutes = 15
      sticker_id = "pill-box"
      default_cadence = "biannual"
      tags = ["household", "health"]

      [[entry]]
      id = "trash-lightbulbs"
      title = "bulb drop"
      neutral_title = "Take CFL/LED bulbs to e-waste"
      duration_minutes = 10
      sticker_id = "bulb"
      default_cadence = "annual"
      tags = ["household"]

      [[entry]]
      id = "trash-hazardous"
      title = "hazardous waste run"
      neutral_title = "Drop paint/oil/chemicals at hazardous-waste site"
      duration_minutes = 30
      sticker_id = "hazard"
      default_cadence = "as-needed"
      tags = ["household"]
      ```
- [ ] **HV-A.3** Laundry cycle. Each phase its own atomic so the user
      can mark *which step* deviated (e.g. washed but never folded).
      LOCKED entries:
      ```toml
      [[entry]]
      id = "laundry-sort"
      title = "good boy sorts laundry"
      neutral_title = "Sort laundry"
      duration_minutes = 5
      sticker_id = "laundry-basket"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Lights pile"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Darks pile"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Delicates / wool pile"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Towels / sheets pile"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Check pockets + zip zips"
      duration_seconds = 60

      [[entry]]
      id = "laundry-wash"
      title = "good boy runs the washing machine"
      neutral_title = "Run washing machine"
      duration_minutes = 5
      sticker_id = "washer"
      default_cadence = "weekly"
      followup_timer_minutes = 120   # auto-creates a "move-wet-laundry" reminder
      tags = ["household"]

      [[entry]]
      id = "laundry-move-wet"
      title = "good boy moves wet laundry"
      neutral_title = "Move wet laundry to dryer or hang"
      duration_minutes = 10
      sticker_id = "wet-shirt"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Undies + socks (small basket)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Shirts (hang)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Pants (hang)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Towels / sheets (rack or dryer)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Delicates (flat-dry)"
      duration_seconds = 120

      [[entry]]
      id = "laundry-fold"
      title = "good boy folds clean laundry"
      neutral_title = "Fold laundry"
      duration_minutes = 20
      sticker_id = "folded-shirts"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Undies + socks"
      duration_seconds = 240
      [[entry.subbeat]]
      label = "Shirts"
      duration_seconds = 240
      [[entry.subbeat]]
      label = "Pants"
      duration_seconds = 240
      [[entry.subbeat]]
      label = "Towels"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Sheets"
      duration_seconds = 180

      [[entry]]
      id = "laundry-put-away"
      title = "good boy puts laundry away"
      neutral_title = "Put laundry away"
      duration_minutes = 10
      sticker_id = "dresser"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Drawer (undies, socks, shirts)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Closet (pants, jackets, dresses)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Shoe rack / shelf"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Linen closet (towels, sheets)"
      duration_seconds = 120

      [[entry]]
      id = "laundry-iron"
      title = "ironing"
      neutral_title = "Iron clothes"
      duration_minutes = 20
      sticker_id = "iron"
      default_cadence = "as-needed"
      tags = ["household"]

      [[entry]]
      id = "laundry-lint-trap"
      title = "lint trap clean"
      neutral_title = "Clean dryer lint trap"
      duration_minutes = 1
      sticker_id = "lint"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "laundry-washer-clean"
      title = "washing machine self-clean cycle"
      neutral_title = "Run washing-machine self-clean cycle"
      duration_minutes = 5
      sticker_id = "washer-sparkle"
      default_cadence = "monthly"
      tags = ["household"]
      ```
- [ ] **HV-A.4** Dishes / kitchen. LOCKED:
      ```toml
      [[entry]]
      id = "dishwasher-load"
      title = "load the dishwasher"
      neutral_title = "Load dishwasher"
      duration_minutes = 5
      sticker_id = "dishwasher"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "dishwasher-run"
      title = "start the dishwasher"
      neutral_title = "Run dishwasher"
      duration_minutes = 1
      sticker_id = "dishwasher-on"
      default_cadence = "daily"
      default_times = ["22:30"]
      tags = ["household"]

      [[entry]]
      id = "dishwasher-unload"
      title = "unload the dishwasher"
      neutral_title = "Unload dishwasher"
      duration_minutes = 5
      sticker_id = "dishwasher-clean"
      default_cadence = "daily"
      default_times = ["07:30"]
      tags = ["household"]

      [[entry]]
      id = "dishes-handwash"
      title = "hand-wash dishes"
      neutral_title = "Hand-wash dishes"
      duration_minutes = 15
      sticker_id = "sponge"
      default_cadence = "as-needed"
      tags = ["household"]

      [[entry]]
      id = "dishes-dry-putaway"
      title = "dry rack put-away"
      neutral_title = "Put away air-dried dishes"
      duration_minutes = 3
      sticker_id = "drying-rack"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "kitchen-wipe-counters"
      title = "wipe counters"
      neutral_title = "Wipe kitchen counters"
      duration_minutes = 3
      sticker_id = "cloth"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "kitchen-wipe-stovetop"
      title = "wipe stovetop"
      neutral_title = "Wipe stovetop"
      duration_minutes = 3
      sticker_id = "stove"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "kitchen-microwave-inside"
      title = "clean inside microwave"
      neutral_title = "Clean microwave interior"
      duration_minutes = 5
      sticker_id = "microwave"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-microwave-outside"
      title = "wipe outside microwave"
      neutral_title = "Wipe microwave exterior"
      duration_minutes = 2
      sticker_id = "microwave"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-fridge-small"
      title = "fridge wipe-down"
      neutral_title = "Wipe fridge shelves (light clean)"
      duration_minutes = 10
      sticker_id = "fridge"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-fridge-deep"
      title = "deep-clean the fridge"
      neutral_title = "Deep-clean fridge interior"
      duration_minutes = 45
      sticker_id = "fridge-sparkle"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-fridge-handles"
      title = "wipe fridge handles + door"
      neutral_title = "Wipe fridge handles and door"
      duration_minutes = 2
      sticker_id = "fridge"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-freezer-defrost"
      title = "freezer defrost"
      neutral_title = "Defrost freezer"
      duration_minutes = 60
      sticker_id = "freezer"
      default_cadence = "biannual"
      tags = ["household"]

      [[entry]]
      id = "kitchen-empty-trash"
      title = "empty the kitchen bin"
      neutral_title = "Empty kitchen trash"
      duration_minutes = 2
      sticker_id = "trash-can"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "kitchen-replace-bag"
      title = "replace trash bag"
      neutral_title = "Replace kitchen trash bag"
      duration_minutes = 1
      sticker_id = "trash-bag"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "kitchen-coffee-clean"
      title = "clean coffee maker"
      neutral_title = "Clean coffee maker"
      duration_minutes = 10
      sticker_id = "coffee"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-kettle-descale"
      title = "descale kettle"
      neutral_title = "Descale kettle"
      duration_minutes = 15
      sticker_id = "kettle"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-sharpen-knives"
      title = "sharpen knives"
      neutral_title = "Sharpen knives"
      duration_minutes = 10
      sticker_id = "knife"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-oil-board"
      title = "oil cutting board"
      neutral_title = "Oil wooden cutting board"
      duration_minutes = 5
      sticker_id = "cutting-board"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "kitchen-oven-clean"
      title = "oven self-clean"
      neutral_title = "Run oven self-clean cycle"
      duration_minutes = 90
      sticker_id = "oven"
      default_cadence = "quarterly"
      tags = ["household"]
      ```
- [ ] **HV-A.5** Bathroom. LOCKED:
      ```toml
      [[entry]]
      id = "bath-toilet"
      title = "scrub the toilet"
      neutral_title = "Clean toilet"
      duration_minutes = 10
      sticker_id = "toilet"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Bowl (interior + under rim)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Seat (top + bottom)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Base + behind toilet"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Flush handle + lid exterior"
      duration_seconds = 60

      [[entry]]
      id = "bath-shower"
      title = "clean the shower"
      neutral_title = "Clean shower"
      duration_minutes = 20
      sticker_id = "shower-clean"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Walls / door"
      duration_seconds = 360
      [[entry.subbeat]]
      label = "Floor"
      duration_seconds = 240
      [[entry.subbeat]]
      label = "Drain hair removal"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Faucet + handles"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Squeegee + dry-wipe"
      duration_seconds = 120

      [[entry]]
      id = "bath-sink"
      title = "clean the bathroom sink"
      neutral_title = "Clean sink"
      duration_minutes = 8
      sticker_id = "sink"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Bowl"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Faucet"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Mirror"
      duration_seconds = 120

      [[entry]]
      id = "bath-shower-curtain"
      title = "wipe shower curtain"
      neutral_title = "Wipe shower curtain"
      duration_minutes = 5
      sticker_id = "curtain"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "bath-mop"
      title = "mop the bathroom"
      neutral_title = "Mop bathroom floor"
      duration_minutes = 8
      sticker_id = "mop"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "bath-refresh-towels"
      title = "swap fresh towels"
      neutral_title = "Refresh bathroom towels"
      duration_minutes = 3
      sticker_id = "towel"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "bath-refill-soap"
      title = "refill hand soap"
      neutral_title = "Refill soap dispenser"
      duration_minutes = 2
      sticker_id = "soap"
      default_cadence = "as-needed"
      tags = ["household"]

      [[entry]]
      id = "bath-refill-tp"
      title = "restock toilet paper"
      neutral_title = "Restock toilet paper"
      duration_minutes = 2
      sticker_id = "tp-roll"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "bath-descale-showerhead"
      title = "descale showerhead"
      neutral_title = "Descale showerhead"
      duration_minutes = 15
      sticker_id = "showerhead"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "bath-squeegee-daily"
      title = "squeegee shower glass"
      neutral_title = "Squeegee shower glass after shower"
      duration_minutes = 1
      sticker_id = "squeegee"
      default_cadence = "daily"
      tags = ["household"]
      ```
- [ ] **HV-A.6** Bedroom. LOCKED:
      ```toml
      [[entry]]
      id = "bed-make"
      title = "good boy makes the bed"
      neutral_title = "Make the bed"
      duration_minutes = 3
      sticker_id = "bed"
      default_cadence = "daily"
      default_times = ["08:00"]
      tags = ["household"]

      [[entry]]
      id = "bed-change-sheets"
      title = "change the sheets"
      neutral_title = "Change bed sheets"
      duration_minutes = 10
      sticker_id = "sheets"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "bed-flip-mattress"
      title = "flip / rotate mattress"
      neutral_title = "Flip and rotate mattress"
      duration_minutes = 10
      sticker_id = "mattress"
      default_cadence = "quarterly"
      tags = ["household"]

      [[entry]]
      id = "bed-wash-pillowcases"
      title = "wash pillowcases"
      neutral_title = "Wash pillowcases"
      duration_minutes = 5
      sticker_id = "pillow"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "bed-fluff-pillows"
      title = "fluff the pillows"
      neutral_title = "Fluff pillows"
      duration_minutes = 1
      sticker_id = "pillow"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "bed-vacuum-under"
      title = "vacuum under the bed"
      neutral_title = "Vacuum under bed"
      duration_minutes = 5
      sticker_id = "vacuum"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "bed-dust-nightstand"
      title = "dust nightstand"
      neutral_title = "Dust nightstand"
      duration_minutes = 3
      sticker_id = "duster"
      default_cadence = "weekly"
      tags = ["household"]
      ```
- [ ] **HV-A.7** Living spaces (per room — wizard duplicates per
      configured room). LOCKED:
      ```toml
      [[entry]]
      id = "living-vacuum"
      title = "vacuum / sweep"
      neutral_title = "Vacuum or sweep floors"
      duration_minutes = 15
      sticker_id = "vacuum"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "living-mop"
      title = "mop floors"
      neutral_title = "Mop hard floors"
      duration_minutes = 15
      sticker_id = "mop"
      default_cadence = "biweekly"
      tags = ["household"]

      [[entry]]
      id = "living-dust"
      title = "dust horizontal surfaces"
      neutral_title = "Dust horizontal surfaces"
      duration_minutes = 10
      sticker_id = "duster"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "living-glass-tables"
      title = "wipe glass tables"
      neutral_title = "Wipe glass tables"
      duration_minutes = 3
      sticker_id = "glass-table"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "living-windows-interior"
      title = "clean interior windows"
      neutral_title = "Clean windows (interior)"
      duration_minutes = 20
      sticker_id = "window"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "living-window-sills"
      title = "wipe window sills"
      neutral_title = "Clean window sills"
      duration_minutes = 5
      sticker_id = "sill"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "living-baseboards"
      title = "wipe baseboards"
      neutral_title = "Wipe baseboards"
      duration_minutes = 20
      sticker_id = "baseboard"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "living-light-fixtures"
      title = "clean light fixtures"
      neutral_title = "Clean light fixtures"
      duration_minutes = 15
      sticker_id = "lamp"
      default_cadence = "quarterly"
      tags = ["household"]

      [[entry]]
      id = "living-fan-blades"
      title = "wipe ceiling fan blades"
      neutral_title = "Wipe ceiling fan blades"
      duration_minutes = 10
      sticker_id = "fan"
      default_cadence = "quarterly"
      tags = ["household"]

      [[entry]]
      id = "living-vacuum-upholstery"
      title = "vacuum upholstery"
      neutral_title = "Vacuum couches / chairs"
      duration_minutes = 10
      sticker_id = "couch"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "living-rotate-cushions"
      title = "rotate couch cushions"
      neutral_title = "Rotate / fluff couch cushions"
      duration_minutes = 2
      sticker_id = "cushion"
      default_cadence = "weekly"
      tags = ["household"]
      ```
- [ ] **HV-A.8** Entry / mudroom. LOCKED:
      ```toml
      [[entry]]
      id = "entry-mats"
      title = "shake the door mats"
      neutral_title = "Shake out door mats"
      duration_minutes = 3
      sticker_id = "mat"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "entry-sweep"
      title = "sweep the entry"
      neutral_title = "Sweep entryway"
      duration_minutes = 3
      sticker_id = "broom"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "entry-handles"
      title = "wipe door handles"
      neutral_title = "Wipe door handles + light switches"
      duration_minutes = 3
      sticker_id = "handle"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "entry-organize-shoes"
      title = "organize shoes"
      neutral_title = "Organize shoes at entry"
      duration_minutes = 3
      sticker_id = "shoes"
      default_cadence = "weekly"
      tags = ["household"]
      ```
- [ ] **HV-A.9** Outdoor (if applicable; wizard gates on
      "has-outdoor-space"). LOCKED:
      ```toml
      [[entry]]
      id = "outdoor-water-plants"
      title = "water the plants"
      neutral_title = "Water outdoor plants"
      duration_minutes = 10
      sticker_id = "watering-can"
      default_cadence = "configurable"   # per plant
      tags = ["household"]

      [[entry]]
      id = "outdoor-repot"
      title = "repot a plant"
      neutral_title = "Repot outdoor plant"
      duration_minutes = 20
      sticker_id = "pot"
      default_cadence = "annual"
      tags = ["household"]

      [[entry]]
      id = "outdoor-prune"
      title = "prune the plants"
      neutral_title = "Prune outdoor plants"
      duration_minutes = 20
      sticker_id = "shears"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "outdoor-balcony-sweep"
      title = "sweep the balcony"
      neutral_title = "Sweep balcony / patio"
      duration_minutes = 10
      sticker_id = "broom"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "outdoor-weed-pull"
      title = "pull weeds"
      neutral_title = "Pull garden weeds"
      duration_minutes = 30
      sticker_id = "weed"
      default_cadence = "biweekly-spring-summer"
      tags = ["household"]

      [[entry]]
      id = "outdoor-winter-prep"
      title = "winterize the balcony"
      neutral_title = "Winter prep (cover plants, store cushions)"
      duration_minutes = 60
      sticker_id = "snowflake"
      default_cadence = "annual"
      default_month = 10
      tags = ["household"]

      [[entry]]
      id = "outdoor-summer-prep"
      title = "summer prep"
      neutral_title = "Summer prep (uncover, plant)"
      duration_minutes = 60
      sticker_id = "sun"
      default_cadence = "annual"
      default_month = 4
      tags = ["household"]
      ```
- [ ] **HV-A.10** Mail / paperwork. LOCKED:
      ```toml
      [[entry]]
      id = "mail-check"
      title = "check the mailbox"
      neutral_title = "Check mailbox"
      duration_minutes = 2
      sticker_id = "mailbox"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "mail-sort"
      title = "open + sort the mail"
      neutral_title = "Open and sort mail"
      duration_minutes = 10
      sticker_id = "envelope"
      default_cadence = "weekly"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Keep (file pile)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Shred pile"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Pay pile (bills due)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Recycle pile"
      duration_seconds = 60

      [[entry]]
      id = "mail-shred"
      title = "shred the shred-pile"
      neutral_title = "Shred documents"
      duration_minutes = 10
      sticker_id = "shredder"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "mail-scan"
      title = "scan docs to cloud"
      neutral_title = "Scan documents to cloud"
      duration_minutes = 15
      sticker_id = "scanner"
      default_cadence = "monthly"
      tags = ["household"]

      [[entry]]
      id = "mail-taxes-prep"
      title = "tax-prep day"
      neutral_title = "Annual tax preparation"
      duration_minutes = 180
      sticker_id = "tax"
      default_cadence = "annual"
      default_month = 3
      tags = ["household"]
      ```
- [ ] **HV-A.11** Pantry / fridge management. LOCKED:
      ```toml
      [[entry]]
      id = "pantry-expiry"
      title = "check expiry dates"
      neutral_title = "Check pantry/fridge expiry dates"
      duration_minutes = 10
      sticker_id = "calendar-check"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "pantry-inventory"
      title = "inventory the pantry"
      neutral_title = "Inventory pantry"
      duration_minutes = 15
      sticker_id = "clipboard"
      default_cadence = "biweekly"
      tags = ["household"]

      [[entry]]
      id = "pantry-list"
      title = "write the shopping list"
      neutral_title = "Write shopping list"
      duration_minutes = 10
      sticker_id = "list"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "pantry-shop"
      title = "grocery run"
      neutral_title = "Grocery shopping"
      duration_minutes = 60
      sticker_id = "cart"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "pantry-unpack"
      title = "unpack groceries"
      neutral_title = "Unpack groceries"
      duration_minutes = 10
      sticker_id = "bag"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "pantry-fifo"
      title = "restock fridge FIFO"
      neutral_title = "Restock fridge by FIFO"
      duration_minutes = 5
      sticker_id = "fridge"
      default_cadence = "weekly"
      tags = ["household"]
      ```
- [ ] **HV-A.12** Pet care (wizard gates on "has-pet" + species).
      LOCKED:
      ```toml
      [[entry]]
      id = "pet-feed"
      title = "feed the pet"
      neutral_title = "Feed pet"
      duration_minutes = 3
      sticker_id = "pet-bowl"
      default_cadence = "twice-daily"
      tags = ["household", "pet", "nonSuperseable"]

      [[entry]]
      id = "pet-water"
      title = "fresh water for the pet"
      neutral_title = "Fresh water for pet"
      duration_minutes = 1
      sticker_id = "water-bowl"
      default_cadence = "daily"
      tags = ["household", "pet", "nonSuperseable"]

      [[entry]]
      id = "pet-walk"
      title = "walk the dog"
      neutral_title = "Walk dog"
      duration_minutes = 30
      sticker_id = "leash"
      default_cadence = "twice-daily"
      tags = ["household", "pet", "nonSuperseable"]

      [[entry]]
      id = "pet-litter"
      title = "scoop the litter box"
      neutral_title = "Clean litter box"
      duration_minutes = 5
      sticker_id = "litter"
      default_cadence = "daily"
      tags = ["household", "pet", "nonSuperseable"]

      [[entry]]
      id = "pet-brush"
      title = "brush the pet"
      neutral_title = "Brush pet"
      duration_minutes = 10
      sticker_id = "brush"
      default_cadence = "weekly"
      tags = ["household", "pet"]

      [[entry]]
      id = "pet-nails"
      title = "clip pet nails"
      neutral_title = "Clip pet nails"
      duration_minutes = 10
      sticker_id = "clippers"
      default_cadence = "monthly"
      tags = ["household", "pet"]

      [[entry]]
      id = "pet-vet"
      title = "vet check-up"
      neutral_title = "Annual vet check-up"
      duration_minutes = 60
      sticker_id = "vet"
      default_cadence = "annual"
      tags = ["household", "pet", "nonSuperseable"]

      [[entry]]
      id = "pet-grooming"
      title = "pro grooming appointment"
      neutral_title = "Professional grooming"
      duration_minutes = 90
      sticker_id = "scissors"
      default_cadence = "quarterly"
      tags = ["household", "pet"]

      [[entry]]
      id = "pet-food-restock"
      title = "pet-food restock"
      neutral_title = "Restock pet food"
      duration_minutes = 15
      sticker_id = "pet-food-bag"
      default_cadence = "monthly"
      tags = ["household", "pet", "nonSuperseable"]
      ```
- [ ] **HV-A.13** Indoor plant care. LOCKED:
      ```toml
      [[entry]]
      id = "plant-water-indoor"
      title = "water indoor plants"
      neutral_title = "Water indoor plants"
      duration_minutes = 5
      sticker_id = "watering-can"
      default_cadence = "twice-weekly"
      tags = ["household"]

      [[entry]]
      id = "plant-mist"
      title = "mist humid-lovers"
      neutral_title = "Mist humidity-loving plants"
      duration_minutes = 2
      sticker_id = "mist"
      default_cadence = "daily"
      tags = ["household"]

      [[entry]]
      id = "plant-rotate"
      title = "rotate plants toward sun"
      neutral_title = "Rotate plants toward sun"
      duration_minutes = 2
      sticker_id = "sun-rotate"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "plant-prune-indoor"
      title = "prune dead leaves"
      neutral_title = "Prune dead leaves"
      duration_minutes = 5
      sticker_id = "leaf"
      default_cadence = "weekly"
      tags = ["household"]

      [[entry]]
      id = "plant-repot-indoor"
      title = "repot indoor plant"
      neutral_title = "Repot indoor plant"
      duration_minutes = 20
      sticker_id = "pot"
      default_cadence = "annual"
      tags = ["household"]
      ```
- [ ] **HV-A.14** Get-dressed-for-work flow. The user explicitly
      called this out as needing detail. LOCKED:
      ```toml
      [[entry]]
      id = "dress-for-work"
      title = "good boy gets dressed for work"
      neutral_title = "Get dressed for work"
      duration_minutes = 10
      sticker_id = "shirt-tie"
      default_cadence = "weekday-mornings"
      default_times = ["07:30"]
      tags = ["household"]
      [[entry.subbeat]]
      label = "Underwear"
      duration_seconds = 30
      sticker_id = "undies"
      [[entry.subbeat]]
      label = "Socks"
      duration_seconds = 30
      sticker_id = "sock"
      [[entry.subbeat]]
      label = "Shirt"
      duration_seconds = 60
      sticker_id = "shirt"
      [[entry.subbeat]]
      label = "Pants"
      duration_seconds = 60
      sticker_id = "pants"
      [[entry.subbeat]]
      label = "Belt"
      duration_seconds = 30
      sticker_id = "belt"
      [[entry.subbeat]]
      label = "Shoes"
      duration_seconds = 60
      sticker_id = "shoe"
      [[entry.subbeat]]
      label = "Watch"
      duration_seconds = 15
      sticker_id = "watch"
      [[entry.subbeat]]
      label = "Wallet in pocket"
      duration_seconds = 15
      sticker_id = "wallet"
      [[entry.subbeat]]
      label = "Keys in pocket"
      duration_seconds = 15
      sticker_id = "keys"
      [[entry.subbeat]]
      label = "Phone in pocket"
      duration_seconds = 15
      sticker_id = "phone"
      [[entry.subbeat]]
      label = "Bag packed + on shoulder"
      duration_seconds = 30
      sticker_id = "bag"
      [[entry.subbeat]]
      label = "Coat (season-conditional)"
      duration_seconds = 30
      sticker_id = "coat"
      [[entry.subbeat]]
      label = "Mask if needed"
      duration_seconds = 15
      sticker_id = "mask"
      ```
- [ ] **HV-A.15** Leave-house check (its own atomic, immediately
      after `dress-for-work` and before `leave-for-work`). LOCKED:
      ```toml
      [[entry]]
      id = "leave-house-check"
      title = "leave-house check"
      neutral_title = "Leave-house checklist"
      duration_minutes = 2
      sticker_id = "checklist"
      default_cadence = "before-leaving"
      tags = ["household"]
      [[entry.subbeat]]
      label = "Stove off"
      duration_seconds = 10
      sticker_id = "stove-off"
      [[entry.subbeat]]
      label = "Lights off"
      duration_seconds = 10
      sticker_id = "lights-off"
      [[entry.subbeat]]
      label = "Windows shut"
      duration_seconds = 15
      sticker_id = "window-shut"
      [[entry.subbeat]]
      label = "Door locked"
      duration_seconds = 10
      sticker_id = "lock"
      [[entry.subbeat]]
      label = "Keys in pocket"
      duration_seconds = 5
      sticker_id = "keys"
      [[entry.subbeat]]
      label = "Wallet in pocket"
      duration_seconds = 5
      sticker_id = "wallet"
      [[entry.subbeat]]
      label = "Phone in pocket"
      duration_seconds = 5
      sticker_id = "phone"
      [[entry.subbeat]]
      label = "Charger if-trip"
      duration_seconds = 10
      sticker_id = "charger"
      ```
- [ ] **HV-A.16** Weekly reset (Sunday ~2hr container; the wizard
      may auto-aggregate other entries into this block). LOCKED:
      ```toml
      [[entry]]
      id = "weekly-reset"
      title = "good boy's weekly reset"
      neutral_title = "Weekly reset"
      duration_minutes = 120
      sticker_id = "reset"
      default_cadence = "weekly"
      default_weekday = "sun"
      default_times = ["16:00"]
      tags = ["household"]
      [[entry.subbeat]]
      label = "Laundry cycle (start)"
      duration_seconds = 600
      [[entry.subbeat]]
      label = "Groceries (or order)"
      duration_seconds = 1800
      [[entry.subbeat]]
      label = "Meal-prep for the week"
      duration_seconds = 2400
      [[entry.subbeat]]
      label = "Week-ahead glance (calendar review)"
      duration_seconds = 600
      [[entry.subbeat]]
      label = "Mailbox + paperwork sweep"
      duration_seconds = 600
      [[entry.subbeat]]
      label = "Plant water"
      duration_seconds = 300
      [[entry.subbeat]]
      label = "Bed-sheet change"
      duration_seconds = 600
      ```
- [ ] **HV-A.17** Kink-coded cute variants. Add `kink_variant_of`
      cross-references for users with K-mode ON. LOCKED (subset; the
      base entries already lean cute, these are *additional* coded
      labels that swap title at render time):
      ```toml
      [[variant]]
      kink_variant_of = "bed-make"
      title = "good boy makes Sir's bed"
      tags = ["kink"]

      [[variant]]
      kink_variant_of = "living-vacuum"
      title = "good boy vacuums his cage-area"
      tags = ["kink"]

      [[variant]]
      kink_variant_of = "kitchen-empty-trash"
      title = "good boy empties the bin like he was told"
      tags = ["kink"]

      [[variant]]
      kink_variant_of = "bath-toilet"
      title = "good boy scrubs the toilet for his keyholder"
      tags = ["kink"]
      ```
      Variants are additive only — neutral-mode renders the base
      entry's `neutral_title`; K-mode renders the variant if present,
      else the base `title`.
- [ ] **HV-A.18** Validator: each entry's sub-beat duration sum ≤
      atomic `duration_minutes`. Same rule as AT-D.4.

Entry count summary: ~88 base atomic entries across 13 categories
(trash 10, laundry 8, kitchen 19, bathroom 10, bedroom 7, living 11,
entry/mudroom 4, outdoor 7, mail 5, pantry 6, pet 9, plant 5, dress-
flow 1 with 13 sub-beats, leave-check 1 with 8 sub-beats, weekly-reset
1 with 7 sub-beats) + 4+ kink variants.

---

## Phase HV-B — Travel-prep atomic template (parameterized by trip)

New template file `templates/atomic-travel-prep.toml`. Unlike HV-A,
this template is *back-filled from a trip start date* by the wizard
(HV-F). Each entry has a `lead_offset_days` instead of a recurrence;
the wizard computes `trip.start_date - lead_offset_days` and
materializes a one-off event for that date on the trip overlay
calendar.

- [ ] **HV-B.1** Path: `templates/atomic-travel-prep.toml`. Schema:
      ```toml
      schema_version = 1
      template_id = "atomic-travel-prep"
      display_name = "Travel prep"
      category = "travel"
      neutral_safe = true
      parameterized = true   # back-filled from trip dates
      ```
- [ ] **HV-B.2** Document prep (T-42 days). LOCKED:
      ```toml
      [[entry]]
      id = "passport-validity"
      title = "check passport validity"
      neutral_title = "Check passport validity (6+ months past return)"
      lead_offset_days = 42
      duration_minutes = 10
      sticker_id = "passport"
      tags = ["travel", "documents"]

      [[entry]]
      id = "passport-renew-trigger"
      title = "renew passport (if needed)"
      neutral_title = "Start passport renewal (only if expiring soon)"
      lead_offset_days = 42
      duration_minutes = 120
      sticker_id = "passport-renew"
      conditional = "passport.expires_within_6_months_of_return"
      tags = ["travel", "documents"]

      [[entry]]
      id = "visa-check"
      title = "check visa requirements"
      neutral_title = "Check visa requirements for destination"
      lead_offset_days = 42
      duration_minutes = 15
      sticker_id = "visa"
      tags = ["travel", "documents"]

      [[entry]]
      id = "visa-apply"
      title = "apply for visa"
      neutral_title = "Apply for visa (if required)"
      lead_offset_days = 42
      duration_minutes = 60
      sticker_id = "visa-stamp"
      conditional = "visa.required"
      tags = ["travel", "documents"]

      [[entry]]
      id = "vaccination-check"
      title = "vaccination check"
      neutral_title = "Check destination vaccination requirements"
      lead_offset_days = 42
      duration_minutes = 15
      sticker_id = "vaccine"
      tags = ["travel", "health"]

      [[entry]]
      id = "travel-insurance"
      title = "buy travel insurance"
      neutral_title = "Purchase travel insurance"
      lead_offset_days = 35
      duration_minutes = 30
      sticker_id = "insurance"
      tags = ["travel", "documents"]

      [[entry]]
      id = "idp"
      title = "international driving permit"
      neutral_title = "Get international driving permit (if driving)"
      lead_offset_days = 35
      duration_minutes = 45
      sticker_id = "driver-license"
      conditional = "trip.will_drive"
      tags = ["travel", "documents"]
      ```
- [ ] **HV-B.3** Logistics prep (T-28 days). LOCKED:
      ```toml
      [[entry]]
      id = "book-flights"
      title = "book flights"
      neutral_title = "Book flights"
      lead_offset_days = 28
      duration_minutes = 45
      sticker_id = "plane-ticket"
      conditional = "trip.mode == 'flight'"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "book-accommodation"
      title = "book accommodation"
      neutral_title = "Book accommodation"
      lead_offset_days = 28
      duration_minutes = 30
      sticker_id = "hotel"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "book-ground-transport"
      title = "book ground transport"
      neutral_title = "Book ground transport at destination"
      lead_offset_days = 21
      duration_minutes = 20
      sticker_id = "car"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "book-activities"
      title = "book activities / tours"
      neutral_title = "Book activities and tours"
      lead_offset_days = 21
      duration_minutes = 30
      sticker_id = "ticket"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "restaurant-reservations"
      title = "restaurant reservations"
      neutral_title = "Make restaurant reservations"
      lead_offset_days = 14
      duration_minutes = 20
      sticker_id = "fork-knife"
      tags = ["travel", "logistics"]
      ```
- [ ] **HV-B.4** Money prep (T-14 days). LOCKED:
      ```toml
      [[entry]]
      id = "currency-exchange"
      title = "order foreign cash"
      neutral_title = "Exchange currency / order foreign cash"
      lead_offset_days = 14
      duration_minutes = 20
      sticker_id = "cash"
      tags = ["travel", "money"]

      [[entry]]
      id = "bank-notify"
      title = "notify bank of travel"
      neutral_title = "Notify bank of travel dates"
      lead_offset_days = 14
      duration_minutes = 10
      sticker_id = "bank"
      tags = ["travel", "money"]

      [[entry]]
      id = "card-test"
      title = "verify cards work abroad"
      neutral_title = "Verify debit/credit cards work abroad"
      lead_offset_days = 14
      duration_minutes = 10
      sticker_id = "card"
      tags = ["travel", "money"]

      [[entry]]
      id = "sim-or-roaming"
      title = "travel SIM / roaming plan"
      neutral_title = "Buy travel SIM or enable roaming plan"
      lead_offset_days = 14
      duration_minutes = 15
      sticker_id = "sim"
      tags = ["travel", "money"]
      ```
- [ ] **HV-B.5** Health prep (T-7 days). LOCKED:
      ```toml
      [[entry]]
      id = "med-refill"
      title = "refill meds for trip"
      neutral_title = "Refill prescriptions (trip + buffer)"
      lead_offset_days = 7
      duration_minutes = 30
      sticker_id = "pill"
      tags = ["travel", "health", "nonSuperseable"]

      [[entry]]
      id = "first-aid-kit"
      title = "pack first-aid kit"
      neutral_title = "Assemble first-aid kit"
      lead_offset_days = 7
      duration_minutes = 20
      sticker_id = "first-aid"
      tags = ["travel", "health"]

      [[entry]]
      id = "rx-paperwork"
      title = "prescription paperwork for customs"
      neutral_title = "Prepare prescription paperwork for customs"
      lead_offset_days = 7
      duration_minutes = 15
      sticker_id = "paperwork"
      conditional = "trip.crosses_border"
      tags = ["travel", "health"]

      [[entry]]
      id = "vaccine-booster"
      title = "vaccine booster"
      neutral_title = "Get vaccine booster (if required)"
      lead_offset_days = 7
      duration_minutes = 45
      sticker_id = "vaccine"
      conditional = "destination.requires_vaccine"
      tags = ["travel", "health"]

      [[entry]]
      id = "sunscreen-pack"
      title = "buy sunscreen"
      neutral_title = "Buy sunscreen"
      lead_offset_days = 7
      duration_minutes = 10
      sticker_id = "sunscreen"
      conditional = "destination.sunny"
      tags = ["travel", "health"]

      [[entry]]
      id = "bug-spray-pack"
      title = "buy bug spray"
      neutral_title = "Buy bug repellent"
      lead_offset_days = 7
      duration_minutes = 10
      sticker_id = "bug"
      conditional = "destination.buggy"
      tags = ["travel", "health"]
      ```
- [ ] **HV-B.6** Logistics confirm (T-3 days). LOCKED:
      ```toml
      [[entry]]
      id = "boarding-passes"
      title = "print/download boarding passes"
      neutral_title = "Download / print boarding passes"
      lead_offset_days = 3
      duration_minutes = 10
      sticker_id = "boarding-pass"
      conditional = "trip.mode == 'flight'"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "online-checkin"
      title = "online check-in (24h window)"
      neutral_title = "Online check-in"
      lead_offset_days = 1
      duration_minutes = 10
      sticker_id = "checkin"
      conditional = "trip.mode == 'flight'"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "screenshot-accommodation"
      title = "screenshot accommodation address"
      neutral_title = "Screenshot accommodation address + offline"
      lead_offset_days = 3
      duration_minutes = 5
      sticker_id = "screenshot"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "offline-maps"
      title = "download offline maps"
      neutral_title = "Download offline maps for destination"
      lead_offset_days = 3
      duration_minutes = 10
      sticker_id = "map"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "entertainment-download"
      title = "download travel entertainment"
      neutral_title = "Download podcasts / shows / books"
      lead_offset_days = 3
      duration_minutes = 20
      sticker_id = "headphones"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "power-bank-charge"
      title = "charge power bank"
      neutral_title = "Charge power bank fully"
      lead_offset_days = 2
      duration_minutes = 5
      sticker_id = "power-bank"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "weather-check"
      title = "weather check"
      neutral_title = "Check destination weather forecast"
      lead_offset_days = 2
      duration_minutes = 5
      sticker_id = "weather"
      tags = ["travel", "logistics"]
      ```
- [ ] **HV-B.7** Pack (T-2 days). Multi-step with sub-beats; each
      pack-* is its own atomic so deviations are per-bucket. LOCKED:
      ```toml
      [[entry]]
      id = "pack-documents"
      title = "good boy packs his documents"
      neutral_title = "Pack travel documents"
      lead_offset_days = 2
      duration_minutes = 10
      sticker_id = "doc-folder"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Passport"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Tickets / boarding passes"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Accommodation booking printout"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Travel insurance card"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Emergency-contact card"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Visa / vaccination paperwork"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Driver's license / IDP"
      duration_seconds = 60

      [[entry]]
      id = "pack-toiletries"
      title = "good boy packs his toiletries"
      neutral_title = "Pack toiletries"
      lead_offset_days = 2
      duration_minutes = 15
      sticker_id = "toiletry-bag"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Toothbrush"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Toothpaste"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Floss"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Deodorant"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Sunscreen"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Razor + blades"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Shampoo (if skeptical of hotel)"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Contact lenses + solution"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Period products (if relevant)"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Condoms + lube (if relevant)"
      duration_seconds = 30

      [[entry]]
      id = "pack-medication"
      title = "good boy packs his meds"
      neutral_title = "Pack medication"
      lead_offset_days = 2
      duration_minutes = 10
      sticker_id = "pill-pack"
      tags = ["travel", "pack", "health", "nonSuperseable"]

      [[entry]]
      id = "pack-clothes"
      title = "good boy packs his clothes"
      neutral_title = "Pack clothes"
      lead_offset_days = 2
      duration_minutes = 30
      sticker_id = "suitcase-clothes"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Underwear (nights+2 count)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Socks (nights+2 count)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Shirts (nights count)"
      duration_seconds = 240
      [[entry.subbeat]]
      label = "Pants (nights/2 + 1)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Dresses (if relevant)"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Swimwear (if relevant)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Sleepwear"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Outerwear / layer"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Formal set (if relevant)"
      duration_seconds = 240

      [[entry]]
      id = "pack-shoes"
      title = "good boy packs his shoes"
      neutral_title = "Pack shoes"
      lead_offset_days = 2
      duration_minutes = 10
      sticker_id = "shoe-pair"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Walking shoes"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Formal shoes (if relevant)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Sandals (if relevant)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Slippers"
      duration_seconds = 120

      [[entry]]
      id = "pack-tech"
      title = "good boy packs his tech"
      neutral_title = "Pack electronics"
      lead_offset_days = 2
      duration_minutes = 15
      sticker_id = "laptop"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Phone charger"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Laptop + charger"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Adapters for destination"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Headphones"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Power bank"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Camera + charger + SD card"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Kindle / book"
      duration_seconds = 30

      [[entry]]
      id = "pack-misc"
      title = "good boy packs the misc kit"
      neutral_title = "Pack miscellaneous items"
      lead_offset_days = 2
      duration_minutes = 10
      sticker_id = "misc"
      tags = ["travel", "pack"]
      [[entry.subbeat]]
      label = "Sunglasses"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Hat"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Reusable water bottle (empty for security)"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Snacks"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Eye mask"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Neck pillow"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Earplugs"
      duration_seconds = 30

      [[entry]]
      id = "pack-kink-kit"
      title = "good boy packs his kit"
      neutral_title = "Pack personal items (private)"
      lead_offset_days = 2
      duration_minutes = 15
      sticker_id = "kit"
      conditional = "alignment != 'unaligned-private' && trip.pack_kink_kit"
      privacy_flag = "K-2"   # never surfaces on lockscreen
      tags = ["travel", "pack", "kink"]
      [[entry.subbeat]]
      label = "Cage"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Collar"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Plug + cleaning supplies"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Lube"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Toys (discreet bag)"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Restraints (soft only if crossing borders)"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Leash (discreet)"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Customs-awareness review for destination"
      duration_seconds = 60
      ```
- [ ] **HV-B.8** House-leave prep (T-1 day). LOCKED:
      ```toml
      [[entry]]
      id = "mail-pause"
      title = "pause the mail"
      neutral_title = "Pause mail delivery / arrange pickup"
      lead_offset_days = 1
      duration_minutes = 10
      sticker_id = "mailbox-paused"
      tags = ["travel", "house"]

      [[entry]]
      id = "plants-water-extra"
      title = "extra water for the plants"
      neutral_title = "Water plants thoroughly OR arrange sitter"
      lead_offset_days = 1
      duration_minutes = 15
      sticker_id = "watering-can"
      tags = ["travel", "house"]

      [[entry]]
      id = "trash-out-pre-trip"
      title = "take out all trash before leaving"
      neutral_title = "Take out trash before trip"
      lead_offset_days = 1
      duration_minutes = 10
      sticker_id = "trash-bag"
      tags = ["travel", "house"]

      [[entry]]
      id = "fridge-empty-perishables"
      title = "empty fridge perishables"
      neutral_title = "Empty perishables from fridge"
      lead_offset_days = 1
      duration_minutes = 10
      sticker_id = "fridge"
      tags = ["travel", "house"]

      [[entry]]
      id = "thermostat-set"
      title = "set the thermostat"
      neutral_title = "Set thermostat to away-mode"
      lead_offset_days = 1
      duration_minutes = 2
      sticker_id = "thermostat"
      tags = ["travel", "house"]

      [[entry]]
      id = "windows-lock"
      title = "lock all windows"
      neutral_title = "Lock all windows"
      lead_offset_days = 1
      duration_minutes = 5
      sticker_id = "window-shut"
      tags = ["travel", "house"]

      [[entry]]
      id = "away-mode-lights"
      title = "set away-mode lights"
      neutral_title = "Configure smart-light away-mode"
      lead_offset_days = 1
      duration_minutes = 5
      sticker_id = "lights"
      conditional = "house.has_smart_lights"
      tags = ["travel", "house"]

      [[entry]]
      id = "key-handoff"
      title = "hand keys to sitter"
      neutral_title = "Hand keys to sitter / neighbor"
      lead_offset_days = 1
      duration_minutes = 10
      sticker_id = "keys-handoff"
      conditional = "trip.has_sitter"
      tags = ["travel", "house"]

      [[entry]]
      id = "devices-charge"
      title = "charge all devices overnight"
      neutral_title = "Charge all devices"
      lead_offset_days = 1
      duration_minutes = 5
      sticker_id = "battery-full"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "alarm-set"
      title = "set the wake-up alarm"
      neutral_title = "Set wake-up alarm for departure"
      lead_offset_days = 1
      duration_minutes = 2
      sticker_id = "alarm"
      tags = ["travel", "logistics"]

      [[entry]]
      id = "travel-outfit-prep"
      title = "lay out travel outfit"
      neutral_title = "Lay out travel outfit"
      lead_offset_days = 1
      duration_minutes = 5
      sticker_id = "outfit"
      tags = ["travel", "pack"]
      ```
- [ ] **HV-B.9** Departure day (T-0). LOCKED:
      ```toml
      [[entry]]
      id = "departure-passport-check"
      title = "final passport check"
      neutral_title = "Final passport check"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "passport"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-ticket-check"
      title = "final ticket check"
      neutral_title = "Final ticket / booking check"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "ticket"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-breakfast"
      title = "light breakfast"
      neutral_title = "Light breakfast"
      lead_offset_days = 0
      duration_minutes = 15
      sticker_id = "breakfast"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-water-bottle"
      title = "empty water bottle (for security)"
      neutral_title = "Empty water bottle before security"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "bottle-empty"
      conditional = "trip.mode == 'flight'"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-outfit-on"
      title = "travel outfit on"
      neutral_title = "Put on travel outfit"
      lead_offset_days = 0
      duration_minutes = 5
      sticker_id = "outfit-on"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-last-bathroom"
      title = "last bathroom at home"
      neutral_title = "Last home bathroom"
      lead_offset_days = 0
      duration_minutes = 5
      sticker_id = "toilet"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-garbage-final"
      title = "final garbage out"
      neutral_title = "Take out final garbage"
      lead_offset_days = 0
      duration_minutes = 5
      sticker_id = "trash-bag"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-lights-off"
      title = "lights off"
      neutral_title = "Turn off all lights"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "lights-off"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-thermostat-off"
      title = "thermostat to away"
      neutral_title = "Set thermostat fully to away"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "thermostat"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-lock-door"
      title = "lock the door"
      neutral_title = "Lock front door"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "lock"
      tags = ["travel", "departure"]

      [[entry]]
      id = "departure-depart-airport"
      title = "depart for airport"
      neutral_title = "Depart for airport / station / dock"
      lead_offset_days = 0
      duration_minutes = 1
      sticker_id = "depart"
      buffer_minutes_destination_lookup = true   # wizard fills airport buffer
      tags = ["travel", "departure"]
      ```
- [ ] **HV-B.10** Validator: any entry with `conditional` skipped if
      the wizard's parameter expression evaluates false. Materializer
      writes a single comment-line in the calendar's commit body
      explaining which conditional entries were filtered.

Entry count summary: ~52 travel-prep entries across 7 lead-time tiers
(documents 7 / logistics 5 / money 4 / health 6 / confirm 7 / pack 7
parent atomics with 50+ sub-beats / house-leave 11 / departure 11).

---

## Phase HV-C — Flight-day atomic template (per-flight parameterized)

New template file `templates/atomic-flight-day.toml`. Parameterized
per flight: `flight_number`, `departure_airport`, `arrival_airport`,
`scheduled_departure`, `scheduled_arrival`. The wizard materializes
one full timeline per flight (handles multi-leg).

- [ ] **HV-C.1** Path: `templates/atomic-flight-day.toml`. Schema:
      ```toml
      schema_version = 1
      template_id = "atomic-flight-day"
      display_name = "Flight day"
      category = "travel"
      neutral_safe = true
      parameterized = true
      parameters = ["flight_number", "departure_airport",
                    "arrival_airport", "scheduled_departure",
                    "scheduled_arrival", "is_international"]
      ```
- [ ] **HV-C.2** Default pre-airport buffer = 180min international /
      120min domestic. User overridable per airport (wizard remembers
      per-IATA buffer overrides).
- [ ] **HV-C.3** Entries (relative offsets from `scheduled_departure`
      unless noted). LOCKED:
      ```toml
      [[entry]]
      id = "online-checkin-window"
      title = "online check-in (24h)"
      neutral_title = "Online check-in window opens"
      offset_minutes_from_departure = -1440
      duration_minutes = 10
      sticker_id = "checkin"
      tags = ["flight"]

      [[entry]]
      id = "flight-pre-alarm"
      title = "wake-up alarm"
      neutral_title = "Wake-up alarm (flight day)"
      offset_minutes_from_departure = "-(buffer + transport + 60)"
      duration_minutes = 1
      sticker_id = "alarm"
      tags = ["flight"]

      [[entry]]
      id = "transport-to-airport"
      title = "transport to airport"
      neutral_title = "Travel to airport"
      offset_minutes_from_departure = "-(buffer + transport)"
      duration_minutes_var = "transport_minutes"
      sticker_id = "taxi"
      tags = ["flight"]

      [[entry]]
      id = "airport-arrive"
      title = "arrive at airport"
      neutral_title = "Arrive at airport"
      offset_minutes_from_departure = "-buffer"
      duration_minutes = 5
      sticker_id = "airport"
      tags = ["flight"]

      [[entry]]
      id = "bag-drop"
      title = "bag drop / kiosk check-in"
      neutral_title = "Bag drop / kiosk check-in"
      offset_minutes_from_departure = "-buffer + 10"
      duration_minutes = 20
      sticker_id = "bag-drop"
      tags = ["flight"]

      [[entry]]
      id = "security"
      title = "security"
      neutral_title = "Security screening"
      offset_minutes_from_departure = "-buffer + 30"
      duration_minutes = 30
      sticker_id = "security"
      tags = ["flight"]

      [[entry]]
      id = "gate-find"
      title = "find the gate"
      neutral_title = "Locate gate"
      offset_minutes_from_departure = "-buffer + 60"
      duration_minutes = 10
      sticker_id = "gate"
      tags = ["flight"]

      [[entry]]
      id = "pre-board-toilet"
      title = "last bathroom before boarding"
      neutral_title = "Pre-board bathroom"
      offset_minutes_from_departure = -50
      duration_minutes = 5
      sticker_id = "toilet"
      tags = ["flight"]

      [[entry]]
      id = "pre-board-water-refill"
      title = "refill water bottle past security"
      neutral_title = "Refill water bottle (past security)"
      offset_minutes_from_departure = -45
      duration_minutes = 5
      sticker_id = "water-fountain"
      tags = ["flight"]

      [[entry]]
      id = "pre-board-food"
      title = "last food"
      neutral_title = "Last food before boarding"
      offset_minutes_from_departure = -40
      duration_minutes = 15
      sticker_id = "snack"
      tags = ["flight"]

      [[entry]]
      id = "pre-board-call"
      title = "last call home"
      neutral_title = "Last call home"
      offset_minutes_from_departure = -30
      duration_minutes = 5
      sticker_id = "phone-call"
      tags = ["flight"]

      [[entry]]
      id = "boarding"
      title = "boarding"
      neutral_title = "Boarding"
      offset_minutes_from_departure = -30
      duration_minutes = 25
      sticker_id = "boarding"
      tags = ["flight"]

      [[entry]]
      id = "in-flight"
      title = "in-flight (sleep / movie / work)"
      neutral_title = "In flight"
      offset_minutes_from_departure = 0
      duration_minutes_var = "flight_duration_minutes"
      sticker_id = "plane"
      tags = ["flight"]

      [[entry]]
      id = "landing"
      title = "landing"
      neutral_title = "Landing"
      offset_minutes_from_arrival = -15
      duration_minutes = 15
      sticker_id = "plane-land"
      tags = ["flight"]

      [[entry]]
      id = "immigration"
      title = "immigration / customs"
      neutral_title = "Immigration / customs"
      offset_minutes_from_arrival = 10
      duration_minutes = 30
      sticker_id = "passport-stamp"
      conditional = "is_international"
      tags = ["flight"]

      [[entry]]
      id = "baggage-claim"
      title = "baggage claim"
      neutral_title = "Baggage claim"
      offset_minutes_from_arrival = 30
      duration_minutes = 25
      sticker_id = "luggage"
      tags = ["flight"]

      [[entry]]
      id = "ground-to-accommodation"
      title = "ground transport to accommodation"
      neutral_title = "Ground transport to accommodation"
      offset_minutes_from_arrival = 60
      duration_minutes_var = "ground_transport_minutes"
      sticker_id = "taxi"
      tags = ["flight"]

      [[entry]]
      id = "accommodation-checkin"
      title = "accommodation check-in"
      neutral_title = "Check in at accommodation"
      offset_minutes_from_arrival = "60 + ground_transport_minutes"
      duration_minutes = 20
      sticker_id = "hotel-checkin"
      tags = ["flight"]

      [[entry]]
      id = "first-day-decompress"
      title = "decompress / unpack"
      neutral_title = "Decompress, unpack, water, snack"
      offset_minutes_from_arrival = "120 + ground_transport_minutes"
      duration_minutes = 60
      sticker_id = "decompress"
      tags = ["flight"]
      ```
- [ ] **HV-C.4** Multi-leg: the wizard generates one full timeline
      per leg; layover-buffer between legs is treated as an
      "in-flight" continuation entry rather than ground-transport.
- [ ] **HV-C.5** Train / car / boat sibling templates can reuse this
      same schema with different IDs in a future phase. Locked-out
      for now: this phase covers flights only. Other modes fall back
      to "user adds events manually" for v1.

Entry count: 19 flight-day entries, parameterized.

---

## Phase HV-D — Vacation-daily atomic template

New template file `templates/atomic-vacation-daily.toml`. The
slob-drift defense.

- [ ] **HV-D.1** Path: `templates/atomic-vacation-daily.toml`.
      Header (philosophy comment is REQUIRED in the file):
      ```toml
      # vacation = lazy-slob risk zone. This template's job is to
      # prevent slob-drift without imposing the full home routine.
      # the good-boy stays a good-boy by doing the bare-essentials
      # on schedule. routine calendars are superseded for the trip
      # window; THIS template runs in their place.

      schema_version = 1
      template_id = "atomic-vacation-daily"
      display_name = "Vacation daily"
      category = "self-care"
      neutral_safe = true
      ```
- [ ] **HV-D.2** Self-care anchors (relaxed cadence but mandatory).
      LOCKED:
      ```toml
      [[entry]]
      id = "vacation-wake"
      title = "good boy wakes up"
      neutral_title = "Wake up (vacation cadence)"
      duration_minutes = 1
      sticker_id = "sunrise"
      default_cadence = "daily"
      default_times = ["09:00"]   # later than routine
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-brush-teeth-am"
      title = "good boy brushes his teeth (morning)"
      neutral_title = "Brush teeth (morning)"
      duration_minutes = 5
      sticker_id = "tooth"
      default_cadence = "daily"
      default_times = ["09:15"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-brush-teeth-pm"
      title = "good boy brushes his teeth (night)"
      neutral_title = "Brush teeth (night)"
      duration_minutes = 5
      sticker_id = "tooth"
      default_cadence = "daily"
      default_times = ["23:30"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-shower"
      title = "good boy showers"
      neutral_title = "Shower"
      duration_minutes = 10
      sticker_id = "shower"
      default_cadence = "configurable"   # default daily; user may relax to every-other-day in wizard
      default_times = ["09:30"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-grooming"
      title = "basic grooming"
      neutral_title = "Basic grooming (deodorant, hair, sunscreen)"
      duration_minutes = 5
      sticker_id = "comb"
      default_cadence = "daily"
      default_times = ["09:50"]
      tags = ["self-care", "vacation"]
      [[entry.subbeat]]
      label = "Deodorant"
      duration_seconds = 30
      [[entry.subbeat]]
      label = "Hair"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Sunscreen (if beach/sunny)"
      duration_seconds = 90
      conditional = "destination.sunny"
      [[entry.subbeat]]
      label = "Skincare quick"
      duration_seconds = 60

      [[entry]]
      id = "vacation-hydrate-1"
      title = "hydrate"
      neutral_title = "Hydration check (mid-morning)"
      duration_minutes = 1
      sticker_id = "water-glass"
      default_cadence = "daily"
      default_times = ["11:00"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-hydrate-2"
      title = "hydrate"
      neutral_title = "Hydration check (afternoon)"
      duration_minutes = 1
      sticker_id = "water-glass"
      default_cadence = "daily"
      default_times = ["15:00"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-hydrate-3"
      title = "hydrate"
      neutral_title = "Hydration check (evening)"
      duration_minutes = 1
      sticker_id = "water-glass"
      default_cadence = "daily"
      default_times = ["19:00"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-meal-anchor"
      title = "the one scheduled meal"
      neutral_title = "Scheduled meal anchor"
      duration_minutes = 30
      sticker_id = "plate"
      default_cadence = "daily"
      default_times = ["13:00"]   # user picks one anchor in wizard
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-movement"
      title = "movement anchor"
      neutral_title = "Walk 20m or stretch 10m"
      duration_minutes = 20
      sticker_id = "walking"
      default_cadence = "daily"
      default_times = ["10:30"]
      tags = ["self-care", "vacation"]

      [[entry]]
      id = "vacation-wind-down"
      title = "evening wind-down"
      neutral_title = "Evening wind-down"
      duration_minutes = 15
      sticker_id = "moon"
      default_cadence = "daily"
      default_times = ["23:00"]
      tags = ["self-care", "vacation"]
      [[entry.subbeat]]
      label = "Skincare"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Tomorrow-glance"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Phone down"
      duration_seconds = 60
      ```
- [ ] **HV-D.3** Kink-care anchors (relaxed to once/day instead of
      3×). Gated on `alignment != 'unaligned-private'`. LOCKED:
      ```toml
      [[entry]]
      id = "vacation-cage-check"
      title = "cage check"
      neutral_title = "Cage check (vacation cadence)"
      duration_minutes = 2
      sticker_id = "cage"
      default_cadence = "daily"
      default_times = ["09:30"]
      conditional = "alignment != 'unaligned-private'"
      privacy_flag = "K-2"
      tags = ["self-care", "vacation", "kink"]

      [[entry]]
      id = "vacation-plug-check"
      title = "plug check"
      neutral_title = "Plug check (vacation cadence)"
      duration_minutes = 5
      sticker_id = "plug"
      default_cadence = "daily"
      default_times = ["21:00"]
      conditional = "alignment != 'unaligned-private' && user.uses_plug_on_vacation"
      privacy_flag = "K-2"
      tags = ["self-care", "vacation", "kink"]

      [[entry]]
      id = "vacation-posture-check"
      title = "posture check"
      neutral_title = "Posture check (vacation cadence)"
      duration_minutes = 1
      sticker_id = "posture"
      default_cadence = "daily"
      default_times = ["14:00"]
      conditional = "alignment != 'unaligned-private'"
      privacy_flag = "K-2"
      tags = ["self-care", "vacation", "kink"]

      [[entry]]
      id = "vacation-collar-check"
      title = "collar check"
      neutral_title = "Collar check (vacation cadence)"
      duration_minutes = 1
      sticker_id = "collar"
      default_cadence = "daily"
      default_times = ["09:30"]
      conditional = "alignment != 'unaligned-private'"
      privacy_flag = "K-2"
      tags = ["self-care", "vacation", "kink"]
      ```
- [ ] **HV-D.4** All entries on this template carry an implicit
      `vacation_overlay = true` flag set at materialization time; the
      resolver uses it to (a) render with a tiny "vacation-mode" leaf
      glyph in the now-card, and (b) ignore the inverted-default
      streak counter from Phase XX so a missed `vacation-movement`
      doesn't shame the user.

Entry count: 11 self-care anchors + 4 kink anchors = 15 vacation-daily
entries with sub-beats on grooming and wind-down.

---

## Phase HV-E — Calendar supersedence mechanic

Extends Phase E (`resolver.md` RV-A..F) and Phase data-model with a
new calendar-to-calendar temporal relationship. LOCKED throughout.

- [ ] **HV-E.1** Data-model — calendar-level frontmatter. Add to
      `calendars/<id>/calendar.toml`:
      ```toml
      # supersedence: this calendar pauses other calendars during
      # the listed date ranges. empty `supersedes` = pauses nothing.
      # empty `superseded_during` but non-empty `supersedes` =
      # "supersedes whenever this calendar itself is active" (rare;
      # typical case fills `superseded_during`).
      supersedes = ["cal-work-12ab", "cal-routine-77ef"]
      superseded_during = [
        { from = "2026-06-12", to = "2026-06-26" },
      ]

      # critical calendars opt out of being superseded
      nonSuperseable = false
      ```
- [ ] **HV-E.2** Resolver — new pass. Insert after RV-D's overlay-
      priority pass, before final render:
      ```
      for each candidate event E on date D from calendar Y:
        if any active calendar X exists where:
            X.supersedes contains Y.id
            AND (X.superseded_during is empty OR
                 some range in X.superseded_during covers D)
            AND Y.nonSuperseable == false
            AND E.tags does not contain "nonSuperseable"
        then:
          mark E as hidden-by-supersedence(X)
      ```
      If multiple X candidates supersede the same Y on D, the
      highest-priority X (per existing TT-priority) wins as the
      attribution; the hide-result is the same. LOCKED.
- [ ] **HV-E.3** UI surfaces:
      - **Schedule / now-card**: hidden events do not render.
      - **Manage overlays screen**: hidden events render strikethrough
        + greyed with a hover/tap tooltip "paused by <X.title> until
        <range.to>". User can tap → "show this one anyway" toggle
        which writes a per-event override file at
        `overrides/<cal-y-id>/<event-id-or-rule-id>/<yyyy-mm-dd>.md`
        with `kind = "force-show"`.
      - **Week / month view**: hidden events suppressed; a small
        leaf-glyph on the date indicates a vacation-overlay is
        active for that date (tap → drill-in shows what's paused).
- [ ] **HV-E.4** Per-event opt-out (wizard's "but keep these on"):
      written to the same `overrides/` directory as HV-E.3, but with
      `kind = "force-show-for-range"` and a date-range. The wizard
      bulk-writes these for medication / vet / etc.
- [ ] **HV-E.5** Cache: Room table `event_visibility(date, event_id,
      hidden_by_calendar_id NULLABLE)`. Invalidated when (a) any
      calendar's `supersedes` / `superseded_during` changes, (b) any
      `nonSuperseable` flag flips, (c) any `overrides/` file
      added/removed.
- [ ] **HV-E.6** New invariants on resolver. LOCKED:
      - **S1** an event can be hidden by AT MOST one calendar (TT-
        priority tiebreak attributes; render is the same).
      - **S2** supersedence does NOT cascade — if X supersedes Y, and
        Y supersedes Z, X does not transitively supersede Z. The
        resolver checks only direct relationships.
      - **S3** a calendar cannot supersede itself. Validator rejects.
      - **S4** calendars with `nonSuperseable = true` ignore all
        supersedence requests; the resolver emits a debug-log line
        when an attempted hide is rejected.
      - **S5** events carrying the tag `nonSuperseable` (e.g. all
        medication entries from HV-B and pet entries from HV-A.12)
        survive supersedence even if their parent calendar does not
        have `nonSuperseable = true`. Allows mixing critical events
        into otherwise-superseable calendars.
- [ ] **HV-E.7** Proposed new locked decisions (numbering for
      integration agent; tentative IDs prefixed `D-NEXT.*`):
      - **D-NEXT.A** Supersedence is a temporal overlay relationship,
        not deletion. Superseded events are hidden in schedule views
        but remain visible + togglable in the manage-overlays UI.
      - **D-NEXT.B** Supersedence is non-cascading and direct-only
        (invariant S2). Trade-off: simpler reasoning, at the cost of
        users having to list every paused calendar explicitly.
      - **D-NEXT.C** `nonSuperseable` is a property of both calendars
        AND individual events (via tag). Medication, pet-care, vet
        appointments default to `nonSuperseable`. Locks in the
        promise: vacation never silently skips your meds.
      - **D-NEXT.D** Per-event opt-out lives at
        `overrides/<cal-id>/<event-id>/<yyyy-mm-dd>.md` — a NEW
        directory parallel to `exceptions/` and `deviations/`.
        Justification: `exceptions/` means *cancel*, `deviations/`
        means *reported-not-done*, `overrides/` means *force-render-
        despite-supersedence*. Three distinct semantics, three
        directories.
- [ ] **HV-E.8** CLI surface (extends Phase CLI-tooling):
      - `skb supersedence add --calendar <X> --supersedes <Y> [--from <date>] [--to <date>]`
      - `skb supersedence remove --calendar <X> --supersedes <Y>`
      - `skb override force-show --calendar <Y> --event <id> --date <yyyy-mm-dd>`
- [ ] **HV-E.9** Tests:
      - Resolver: every invariant S1..S5 with red/green fixtures.
      - Resolver: priority tiebreak when two vacation overlays both
        supersede `cal-work` on the same date.
      - Per-event override survives supersedence.
      - `nonSuperseable` calendars ignore supersedence.
      - `nonSuperseable` event-tag survives even inside a
        superseable calendar.

---

## Phase HV-F — Quick-vacation-wizard (in-app, multi-screen)

In-app Compose flow. Distinct from Phase K (first-launch wizard). Lives
behind two entry-points (HV-G). Re-runnable to edit a trip in flight.

- [ ] **HV-F.1** Compose nav-graph: 6 screens + a confirm modal. State
      stored in a `TripDraft` data class backed by Room until commit;
      on commit, materializes calendar files + commits via JGit.
- [ ] **HV-F.2** **Screen 1 — Trip basics**:
      - Field: trip name (e.g. "Sicily 2026").
      - Field: start date (date-picker, default = next Saturday).
      - Field: end date (date-picker, must be ≥ start).
      - Field: destination (free text + optional autocomplete via an
        OFFLINE bundled list of countries + capitals; no network
        lookup, per app's offline-first stance).
      - Field: travel mode (radio: flight / train / car / boat / none).
      - Bat-mascot sticker: `trip-suitcase-waving` (HV-H).
- [ ] **HV-F.3** **Screen 2 — Travel-prep cadence**:
      - Shows back-fill preview: list of HV-B entries with computed
        absolute dates ("passport check on Apr 30, packing on Jun 10
        …").
      - Each row has an inline toggle (off = skip this entry) and a
        tap-to-edit lead-offset.
      - Conditional entries grayed out unless the wizard's parameter
        evaluates true (e.g. visa-apply only if visa-check is on AND
        user toggled `visa.required`).
      - Bat-mascot sticker: `flight-paw-prints`.
- [ ] **HV-F.4** **Screen 3 — Flight details (conditional)**:
      - Shown only if Screen 1's mode = flight.
      - Multi-leg list: add-leg button; each leg has `flight_number`,
        `departure_airport` (IATA), `arrival_airport`, `scheduled_
        departure`, `scheduled_arrival`, `is_international` toggle.
      - Per-leg pre-airport buffer override field (default 180min
        international / 120min domestic).
      - Bat-mascot sticker: `flight-paw-prints`.
- [ ] **HV-F.5** **Screen 4 — Vacation-daily anchors**:
      - List from HV-D.2 + HV-D.3 (latter only if alignment ≠
        unaligned-private).
      - Each anchor has a toggle (defaults ON) and a relax-cadence
        button (e.g. shower: daily → every-other-day).
      - "Pick your meal anchor" radio: breakfast / lunch / dinner
        (defaults dinner).
      - "Pack kink-kit?" toggle (gates HV-B.7's `pack-kink-kit`
        entry).
      - Bat-mascot sticker: `beach-loungin-with-cage-still-on`.
- [ ] **HV-F.6** **Screen 5 — Supersedence picker**:
      - Lists ALL active calendars with toggle "pause during trip".
      - Defaults SUPERSEDED: any calendar tagged `work`, `university`,
        `kink-routine`, `weekly-reset`.
      - Defaults NOT SUPERSEDED: any calendar tagged `medication`,
        `health`, `pet-care`, or carrying `nonSuperseable = true`
        (those are visually locked-on with an explanatory chip
        "critical — never paused").
      - For each NOT-defaulted-superseded calendar, a secondary
        "but pause anyway" toggle is available (user override).
      - For each superseded calendar, an expandable "but keep these
        events on" list lets the user write per-event overrides
        (HV-E.4) — e.g. "pause work, but keep my Tuesday vet
        appointment".
      - Bat-mascot sticker: `supersedence-snooze-toggle`.
- [ ] **HV-F.7** **Screen 6 — Confirm**:
      - Calendar preview: renders the new vacation overlay on a
        mini-month view, showing back-filled prep events leading into
        the trip window and the daily anchors during.
      - Greyed-strikethrough preview of superseded events for
        transparency.
      - "Re-edit" button → jumps back to whichever screen the user
        taps a section of.
      - "Confirm and materialize" button → commits.
      - Bat-mascot sticker: `confirm-tail-flick` + `good-boy-stays-
        good-boy-on-vacation` reassurance text bubble.
- [ ] **HV-F.8** Materialization on confirm:
      - Creates a new calendar `calendars/cal-trip-<uuidv7>/` with
        `calendar.toml` containing the `supersedes` + `superseded_
        during` arrays from Screen 5.
      - Materializes HV-B prep events as one-off events under
        `calendars/cal-trip-<uuidv7>/events/<yyyy>/<mm>/<eid>.md`,
        computed dates from lead-offsets.
      - Materializes HV-C flight events under same calendar, per leg.
      - Materializes HV-D daily anchors as a recurring rule under
        `calendars/cal-trip-<uuidv7>/rules/<rid>.md` with RRULE
        bounded by trip start/end.
      - Writes per-event override files from Screen 6 to
        `overrides/<superseded-cal-id>/<event-id>/<date>.md`.
      - Single atomic git commit with body:
        `vacation overlay "<trip name>" <yyyy-mm-dd>..<yyyy-mm-dd>:
        N events, M overrides, K superseded calendars`.
- [ ] **HV-F.9** Edit-in-flight: if a trip's `cal-trip-<id>` already
      exists, the wizard's "+ Plan a trip" entry-point offers a
      detected-trip chip "Continue editing 'Sicily 2026'?". Picking
      it re-enters the wizard with `TripDraft` rehydrated from the
      existing calendar; commit produces a diff-commit, not a fresh
      calendar.
- [ ] **HV-F.10** Cancel-trip: separate "cancel this trip" action
      from the overlay-management UI; deletes the `cal-trip-<id>/`
      directory and all `overrides/` it created, atomic commit
      `vacation overlay "<trip name>" cancelled`.

---

## Phase HV-G — Entry-points

- [ ] **HV-G.1** Settings → "+ Plan a trip" row (Compose
      `ListItem`, leading icon = bat-with-suitcase). Tap launches
      HV-F. LOCKED placement: directly under "Calendars" section,
      above "Sharing".
- [ ] **HV-G.2** Calendar-detail screen → "+ overlay from template"
      menu item; on tap shows a picker of available overlay
      templates; "Vacation / trip" picker entry launches HV-F seeded
      with the current calendar pre-flagged for supersedence.
- [ ] **HV-G.3** Now-card empty-state copy (when no events today):
      "no plans today — want to plan a trip?" with a small bat-paw
      button linking to HV-F. LOCKED as the ONLY in-card promotion
      of the wizard (no other surfaces nag the user).

---

## Phase HV-H — Bat-mascot sticker beats for vacation wizard

Extends Phase LW-K (`draft-lifestyle-wizard.md`) sticker spec. New
beats added to the shared sprite sheet:

- [ ] **HV-H.1** `trip-suitcase-waving` — bat with tiny suitcase
      waving paw. Used on HV-F.2.
- [ ] **HV-H.2** `flight-paw-prints` — paw prints curving across a
      cloud. Used on HV-F.3 + HV-F.4.
- [ ] **HV-H.3** `beach-loungin-with-cage-still-on` — bat on a
      pool float, tiny cage visible. NEUTRAL VARIANT REQUIRED:
      `beach-loungin` (no cage). Switched at render-time by K-mode.
      Used on HV-F.5.
- [ ] **HV-H.4** `supersedence-snooze-toggle` — bat tucking a tiny
      calendar under a blanket. Used on HV-F.6.
- [ ] **HV-H.5** `confirm-tail-flick` — bat tail-flick with
      thumbs-up paw. Used on HV-F.7 confirm.
- [ ] **HV-H.6** `good-boy-stays-good-boy-on-vacation` — bat in
      sunglasses brushing tiny teeth with a determined face.
      NEUTRAL VARIANT REQUIRED: `staying-on-track`. Used on HV-F.7
      reassurance bubble.
- [ ] **HV-H.7** All new sticker assets follow LW-K's SVG-with-PNG-
      fallback pipeline, 64×64 + 128×128 + 256×256 export sizes.

---

## Phase HV-I — Tests

- [ ] **HV-I.1** Template-content tests: ktoml parses every entry in
      HV-A / HV-B / HV-C / HV-D; sub-beat duration ≤ atomic duration
      validator passes; all required fields present.
- [ ] **HV-I.2** Neutral-mode-rendering tests: every entry in HV-A
      has a `neutral_title` distinct from `title` OR a comment
      asserting the two are identical (e.g. `bath-mop` where the cute
      title and neutral title coincide). Test asserts the render
      function picks `neutral_title` in K-3 mode.
- [ ] **HV-I.3** Kink-variant rendering tests: HV-A.17 variants
      render their title only when K-mode is ON; base entry's title
      renders otherwise.
- [ ] **HV-I.4** Travel-prep back-fill tests: given a `TripDraft`
      with `start_date = 2026-06-12`, materializer produces
      `passport-validity` event on 2026-05-01 (T-42), `pack-clothes`
      on 2026-06-10 (T-2), etc. Conditional entries filtered
      correctly.
- [ ] **HV-I.5** Flight-day timeline tests: given a flight with
      `scheduled_departure = 2026-06-12T14:00`, `is_international =
      true`, `transport_minutes = 45`, materializer produces wake-up
      at 09:15, transport at 09:15, airport-arrive at 11:00, etc.
- [ ] **HV-I.6** Supersedence-resolver tests (invariants S1..S5):
      each invariant has a fixture and a unit test in
      `app/src/test/.../resolver/SupersedenceTest.kt`.
- [ ] **HV-I.7** Wizard path-coverage tests (Compose UI tests via
      androidx.compose.ui.test):
      - Happy path: flight trip with all defaults.
      - No-flight trip (mode = car).
      - Trip with kink-kit toggled OFF.
      - Trip with `unaligned-private` alignment (kink-care anchors
        absent).
      - Trip with override "keep vet appointment on" (verifies the
        per-event override file written).
      - Edit-in-flight: re-open existing trip, change end date,
        verify diff-commit.
      - Cancel trip: verify `cal-trip-<id>/` removed + atomic
        commit.
- [ ] **HV-I.8** Sticker rendering tests: neutral-mode swap of
      `beach-loungin-with-cage-still-on` → `beach-loungin` verified
      via screenshot test.
- [ ] **HV-I.9** Privacy tests: notifications for any event tagged
      `kink` and `privacy_flag = K-2` render with the empty-body
      lockscreen path from AT-C.2.

---

## Phase HV-K — ADHD body-doubling anchors atomic template

New template file `templates/atomic-adhd-anchors.toml`.

**Philosophy header (LOCKED, inlined as a TOML comment block at the
top of the file):** *"ADHD brains lose track of body signals; the
inverted habit model lets us schedule the body-doubling without the
schedule feeling like surveillance. Default state = done; deviation =
explicit. Watch buzz is the gentle hand on the shoulder, not a drill
sergeant."*

ADHD-brain comprehensiveness applies: every anchor gets its own
atomic. No combining. The boring-but-vital are listed exhaustively.

- [ ] **HV-K.1** Path: `templates/atomic-adhd-anchors.toml`. Schema
      header:
      ```toml
      schema_version = 1
      template_id = "atomic-adhd-anchors"
      display_name = "ADHD body-doubling anchors"
      category = "adhd-anchors"
      neutral_safe = true
      ```
- [ ] **HV-K.2** Hydration anchors (LOCKED). Every-90min cadence is
      a *configurable default* — wizard surface lets the user dial it
      to 60, 120, etc.
      ```toml
      [[entry]]
      id = "water-check"
      title = "good boy sips his water"
      neutral_title = "Drink 250ml of water now"
      duration_minutes = 1
      sticker_id = "water-glass"
      category = "hydration"
      default_cadence = "every-90-minutes-waking"
      default_window = ["07:00", "22:00"]
      tags = ["adhd-anchor", "hydration"]

      [[entry]]
      id = "water-bottle-refill"
      title = "good boy refills his water bottle"
      neutral_title = "Refill water bottle"
      duration_minutes = 2
      sticker_id = "water-bottle"
      default_cadence = "twice-daily"
      default_times = ["09:00", "15:00"]
      tags = ["adhd-anchor", "hydration"]

      [[entry]]
      id = "electrolyte-check"
      title = "good boy checks his electrolytes"
      neutral_title = "Electrolyte check (hot day / hungover / sick)"
      duration_minutes = 2
      sticker_id = "electrolyte"
      default_cadence = "conditional"
      condition = "hot_day OR hungover OR sick"
      tags = ["adhd-anchor", "hydration", "conditional"]

      [[entry]]
      id = "caffeine-cutoff"
      title = "no more zoomies juice for good boy"
      neutral_title = "Caffeine cutoff (8h before sleep)"
      duration_minutes = 1
      sticker_id = "coffee-stop"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-8h"
      tags = ["adhd-anchor", "sleep-hygiene"]

      [[entry]]
      id = "alcohol-water-pair"
      title = "good boy drinks a glass of water for every drink"
      neutral_title = "1:1 water pairing while drinking"
      duration_minutes = 1
      sticker_id = "water-pair"
      default_cadence = "conditional"
      condition = "drinking_alcohol"
      tags = ["adhd-anchor", "hydration", "conditional"]
      ```
- [ ] **HV-K.3** Food anchors (LOCKED). Four per waking day, each
      with a *did-you-actually-eat* sub-beat (the inverted-habit
      deviation prompt). Blood-sugar-check is a separate cadence.
      ```toml
      [[entry]]
      id = "breakfast-anchor"
      title = "good boy eats his breakfast"
      neutral_title = "Breakfast"
      duration_minutes = 20
      sticker_id = "breakfast"
      default_cadence = "daily"
      default_times = ["08:00"]
      tags = ["adhd-anchor", "food"]
      [[entry.subbeat]]
      label = "good boy fed himself today?"
      neutral_label = "Did you actually eat?"
      duration_seconds = 30
      sticker_id = "checkin"

      [[entry]]
      id = "lunch-anchor"
      title = "good boy eats his lunch"
      neutral_title = "Lunch"
      duration_minutes = 30
      sticker_id = "lunch"
      default_cadence = "daily"
      default_times = ["12:30"]
      tags = ["adhd-anchor", "food"]
      [[entry.subbeat]]
      label = "good boy fed himself today?"
      neutral_label = "Did you actually eat?"
      duration_seconds = 30

      [[entry]]
      id = "dinner-anchor"
      title = "good boy eats his dinner"
      neutral_title = "Dinner"
      duration_minutes = 30
      sticker_id = "dinner"
      default_cadence = "daily"
      default_times = ["19:00"]
      tags = ["adhd-anchor", "food"]
      [[entry.subbeat]]
      label = "good boy fed himself today?"
      neutral_label = "Did you actually eat?"
      duration_seconds = 30

      [[entry]]
      id = "snack-anchor"
      title = "good boy gets a snack"
      neutral_title = "Afternoon snack"
      duration_minutes = 10
      sticker_id = "snack"
      default_cadence = "daily"
      default_times = ["16:00"]
      tags = ["adhd-anchor", "food"]

      [[entry]]
      id = "blood-sugar-check"
      title = "good boy checks his sugars"
      neutral_title = "Blood glucose check"
      duration_minutes = 2
      sticker_id = "glucose"
      default_cadence = "configurable"
      default_times = ["07:30", "12:00", "18:30", "22:00"]
      tags = ["adhd-anchor", "medical", "conditional"]
      condition = "hypoglycemic_or_diabetic"
      ```
- [ ] **HV-K.4** Medication adherence shapes (LOCKED). The TEMPLATE
      provides shapes — the user fills in the actual med names in
      the wizard. The med-specific titles get composed at scaffold
      time: `"good boy takes his <med>"` / neutral `"Take <med>"`.
      ```toml
      [[entry]]
      id = "meds-morning"
      title = "good boy takes his morning meds"
      neutral_title = "Morning medication"
      duration_minutes = 2
      sticker_id = "pill-am"
      default_cadence = "daily"
      default_times = ["08:00"]
      tags = ["adhd-anchor", "medication"]
      privacy_flag = true   # K-2 default for meds

      [[entry]]
      id = "meds-noon"
      title = "good boy takes his noon meds"
      neutral_title = "Midday medication"
      duration_minutes = 2
      sticker_id = "pill-noon"
      default_cadence = "daily"
      default_times = ["12:00"]
      tags = ["adhd-anchor", "medication"]
      privacy_flag = true

      [[entry]]
      id = "meds-evening"
      title = "good boy takes his evening meds"
      neutral_title = "Evening medication"
      duration_minutes = 2
      sticker_id = "pill-pm"
      default_cadence = "daily"
      default_times = ["18:00"]
      tags = ["adhd-anchor", "medication"]
      privacy_flag = true

      [[entry]]
      id = "meds-bedtime"
      title = "good boy takes his bedtime meds"
      neutral_title = "Bedtime medication"
      duration_minutes = 2
      sticker_id = "pill-night"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-30m"
      tags = ["adhd-anchor", "medication"]
      privacy_flag = true

      [[entry]]
      id = "meds-as-needed-rescue"
      title = "good boy may take his rescue meds if he needs them"
      neutral_title = "As-needed (rescue) medication available"
      duration_minutes = 1
      sticker_id = "pill-rescue"
      default_cadence = "conditional"
      condition = "user_initiated"
      tags = ["adhd-anchor", "medication", "rescue"]
      privacy_flag = true

      [[entry]]
      id = "inhaler-prophylactic"
      title = "good boy puffs his preventer"
      neutral_title = "Prophylactic inhaler"
      duration_minutes = 2
      sticker_id = "inhaler"
      default_cadence = "twice-daily"
      default_times = ["08:00", "20:00"]
      tags = ["adhd-anchor", "medication", "asthma"]
      privacy_flag = true

      [[entry]]
      id = "inhaler-rescue"
      title = "good boy reaches for his rescue puffer"
      neutral_title = "Rescue inhaler"
      duration_minutes = 1
      sticker_id = "inhaler-rescue"
      default_cadence = "conditional"
      condition = "user_initiated"
      tags = ["adhd-anchor", "medication", "asthma", "rescue"]
      privacy_flag = true

      [[entry]]
      id = "prn-rescue-bag-check"
      title = "good boy checks his rescue meds are in his bag"
      neutral_title = "PRN rescue meds bag check"
      duration_minutes = 3
      sticker_id = "bag-meds"
      default_cadence = "weekly"
      default_weekday = "sun"
      default_times = ["19:00"]
      tags = ["adhd-anchor", "medication"]
      ```
- [ ] **HV-K.5** Body-state check-ins (LOCKED). Every-3h cadence
      during waking hours; tension-scan and eye-rest are
      separately cadenced.
      ```toml
      [[entry]]
      id = "bathroom-check"
      title = "when did good boy last pee?"
      neutral_title = "Bathroom check (go if it's been a while)"
      duration_minutes = 3
      sticker_id = "bathroom"
      default_cadence = "every-3-hours-waking"
      default_window = ["07:00", "22:00"]
      tags = ["adhd-anchor", "body-state"]

      [[entry]]
      id = "posture-check-adhd"
      title = "good boy sits up straight"
      neutral_title = "Posture check (ADHD slouch reset)"
      duration_minutes = 1
      sticker_id = "posture-reset"
      default_cadence = "every-3-hours-waking"
      tags = ["adhd-anchor", "body-state"]
      note = "Separate from the kink posture-check in atomic-kink-self-care; that one is intentional, this one is the ADHD-slouch fix."

      [[entry]]
      id = "eye-rest"
      title = "good boy looks far away"
      neutral_title = "Eye rest (20-20-20 rule)"
      duration_minutes = 1
      sticker_id = "eye-rest"
      default_cadence = "hourly-waking"
      default_window = ["07:00", "22:00"]
      tags = ["adhd-anchor", "body-state"]
      [[entry.subbeat]]
      label = "Look 20ft away for 20s"
      neutral_label = "20s gaze at distant object"
      duration_seconds = 20

      [[entry]]
      id = "tension-scan"
      title = "good boy unclenches"
      neutral_title = "Tension scan"
      duration_minutes = 1
      sticker_id = "tension-scan"
      default_cadence = "every-3-hours-waking"
      tags = ["adhd-anchor", "body-state"]
      [[entry.subbeat]]
      label = "Jaw — unclench"
      duration_seconds = 7
      [[entry.subbeat]]
      label = "Shoulders — drop"
      duration_seconds = 7
      [[entry.subbeat]]
      label = "Hands — uncurl"
      duration_seconds = 8
      [[entry.subbeat]]
      label = "Breath — slow exhale"
      duration_seconds = 8

      [[entry]]
      id = "body-temp-check"
      title = "is good boy overheated or cold?"
      neutral_title = "Body temperature check"
      duration_minutes = 1
      sticker_id = "thermometer"
      default_cadence = "every-3-hours-waking"
      tags = ["adhd-anchor", "body-state"]
      note = "Mood + focus are temperature-coupled; this is ADHD-relevant."
      ```
- [ ] **HV-K.6** Cognitive anchors (LOCKED).
      ```toml
      [[entry]]
      id = "what-am-i-doing-rn"
      title = "what is good boy doing right now?"
      neutral_title = "What am I doing right now?"
      duration_minutes = 1
      sticker_id = "compass"
      default_cadence = "hourly-during-focus-blocks"
      tags = ["adhd-anchor", "cognitive"]
      note = "Deviation kind = 'got lost in something' with optional free-text note."

      [[entry]]
      id = "did-i-eat-today"
      title = "did your good boy take care of himself today?"
      neutral_title = "End-of-day self-care audit (food, water, meds)"
      duration_minutes = 3
      sticker_id = "audit"
      default_cadence = "daily"
      default_times = ["21:30"]
      tags = ["adhd-anchor", "cognitive"]

      [[entry]]
      id = "tomorrow-glance"
      title = "good boy peeks at tomorrow"
      neutral_title = "Glance at tomorrow's schedule"
      duration_minutes = 5
      sticker_id = "tomorrow"
      default_cadence = "daily"
      default_times = ["21:00"]
      tags = ["adhd-anchor", "cognitive", "sleep-hygiene"]
      [[entry.subbeat]]
      label = "Meds prep (tomorrow's pill caddy)"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Outfit prep"
      duration_seconds = 90
      [[entry.subbeat]]
      label = "Bag pack"
      duration_seconds = 90
      [[entry.subbeat]]
      label = "Calendar scan"
      duration_seconds = 60

      [[entry]]
      id = "dopamine-budget-check"
      title = "is good boy doom-scrolling?"
      neutral_title = "Dopamine-budget check (doom-scroll detector)"
      duration_minutes = 1
      sticker_id = "phone-down"
      default_cadence = "every-2-hours-waking"
      tags = ["adhd-anchor", "cognitive"]
      ```
- [ ] **HV-K.7** Sleep hygiene anchors (LOCKED). `tomorrow-glance`
      from HV-K.6 cross-references here; do NOT duplicate the entry,
      just reference it.
      ```toml
      [[entry]]
      id = "phone-out-of-bedroom"
      title = "good boy banishes his phone from the den"
      neutral_title = "Phone out of bedroom (30min before sleep)"
      duration_minutes = 2
      sticker_id = "phone-out"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-30m"
      tags = ["adhd-anchor", "sleep-hygiene"]

      [[entry]]
      id = "blue-light-cutoff"
      title = "good boy dims his screens"
      neutral_title = "Blue-light cutoff (1h before sleep)"
      duration_minutes = 2
      sticker_id = "screen-dim"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-1h"
      tags = ["adhd-anchor", "sleep-hygiene"]

      [[entry]]
      id = "bedroom-temp-check"
      title = "good boy checks his den temperature"
      neutral_title = "Bedroom temperature check"
      duration_minutes = 2
      sticker_id = "bedroom-thermo"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-15m"
      tags = ["adhd-anchor", "sleep-hygiene"]

      [[entry]]
      id = "wind-down-routine-start"
      title = "good boy starts winding down"
      neutral_title = "Wind-down routine start (1h before sleep)"
      duration_minutes = 60
      sticker_id = "wind-down"
      default_cadence = "daily"
      default_offset_from = "sleep_target"
      default_offset = "-1h"
      tags = ["adhd-anchor", "sleep-hygiene"]
      [[entry.subbeat]]
      label = "Shower or skip"
      duration_seconds = 600
      [[entry.subbeat]]
      label = "Teeth"
      duration_seconds = 300
      [[entry.subbeat]]
      label = "Skincare"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Set tomorrow's outfit"
      duration_seconds = 180
      [[entry.subbeat]]
      label = "Water by bed"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Alarm set"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Light out"
      duration_seconds = 30
      note = "Cross-references tomorrow-glance from HV-K.6; that anchor runs separately at 21:00."
      ```
- [ ] **HV-K.8** ADHD-specific maintenance (LOCKED).
      ```toml
      [[entry]]
      id = "meds-refill-check"
      title = "good boy counts his pills"
      neutral_title = "Medication refill check (count vs. days remaining)"
      duration_minutes = 5
      sticker_id = "pill-count"
      default_cadence = "weekly"
      default_weekday = "sun"
      default_times = ["19:30"]
      tags = ["adhd-anchor", "medication", "maintenance"]
      auto_flag_at = "days_remaining < 7"
      privacy_flag = true

      [[entry]]
      id = "therapist-followup"
      title = "good boy talks to his therapist"
      neutral_title = "Therapist appointment"
      duration_minutes = 60
      sticker_id = "therapist"
      default_cadence = "configurable"
      default_cadence_default = "biweekly"
      tags = ["adhd-anchor", "mental-health"]
      privacy_flag = true

      [[entry]]
      id = "psychiatrist-followup"
      title = "good boy sees his psychiatrist"
      neutral_title = "Psychiatrist appointment"
      duration_minutes = 30
      sticker_id = "psychiatrist"
      default_cadence = "quarterly"
      tags = ["adhd-anchor", "mental-health", "medication"]
      privacy_flag = true

      [[entry]]
      id = "prescription-renewal-adhd"
      title = "good boy renews his script"
      neutral_title = "Prescription renewal"
      duration_minutes = 10
      sticker_id = "renewal"
      default_cadence = "triggered"
      trigger = "meds-refill-check.days_remaining < 14"
      tags = ["adhd-anchor", "medication", "maintenance"]
      privacy_flag = true

      [[entry]]
      id = "executive-function-reset"
      title = "good boy resets his week"
      neutral_title = "Executive-function reset (inbox / desk / week-plan)"
      duration_minutes = 30
      sticker_id = "ef-reset"
      default_cadence = "weekly"
      default_weekday = "sun"
      default_times = ["18:00"]
      tags = ["adhd-anchor", "cognitive", "maintenance"]
      [[entry.subbeat]]
      label = "Empty inbox to zero"
      duration_seconds = 600
      [[entry.subbeat]]
      label = "Clear desk surface"
      duration_seconds = 300
      [[entry.subbeat]]
      label = "Plan the week"
      duration_seconds = 900

      [[entry]]
      id = "hyperfocus-recovery"
      title = "good boy comes up for air"
      neutral_title = "Hyperfocus recovery (forced break + anchors restart)"
      duration_minutes = 15
      sticker_id = "surface-air"
      default_cadence = "triggered"
      trigger = "continuous_focus_block > 3h"
      tags = ["adhd-anchor", "cognitive"]
      side_effect = "restart_anchor_cadences"
      ```
- [ ] **HV-K.9** Sub-beat duration validator passes for every entry
      (sum ≤ atomic duration). Wind-down sub-beats sum to 1410s ≤
      3600s envelope; tension-scan sub-beats sum to 30s ≤ 60s.
- [ ] **HV-K.10** Render notes: `auto_flag_at`, `trigger`,
      `side_effect`, `condition` are NEW additive scaffolding-time
      fields. Document in HV-O integration update; resolver ignores
      them after scaffold (they only drive the wizard's
      materialization decisions).

---

## Phase HV-L — Medication & menstrual management

Two related but distinct template files. The HV-K medication
*adherence* entries are the daily buzz; HV-L medication *management*
covers the supply-chain + clinical-followup layer. Menstrual cycle
is its own template because it has its own clock (the cycle), and
because trans / nonbinary users may toggle it on/off independent of
other body-state anchors.

**File-header philosophy for `atomic-menstrual-cycle.toml` (LOCKED,
inlined as TOML comment block):** *"period-tracking is a calendar
concern not a separate app; we own the cadence + the body-doubling.
Kink-aware: a sub on her period may schedule a 'period grace'
overlay that supersedes kink-care anchors per HV-E. Trans +
nonbinary users on hormones use this template with edits — keep
field names anatomically-neutral where possible."*

### HV-L.A — Medication management template

- [ ] **HV-L.A.1** Path: `templates/atomic-medication.toml`. Schema
      header:
      ```toml
      schema_version = 1
      template_id = "atomic-medication"
      display_name = "Atomic medication management"
      category = "medical"
      neutral_safe = true
      ```
- [ ] **HV-L.A.2** Supply-chain entries (LOCKED). All
      `privacy_flag = true` by default (med info = K-2 sensitive).
      ```toml
      [[entry]]
      id = "prescription-renewal"
      title = "good boy renews his script"
      neutral_title = "Prescription renewal"
      duration_minutes = 10
      sticker_id = "renewal"
      default_cadence = "per-med-supply"
      tags = ["medical", "supply"]
      privacy_flag = true
      note = "Factors days-supply remaining per med; auto-fires when supply < 14 days."

      [[entry]]
      id = "pharmacy-pickup"
      title = "good boy collects his pills"
      neutral_title = "Pharmacy pickup"
      duration_minutes = 20
      sticker_id = "pharmacy"
      default_cadence = "triggered"
      trigger = "prescription-renewal.completed + 1d"
      tags = ["medical", "supply"]
      privacy_flag = true

      [[entry]]
      id = "med-side-effect-log"
      title = "good boy logs how he's feeling on his new med"
      neutral_title = "Medication side-effect log"
      duration_minutes = 5
      sticker_id = "side-effect-log"
      default_cadence = "tapered"
      cadence_schedule = "weekly_for_90d_then_monthly"
      tags = ["medical", "monitoring"]
      privacy_flag = true
      condition = "newly_started_med"

      [[entry]]
      id = "med-titration-check"
      title = "good boy checks his dose ladder"
      neutral_title = "Medication titration check-in"
      duration_minutes = 5
      sticker_id = "titration"
      default_cadence = "conditional"
      condition = "med_being_titrated"
      tags = ["medical", "monitoring"]
      privacy_flag = true

      [[entry]]
      id = "med-interaction-review"
      title = "good boy reviews his med list with his doctor"
      neutral_title = "Annual medication interaction review"
      duration_minutes = 30
      sticker_id = "interaction-review"
      default_cadence = "annual"
      tags = ["medical", "monitoring"]
      privacy_flag = true

      [[entry]]
      id = "supplement-cycle-review"
      title = "good boy audits his supplement shelf"
      neutral_title = "Supplement cycle review (what's working, what's woo)"
      duration_minutes = 15
      sticker_id = "supplement-audit"
      default_cadence = "monthly"
      tags = ["medical", "monitoring"]
      ```
- [ ] **HV-L.A.3** Emergency-meds + vaccinations (LOCKED).
      ```toml
      [[entry]]
      id = "emergency-rescue-meds-check"
      title = "good boy checks his rescue stash"
      neutral_title = "Emergency rescue meds check (epipen / inhaler / glucose / naloxone)"
      duration_minutes = 10
      sticker_id = "rescue-stash"
      default_cadence = "monthly"
      tags = ["medical", "rescue", "maintenance"]
      privacy_flag = true
      [[entry.subbeat]]
      label = "Bag — in-date + present"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Car — in-date + present"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Bedside — in-date + present"
      duration_seconds = 120
      [[entry.subbeat]]
      label = "Home backup — in-date + present"
      duration_seconds = 240

      [[entry]]
      id = "vaccinations-due"
      title = "good boy gets his jabs"
      neutral_title = "Vaccinations due check"
      duration_minutes = 5
      sticker_id = "vaccine"
      default_cadence = "annual"
      tags = ["medical", "preventative"]
      note = "Annual flu, COVID per public-health schedule, tetanus 10y, HPV catch-up, travel-specific."

      [[entry]]
      id = "bloodwork-quarterly"
      title = "good boy gets his blood drawn"
      neutral_title = "Quarterly bloodwork (liver / kidney / lipid)"
      duration_minutes = 30
      sticker_id = "bloodwork"
      default_cadence = "quarterly"
      condition = "chronic_med_requiring_monitoring"
      tags = ["medical", "monitoring"]
      privacy_flag = true
      ```
- [ ] **HV-L.A.4** Specialist follow-ups (LOCKED). Cadence
      per-condition; user-toggle which specialists apply.
      ```toml
      [[entry]]
      id = "specialist-followup"
      title = "good boy sees his specialist"
      neutral_title = "Specialist follow-up"
      duration_minutes = 45
      sticker_id = "specialist"
      default_cadence = "per-condition"
      tags = ["medical", "specialist"]
      privacy_flag = true
      note = "Derm annual, endo per protocol, cardio per protocol, etc. Wizard composes per-condition copies."

      [[entry]]
      id = "dental-cleaning"
      title = "good boy gets his fangs polished"
      neutral_title = "Dental cleaning"
      duration_minutes = 45
      sticker_id = "dental-cleaning"
      default_cadence = "every-6-months"
      tags = ["medical", "dental"]

      [[entry]]
      id = "dental-exam"
      title = "good boy gets his fangs checked"
      neutral_title = "Dental exam"
      duration_minutes = 30
      sticker_id = "dental-exam"
      default_cadence = "annual"
      tags = ["medical", "dental"]

      [[entry]]
      id = "dental-xrays"
      title = "good boy gets fang x-rays"
      neutral_title = "Dental x-rays"
      duration_minutes = 15
      sticker_id = "dental-xray"
      default_cadence = "annual"
      tags = ["medical", "dental"]

      [[entry]]
      id = "dental-deep-clean"
      title = "good boy gets his deep clean"
      neutral_title = "Deep dental cleaning (scaling)"
      duration_minutes = 60
      sticker_id = "dental-deep"
      default_cadence = "as-needed"
      tags = ["medical", "dental"]

      [[entry]]
      id = "vision-exam"
      title = "good boy gets his eyes checked"
      neutral_title = "Vision exam"
      duration_minutes = 45
      sticker_id = "vision"
      default_cadence = "annual"
      tags = ["medical", "vision"]

      [[entry]]
      id = "hearing-check"
      title = "good boy gets his ears checked"
      neutral_title = "Hearing check"
      duration_minutes = 30
      sticker_id = "hearing"
      default_cadence = "configurable"
      default_cadence_default_age_under_50 = "every-3-years"
      default_cadence_default_age_50_plus = "annual"
      tags = ["medical", "hearing"]

      [[entry]]
      id = "skin-cancer-screen"
      title = "good boy gets his moles checked"
      neutral_title = "Skin cancer screening"
      duration_minutes = 30
      sticker_id = "skin-screen"
      default_cadence = "configurable"
      default_cadence_default_high_risk = "annual"
      default_cadence_default_normal_risk = "every-2-years"
      tags = ["medical", "preventative"]

      [[entry]]
      id = "mental-health-checkin-weekly"
      title = "good boy journals about his feelings"
      neutral_title = "Weekly mental-health journaling"
      duration_minutes = 15
      sticker_id = "journal"
      default_cadence = "weekly"
      default_weekday = "sun"
      default_times = ["20:00"]
      tags = ["medical", "mental-health"]
      privacy_flag = true

      [[entry]]
      id = "physical-therapy-session"
      title = "good boy does his PT"
      neutral_title = "Physical therapy session"
      duration_minutes = 60
      sticker_id = "pt"
      default_cadence = "per-protocol"
      condition = "active_pt_protocol"
      tags = ["medical", "rehab"]

      [[entry]]
      id = "chiropractor-session"
      title = "good boy gets his back cracked"
      neutral_title = "Chiropractor session"
      duration_minutes = 30
      sticker_id = "chiro"
      default_cadence = "user-toggle"
      tags = ["medical", "bodywork"]

      [[entry]]
      id = "massage-session"
      title = "good boy gets a massage"
      neutral_title = "Massage session"
      duration_minutes = 60
      sticker_id = "massage"
      default_cadence = "user-toggle"
      tags = ["medical", "bodywork"]

      [[entry]]
      id = "acupuncture-session"
      title = "good boy gets needled"
      neutral_title = "Acupuncture session"
      duration_minutes = 60
      sticker_id = "acupuncture"
      default_cadence = "user-toggle"
      tags = ["medical", "bodywork"]
      ```

### HV-L.B — Menstrual-cycle template

- [ ] **HV-L.B.1** Path: `templates/atomic-menstrual-cycle.toml`.
      Schema header:
      ```toml
      schema_version = 1
      template_id = "atomic-menstrual-cycle"
      display_name = "Atomic menstrual cycle"
      category = "medical"
      neutral_safe = true
      anatomically_neutral_field_names = true
      ```
- [ ] **HV-L.B.2** Cycle anchors (LOCKED). `period-day-1` is the
      anchor the cycle template materializes from; the rest are
      derived offsets. Inverted-habit model: we ASSUME the cycle is
      on-track; deviations log change.
      ```toml
      [[entry]]
      id = "period-day-1"
      title = "good girl's cycle begins"
      neutral_title = "Period day 1"
      duration_minutes = 5
      sticker_id = "cycle-anchor"
      default_cadence = "cycle-anchor"
      tags = ["medical", "menstrual"]
      privacy_flag = true
      note = "User logs start; recurrence drives the rest of the template."

      [[entry]]
      id = "period-day-N"
      title = "good girl on day N"
      neutral_title = "Period day N"
      duration_minutes = 10
      sticker_id = "cycle-day"
      default_cadence = "days-1-through-7-after-anchor"
      tags = ["medical", "menstrual"]
      privacy_flag = true
      [[entry.subbeat]]
      label = "Tampon / pad / cup change cadence"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Cramp-management check-in"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Iron-rich food anchor"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Extra hydration (+500ml)"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Heating pad availability check"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Mood check"
      duration_seconds = 60

      [[entry]]
      id = "ovulation-day"
      title = "good girl ovulates"
      neutral_title = "Ovulation day"
      duration_minutes = 2
      sticker_id = "ovulation"
      default_cadence = "mid-cycle"
      default_offset_from_anchor = "+14d-from-next-period"
      tags = ["medical", "menstrual", "fertility"]
      privacy_flag = true
      condition = "fertility_aware_or_trying_or_avoiding"

      [[entry]]
      id = "pms-week"
      title = "good girl's pms week"
      neutral_title = "PMS week (days -7 to -1)"
      duration_minutes = 15
      sticker_id = "pms"
      default_cadence = "days-minus-7-through-minus-1"
      tags = ["medical", "menstrual"]
      privacy_flag = true
      [[entry.subbeat]]
      label = "Mood-volatility warning"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Comfort-food permission"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Energy-conservation note"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Social-calendar buffer scan"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Sleep-priority elevation"
      duration_seconds = 60
      ```
- [ ] **HV-L.B.3** Contraceptive anchors (LOCKED).
      ```toml
      [[entry]]
      id = "pill-time"
      title = "good girl takes her pill"
      neutral_title = "Hormonal contraception pill"
      duration_minutes = 1
      sticker_id = "pill-contraceptive"
      default_cadence = "daily-strict"
      default_offset_tolerance = "0m"
      tags = ["medical", "contraception"]
      privacy_flag = true
      note = "STRICT cadence; deviation = 'missed' triggers pill-pack-specific protocol."

      [[entry]]
      id = "emergency-contraception-window"
      title = "good girl knows her options"
      neutral_title = "Emergency contraception window (link to local pharmacy info)"
      duration_minutes = 2
      sticker_id = "ec-link"
      default_cadence = "triggered"
      trigger = "pill-time.deviation.kind == 'skipped'"
      tags = ["medical", "contraception", "rescue"]
      privacy_flag = true
      ```
- [ ] **HV-L.B.4** Preventative + tracking (LOCKED).
      ```toml
      [[entry]]
      id = "pap-smear"
      title = "good girl gets her smear test"
      neutral_title = "Pap smear / cervical screening"
      duration_minutes = 30
      sticker_id = "pap"
      default_cadence = "per-local-guideline"
      cadence_default_under_30 = "every-3-years"
      cadence_default_30_to_65_with_hpv = "every-5-years"
      tags = ["medical", "preventative"]
      privacy_flag = true

      [[entry]]
      id = "breast-exam-self"
      title = "good girl checks her chest"
      neutral_title = "Self breast exam"
      duration_minutes = 5
      sticker_id = "self-exam"
      default_cadence = "monthly"
      default_offset_from_anchor = "+7d-after-period-ends"
      tags = ["medical", "preventative"]
      privacy_flag = true

      [[entry]]
      id = "mammogram"
      title = "good girl gets her mammogram"
      neutral_title = "Mammogram"
      duration_minutes = 30
      sticker_id = "mammogram"
      default_cadence = "per-local-guideline"
      condition = "age_40_plus"
      tags = ["medical", "preventative"]
      privacy_flag = true

      [[entry]]
      id = "gynae-followup"
      title = "good girl sees her gynae"
      neutral_title = "Gynaecology follow-up"
      duration_minutes = 30
      sticker_id = "gynae"
      default_cadence = "annual"
      tags = ["medical", "preventative"]
      privacy_flag = true

      [[entry]]
      id = "supplies-restock"
      title = "good girl restocks her cycle kit"
      neutral_title = "Period supplies restock (tampons / pads / cups / discs / underwear)"
      duration_minutes = 5
      sticker_id = "supplies"
      default_cadence = "biweekly"
      tags = ["medical", "menstrual", "supply"]

      [[entry]]
      id = "cycle-tracking-export"
      title = "good girl shares her cycle log with her doctor"
      neutral_title = "Cycle-tracking export (provider request)"
      duration_minutes = 5
      sticker_id = "cycle-export"
      default_cadence = "monthly"
      condition = "provider_requested"
      tags = ["medical", "menstrual", "monitoring"]
      privacy_flag = true
      ```
- [ ] **HV-L.B.5** Period-grace overlay (LOCKED). When `period-day-N`
      events are materialized AND the user has kink-care anchors
      active (cage-check, plug-check, edge-and-stop from AT-E), the
      menstrual template scaffolds a `cal-period-grace` calendar
      with `supersedes = ["routine-kink"]` for `period-day-1`
      through `period-day-N` per HV-E. The supersedence is opt-in
      at wizard scaffold time (one toggle: "pause kink-care anchors
      during period?"). LOCKED.
- [ ] **HV-L.B.6** Atomicity validator: every did-you-do-X is its own
      atomic. The `period-day-N` sub-beats are the ONLY exception
      (they ARE the day's body-doubling, not separate cadences). No
      combining elsewhere.

---

## Phase HV-M — Event attachments schema

EXTENDS the event-file frontmatter schema (per `data-model.md` DM-*)
with a sealed `attachments` array. Lock the design fully.

- [ ] **HV-M.1** Field LOCKED: `attachments: List<Attachment>` on
      every event file's TOML frontmatter. Array empty by default;
      missing field == empty array.
- [ ] **HV-M.2** Attachment kinds (SEALED, LOCKED):
      ```toml
      # Schema per kind, embedded in [[attachments]] blocks:

      [[attachments]]
      kind = "link"
      url = "https://portal.example.com/appt/123"
      label = "Appointment portal"            # optional

      [[attachments]]
      kind = "qr"
      # Either inline OR file-ref:
      file = "attachments/<event-id>/qr-checkin.png"   # repo-relative path
      data = "PASS:AC123:SEAT12A"                       # optional, for re-render

      [[attachments]]
      kind = "file"
      file = "attachments/<event-id>/boarding-pass.pdf"
      mime_type = "application/pdf"
      size_bytes = 184320
      description = "Boarding pass (front + back)"     # optional

      [[attachments]]
      kind = "barcode"
      file = "attachments/<event-id>/bp-aztec.png"
      format = "aztec" | "pdf417" | "code128"
      data = "M1DOE/JOHN..."                            # standard BP encoding

      [[attachments]]
      kind = "vcard"
      file = "attachments/<event-id>/dentist.vcf"

      [[attachments]]
      kind = "location"
      lat = 52.5200
      lon = 13.4050
      label = "Charité Hospital — main entrance"
      ```
      LOCKED: no other kinds. The sealed union is enforced by
      ktoml deserialization with a discriminator field.
- [ ] **HV-M.3** Storage rules (LOCKED):
      - Files >100KB MUST be Git-LFS'd (Phase Z). Resolver warns at
        write-time if the file exceeds the threshold and LFS isn't
        configured for `attachments/*`.
      - Files live at `attachments/<event-id>/<filename>` (repo-
        relative), committed normally (under LFS if applicable).
      - No-origin path: files live locally with the repo; on sync
        they push with the rest. No special-case upload.
      - Privacy: if event has `private = true` (K-2), attachments
        inherit `private = true` automatically; lockscreen previews
        show NO attachment indicator (only "scheduled event").
- [ ] **HV-M.4** UI representation (LOCKED, Compose surface notes):
      - Event-detail screen: `AttachmentList` Composable below the
        body. One row per attachment, kind-keyed renderer:
        - `link` → `ListItem` with link icon; tap = open in
          system browser via `Intent.ACTION_VIEW`.
        - `qr` → thumbnail at 64dp; tap = full-screen scan-friendly
          view (max-brightness override, no chrome, 80% screen
          fill, swipe-down to close).
        - `file` → `ListItem` with file icon + mime + size; tap =
          open in viewer (PDF viewer for PDFs, image viewer for
          images, system chooser for others).
        - `barcode` → same as `qr` but full-screen renders at
          format-appropriate aspect (PDF417 wide, Aztec square,
          Code128 wide).
        - `vcard` → `ListItem` with contact icon; tap =
          `Intent.ACTION_INSERT` for contacts.
        - `location` → map preview thumbnail (static map tile); tap
          = `Intent.ACTION_VIEW` with `geo:` URI.
      - Now-card: tiny attachment-icon (paperclip glyph, 12dp) if
        ANY attachments present. No detail until tapped through.
        Privacy: glyph still appears on private events (the FACT of
        an attachment is not secret; the CONTENT is).
- [ ] **HV-M.5** Kink-aware defaults (LOCKED): events tagged `kink`
      OR with attachments matching keyword heuristics (toy-photo
      file names, scene-log file names, contract file names) AUTO-
      flag `private = true` per K-2. Resolver warns at write-time
      if a `kink`-tagged event gets an attachment without
      `private = true` set — "this looks private, want to flag it
      private?" Dismissable.
- [ ] **HV-M.6** Import path (extends Phase P, import/export):
      - `.ics` import: links in `URL` / `DESCRIPTION` → `link`
        attachments.
      - `.eml` import (airline confirmation): boarding-pass
        attachments → `barcode` + `file` (raw PDF) attachments;
        gate/seat/PNR → event description; arrival/departure → event
        time.
      - `.pkpass` import (Apple Wallet pass): unzip, lift the
        primary barcode to a `barcode` attachment, the metadata to
        event fields, the original `.pkpass` archive to a `file`
        attachment for future re-use.
      - Travel-prep events (HV-B) inherit attachments from
        materialization-time inputs (passport photo, insurance
        card scan, visa PDF).
- [ ] **HV-M.7** Validation: ktoml round-trip every kind; reject
      `file` paths outside the repo root; reject `lat`/`lon` outside
      ±90/±180; reject `mime_type` if it disagrees with file
      magic-bytes (warn only — user may have renamed).
- [ ] **HV-M.8** CLI: `skb attach <event> <file>` infers kind from
      extension (PDF → file, png-with-QR-decoded → qr, vcf → vcard,
      etc.); `skb attach <event> --kind link <url>`; `skb attach
      <event> --kind location <lat> <lon> [label]`.

---

## Phase HV-N — Multi-reminder schema + early-warning + all-day notifications

EXTEND the event-file frontmatter with a comprehensive reminder
schema. Currently events get one reminder at event-start (Phase M).
This phase extends to multi-reminder with kind-keyed semantics, plus
off-schedule detection and a system briefings calendar.

- [ ] **HV-N.1** Field LOCKED: `reminders: List<Reminder>` on every
      event file's TOML frontmatter. Empty by default; missing
      field == empty array. Each reminder:
      ```toml
      [[reminders]]
      offset = "-7d"                # ISO 8601 duration; negative = before, positive = after, "0" = at start
      kind = "heads_up"             # sealed enum, see HV-N.2
      channel = "high_priority"     # optional override; default per-kind
      lockscreen_visibility = "title_only"  # optional; default derived from private flag
      ```
- [ ] **HV-N.2** ReminderKind sealed enum (LOCKED):
      - `heads_up` — "you have X tomorrow / next week"; soft
        notification, low-priority channel (`events-heads-up`).
      - `all_day_banner` — for events flagged `all_day = true`;
        fires at user's morning briefing time (default 07:00) with
        "today: business trip" / "today: doctor's appointment 2pm".
      - `tomorrow_briefing` — fires at user's evening briefing time
        (default 21:00) the day BEFORE; lists tomorrow's events
        with off-schedule highlights.
      - `pre_event` — standard pre-event warning ("in 15 min").
      - `at_start` — current default; fires at `event.start`.
      - `post_event_checkin` — fires after `event.end`; opt-in per
        event; prompts inverted-habit deviation logging ("did you
        do this?" → yes/no/partial/snooze).
- [ ] **HV-N.3** Default reminder cadences per template category
      (LOCKED — D-NEXT.E):
      - **Medical appointments** (`category = "medical"`,
        non-anchor): `[-7d heads_up, -1d tomorrow_briefing,
        -2h pre_event, 0 at_start]`.
      - **Flights** (`category = "travel"`, sub-type flight): `[
        -1w heads_up, -24h heads_up "check-in window opens",
        -3h pre_event "leave home", -30m pre_event "board call", 0
        at_start]`.
      - **Travel-prep events** (HV-B): no pre-event reminders
        (inverted-habit applies); ONE `-3d heads_up` to surface the
        approaching prep window.
      - **Vacation-daily** (HV-D): `[0 at_start]` only.
      - **Household chores** (HV-A): NONE by default (too noisy);
        user opts in per category via wizard.
      - **Medication** (HV-K + HV-L): `[0 at_start]` only;
        missed-dose flow (post_event_checkin) handles recovery.
      - **ADHD anchors** (HV-K): `[0 at_start]` only; the cadence
        IS the reminder.
      - **Birthdays / anniversaries** (`all_day = true`): `[-1d
        tomorrow_briefing, 0 all_day_banner]`.
- [ ] **HV-N.4** Off-schedule-event detection (LOCKED, algorithm
      D-NEXT.F). Each calendar may declare a `baseline_cadence` in
      its `calendar.toml`:
      ```toml
      [baseline_cadence]
      weekdays = ["mon", "tue", "wed", "thu", "fri"]
      window = ["09:00", "17:00"]
      timezone = "Europe/Berlin"
      ```
      Events whose `(weekday, local_time)` falls OUTSIDE this
      window are marked `off_schedule = true` by the resolver. The
      `tomorrow_briefing` reminder highlights off-schedule events
      with a ⚠ prefix: *"⚠ tomorrow: 2pm dentist (off-schedule —
      work calendar normally runs 9-17)"*. LOCKED: detection is
      per-calendar, not per-event; an event inherits its calendar's
      baseline. No baseline declared = no off-schedule flagging.
- [ ] **HV-N.5** All-day events (LOCKED): events with
      `all_day = true` (per existing data-model) skip `at_start`
      and instead fire `all_day_banner` at user's morning briefing
      time. Examples: business-trip day, anniversary, birthday,
      period-day, jet-lag-recovery-day. If both `at_start` and
      `all_day_banner` are configured, `at_start` is ignored
      (resolver warns at write-time).
- [ ] **HV-N.6** Morning + evening briefing (LOCKED, D-NEXT.G).
      Both are FIRST-CLASS scheduled events on a system
      `cal-briefings` calendar. The wizard creates this calendar by
      default (`active_toggle = true`); user can disable in
      Settings → Notifications → "Show briefings".
      - `cal-briefings/events/<yyyy>/<mm>/morning-briefing-<yyyy-mm-dd>.md`
        — daily, at 07:00 (configurable), recurrence `FREQ=DAILY`.
        Body AUTO-GENERATED at fire time by walking the day's
        upcoming-window query. NOT user-editable; user-edit attempts
        get a warning + a "regenerate?" prompt.
      - `cal-briefings/events/<yyyy>/<mm>/evening-briefing-<yyyy-mm-dd>.md`
        — daily, at 21:00, recurrence `FREQ=DAILY`. Body lists
        tomorrow's events.
      - Body format (LOCKED): inline Markdown list, one item per
        upcoming event, with off-schedule events marked ⚠.
        Quick-actions per item: `snooze`, `re-arm`, `mark-done-early`,
        `mark-skipped` (notification expand-action surfaces).
- [ ] **HV-N.7** Notification stacking (LOCKED): when N events have
      reminders at the same minute (or within a 60s window), they
      collapse into ONE Android notification with N expansion
      lines. Tap-anywhere expands; per-line quick-actions remain.
      Channel hierarchy used = the HIGHEST-importance channel
      among the collapsed reminders. Privacy: collapsed
      notification's preview shows "3 reminders" if ANY of the
      collapsed events is private.
- [ ] **HV-N.8** Inverted-habit integration (LOCKED, D-NEXT.H):
      `post_event_checkin` is OPT-IN per event/template. Templates
      that opt in by default: none (templates that get the
      `at_start`-only default rely on inverted-habit's default-by-
      schedule auto-completion). Opt-in is a wizard toggle ("ask me
      if I did it after the event ends?") AND a per-event toggle
      on the event-detail sheet. When `post_event_checkin` fires:
      - Notification body: *"did you do this?"* (kink) /
        *"Completed?"* (neutral).
      - Actions: `Yes` (no-op; default-by-schedule already wins),
        `No` (writes `deviations/.../<date>.md` with
        `kind = "skipped"`), `Partial` (writes `kind = "partial"`
        with sub-beat picker), `Remind me again in X` (re-arms
        post_event_checkin at now+X).
- [ ] **HV-N.9** Resolver work + Room cache: a new
      `event_reminders` table caches `(repo, event-id, offset,
      kind, scheduled_at)`; invalidated when (a) event file
      changes, (b) `now` crosses any scheduled_at, (c) calendar's
      `baseline_cadence` changes. Briefing-body generation queries
      this table directly.
- [ ] **HV-N.10** Tests: see HV-O.3 + HV-O.4 + HV-O.5.

---

## Phase HV-O — Tests + content + integration-notes update

- [ ] **HV-O.1** Template-content tests for HV-K (ADHD anchors) and
      HV-L (med + menstrual). ktoml parses every entry; sub-beat
      duration ≤ atomic envelope; conditional/triggered fields
      preserved; render snapshot per entry under both neutral-mode
      and kink-mode.
- [ ] **HV-O.2** Attachment schema round-trip tests:
      serialize → parse → re-serialize for each of the 6 kinds;
      verify `private` flag inheritance from event;
      verify LFS-threshold warning at 100KB+; verify lockscreen
      preview hides attachment indicator when `private = true`.
- [ ] **HV-O.3** Multi-reminder firing tests:
      `ShadowAlarmManager` time-travel fixtures at event-7d / -1d /
      -3h / -30m / start / +30m; assert each reminder fires
      exactly once and not after user-dismissal.
- [ ] **HV-O.4** Off-schedule detection test: configure work
      calendar with `baseline_cadence = {weekdays = [mon..fri],
      window = ["09:00", "17:00"]}`; add a Sat-14:00 doctor event
      on it; assert resolver flags `off_schedule = true`; assert
      the day-before `tomorrow_briefing` body contains the ⚠
      prefix.
- [ ] **HV-O.5** Morning + evening briefing rendering test (fixture:
      3 events tomorrow — one on-schedule, one off-schedule, one
      private); assert the rendered briefing body collapses
      correctly, masks the private event's title, and highlights
      the off-schedule one.
- [ ] **HV-O.6** Period-cycle template snapshot test under
      kink-mode and neutral-mode; verify `pill-time.deviation` of
      `kind = "skipped"` triggers the `emergency-contraception-window`
      event materialization with a link to local pharmacy info.
- [ ] **HV-O.7** Body-doubling anchors under hyperfocus-recovery
      condition: simulate a 3h continuous focus block fixture;
      assert `hyperfocus-recovery` event materializes AND
      anchor cadences (water-check, posture-check-adhd, eye-rest)
      restart from the recovery time.
- [ ] **HV-O.8** Notification stacking test: 3 events with
      reminders at the same minute; assert one Android
      notification with 3 expansion lines; assert tap-to-expand
      shows per-event quick-actions.
- [ ] **HV-O.9** Update the existing HV-J Integration-notes section
      with the additional integration edits these phases require.
      (Done in-line below — HV-J extensions appended there directly.)
- [ ] **HV-O.10** Mode-toggle tests (HV-Q.1): free→strictly-kept→free
      transitions write the expected `mode.toml` commits; review-feed
      directory `reviews/` populates ONLY in kept mode; assert no
      `reviewable_change/*.md` entries created while `mode = "free"`;
      assert transitions cannot be blocked or vetoed by the dom
      (dom-side review responses cannot prevent the boy's mode-flip
      commit from landing); assert the 24h cooling-off confirmation
      gate is a *boy-side* confirmation, not a dom-gate.
- [ ] **HV-O.11** Review-feed cross-repo roundtrip (HV-Q.2): boy
      commits to their own repo; cross-repo resolver (Phase YY)
      surfaces the new `reviewable_change/<sha>.md` entry to the
      dom's app; dom (AI or human) writes a response to
      `reviews/<sha>/responses/<dom-fingerprint>-<timestamp>.md` IN
      THE DOM'S OWN REPO (write-back-target); boy's app's
      cross-repo-feedback pull surfaces the response attached to the
      original commit in the feed; assert reactions
      (locked/collar/good-boy/paw/heart/fire/thumbsup/🦇/smirk)
      roundtrip; assert empty-text + good-boy reaction renders as
      the cute-coded LGTM.
- [ ] **HV-O.12** AI-dom-persona safety test (HV-Q.3): instantiate
      each shipped persona (stern-but-fair, playful-tease,
      kinky-affectionate, daddy-warmth, bratty-switch-energy,
      clinical-protocol); prompt each with an explicit-content
      request from the boy; assert the persona declines without
      breaking character; assert default kink-mode AI-dom is
      *suggestive-tone, no explicit content* unless the boy has
      explicitly opted in via setting AND age-gated past K-6.
- [ ] **HV-O.13** Identity.toml round-trip (HV-R.1, HV-R.2):
      wizard LW-Screen-3.5 writes `identity.toml` at calendar-repo
      root; agent reads it on session-start; template-title
      renderer resolves `{{praise}}` placeholders to the chosen
      term; briefing salutation uses the chosen term + emoji
      density; dom-Claude register uses the chosen honorific in
      voice-of-boy and alternates praise terms across responses.
- [ ] **HV-O.14** AGENTS.md / identity.toml separation test
      (HV-R.4, D-NEXT.I): change praise term in `identity.toml`
      from "good boy" to "good pet"; assert `AGENTS.md` content
      is byte-identical before and after; assert `AGENTS.md`
      contains exactly the one-line reference *"See identity.toml
      at repo root..."*; assert no praise-string nor pronoun-string
      appears anywhere in `AGENTS.md` or `CLAUDE.md`.
- [ ] **HV-O.15** Toxic-dom safety affordance test (HV-Q.5): boy
      revokes the dom's remote read access via Phase ZZ
      remote-removal; assert the dom-side resolver can no longer
      pull review entries; boy switches mode strictly-kept-by-human
      → self-keep; assert `identity.toml` carries over unchanged;
      assert pre-existing reviews remain readable in the boy's
      git history; assert the new write-back-target is the boy's
      own repo and self-reviews land there; assert the
      always-visible "transition my mode" affordance is present
      in the most-kept UI state (NOT buried in a sub-screen).
- [ ] **HV-O.16** Leisure template content tests (HV-P):
      ktoml parses every entry in `atomic-leisure.toml`; sub-beat
      duration ≤ atomic envelope; every entry has both `title`
      and `neutral_title`; kink-coded entries (`tags` includes
      "kink") render only when K-mode is ON; the `dog-walk`
      atomic decomposes into the named 7 sub-beats; the
      caged-comfort-check cadence fires every 30min during a
      >2hr leisure block; the `locked-leisure-marker` state event
      surfaces to the dom's review feed without a corresponding
      atomic action.

---

## Phase HV-P — Leisure / scheduled-play / downtime atomic template

New template file `templates/atomic-leisure.toml`. Same TOML schema as
`atomic-self-care.toml` (AT-D) and the other atomic templates. All
entries support `neutral_title`, `tags`, optional `subbeats`, optional
`kink_variant_of` for cute-coded alternates, `sticker_id`, and the
standard inverted-habit `default_state = "done"` semantics.

**Header philosophy (LOCKED, reproduce verbatim in the TOML header
comment):** *"a good boy without scheduled play turns feral — and bored
boys make poor decisions. Scheduled downtime is not a contradiction;
it's the architecture that lets master/dom see at-a-glance whether the
boy is busy or available, and lets the boy himself say yes to 'free
time' without guilt because it's ON the schedule. Inverted habits
apply: default state = done = took the rest. Deviation = 'I skipped my
downtime to grind work' is its own kind of slob-drift the schedule
catches."*

- [ ] **HV-P.1** Create `templates/atomic-leisure.toml` with the
      header-philosophy comment block reproduced verbatim above.
- [ ] **HV-P.2** Author the **passive-consumption** category
      (entries need to BE scheduled or they eat the whole day):
      `tv-watching` (episode-budgeted; pick-a-show + N-episodes
      default; sub-beats per-episode), `movie-night`
      (90-180min block; sub-beats pick / snacks / curl-up / start
      / debrief), `youtube-budget-15` / `youtube-budget-30` /
      `youtube-budget-60` (pick cadence), `reading-session`
      (book / comic / fanfic; sub-beats phone-away / settle /
      read / bookmark), `audiobook-while-doing-something` (links
      to a chore atomic — e.g. fold-laundry + audiobook combo),
      `podcast-listen` (commute or walk?),
      `music-deep-listen` (no other inputs; 30-90min),
      `social-media-check` (15min hard cap — opt-in atomic;
      deviation = doomscroll alert), `news-check` (10-20min,
      configurable cadence daily/weekly).
- [ ] **HV-P.3** Author the **active-play** category
      (good-boy-recreation-coded): `video-games-session` (default
      60min; sub-beats hydrate / position-check-if-caged / start
      / break-at-30min / wrap; **kink variant** title *"good
      boy gets video-game time while caged"* / `neutral_title`:
      "video game session"), `board-games` (solo or social —
      different sub-flows), `tabletop-rpg-session` (2-4hr block),
      `puzzle-time` (jigsaw / crossword / sudoku),
      `hobby-project` (per-user customizable placeholder
      atomic), `creative-burst` (drawing / writing / music-make
      / craft — generic creative anchor),
      `learning-pleasure` (language app /
      course-i-am-doing-for-fun — distinct from academic study).
- [ ] **HV-P.4** Author the **movement-play** category
      (joy-coded, NOT workout-coded — separate from the workout
      atomic template): `walk-no-purpose` (20-40min just
      outside), **`dog-walk`** (per-dog cadence; sub-beats leash
      / poop-bag / water-for-dog / route-pick / actual-walk /
      post-walk-dog-water / paw-wipe — user-named explicitly,
      well-decomposed), `dog-play-time` (separate from walk —
      fetch / tug / training-sessions), `bike-ride-leisure`,
      `swim-leisure` (pool / lake / ocean),
      `playground-or-park-visit`, `dance-around-the-house`
      (5-15min joy anchor), `stretching-not-yoga`
      (just-lay-on-the-floor anchor).
- [ ] **HV-P.5** Author the **social-recreation** category:
      `call-a-friend`, `call-family`,
      `text-someone-i-havent` (the "haven't-talked-in-3-weeks"
      anchor), `coffee-with-friend` (social-block atomic),
      `dinner-with-friend`, `group-hangout` (3+ people;
      sub-beats arrive / be-present / leave-on-time),
      `online-game-with-friends`,
      `voice-chat-discord-hangout`.
- [ ] **HV-P.6** Author the **solo-decompress** category
      (the *just-being* anchors): `bath-no-purpose` (45min;
      NOT the hygiene shower), `nap-permitted` (20min power /
      90min full cycle — pick variant), `nothing-time`
      (literally do nothing for 15min; sub-beat
      phone-out-of-arm-reach), `daydream-walk` (10-20min no
      phone), `meditation` (5-30min, cadence configurable),
      `journaling-personal` (separate from the cross-repo
      `journal/` entries — this is private decompression
      journaling).
- [ ] **HV-P.7** Author the **caged-play-affordances**
      category (kink-coded, hidden in neutral-mode):
      `cage-comfort-check` during-leisure (30min cadence
      during a >2hr leisure block; sub-beats position-shift /
      water / breath / "still good?"), `locked-leisure-marker`
      (state-event declaring the entire leisure block happens
      locked — for the dom's review feed; no atomic action,
      just a state), `safeword-aware-leisure` (any kink-coded
      leisure block carries the `safeword_remind` flag; at 50%
      through the block the system surfaces a soft reminder
      that the safeword is always available).
- [ ] **HV-P.8** Author the **recovery-leisure** category
      (after-hard-things): `aftercare-time` (after a kink
      scene; sub-beats water / snack / blanket /
      cuddle-or-alone-time / debrief), `post-work-decompress`
      (5-30min mandatory transition),
      `post-social-recovery` (introvert-recharge after a
      group event, proportional to group size + duration),
      `sick-day-rest` (the "i'm-actually-sick-not-skipping"
      mode — opt-in atomic marking the day rest-only).
- [ ] **HV-P.9** Author the **long-cycle-leisure-cadence**
      category: `weekly-treat`
      (Friday-night-flavor: special-meal / special-show /
      special-game), `monthly-day-off` (a full day with NO
      scheduled tasks except the vacation-daily anchors — a
      mini-vacation-overlay), `quarterly-mini-trip` (weekend
      away; ties to HV-F vacation-wizard with shorter date
      range), `annual-real-vacation` (full HV-F trigger).
- [ ] **HV-P.10** Inline FULL TOML for `atomic-leisure.toml`
      reproduced below — each entry carries `id`, `title`,
      `neutral_title`, `duration_minutes`, `recurrence`,
      `category`, `tags` (incl. `["leisure", "neutral"]` or
      `["leisure", "kink"]`), `sticker_id`, optional
      `subbeats`.

```toml
# templates/atomic-leisure.toml
# A good boy without scheduled play turns feral — and bored boys make
# poor decisions. Scheduled downtime is not a contradiction; it's the
# architecture that lets master/dom see at-a-glance whether the boy is
# busy or available, and lets the boy himself say yes to "free time"
# without guilt because it's ON the schedule. Inverted habits apply:
# default state = done = took the rest. Deviation = "I skipped my
# downtime to grind work" is its own kind of slob-drift the schedule
# catches.

schema_version = "1"
template_id = "atomic-leisure"
default_state = "done"  # inverted-habit canonical

# ── passive consumption ──────────────────────────────────────────────

[[entries]]
id = "tv-watching"
title = "good boy gets a show 🦇"
neutral_title = "tv watching"
duration_minutes = 60
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral"]
sticker_id = "bat-with-popcorn"
subbeats = [
  { id = "pick-show", duration_minutes = 3 },
  { id = "snack-set", duration_minutes = 4 },
  { id = "watch-ep-1", duration_minutes = 25 },
  { id = "stretch-between", duration_minutes = 3 },
  { id = "watch-ep-2", duration_minutes = 25 },
]

[[entries]]
id = "movie-night"
title = "movie-night curled up 🦇"
neutral_title = "movie night"
duration_minutes = 150
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral"]
sticker_id = "bat-under-blanket"
subbeats = [
  { id = "pick", duration_minutes = 5 },
  { id = "snacks", duration_minutes = 8 },
  { id = "curl-up", duration_minutes = 3 },
  { id = "start", duration_minutes = 120 },
  { id = "debrief", duration_minutes = 8 },
]

[[entries]]
id = "youtube-budget-15"
title = "yt 15min"
neutral_title = "youtube 15 minutes"
duration_minutes = 15
recurrence = "daily-opt-in"
category = "passive-consumption"
tags = ["leisure", "neutral", "hard-cap"]
sticker_id = "bat-watching-phone"

[[entries]]
id = "youtube-budget-30"
title = "yt 30min"
neutral_title = "youtube 30 minutes"
duration_minutes = 30
recurrence = "daily-opt-in"
category = "passive-consumption"
tags = ["leisure", "neutral", "hard-cap"]
sticker_id = "bat-watching-phone"

[[entries]]
id = "youtube-budget-60"
title = "yt 60min"
neutral_title = "youtube 60 minutes"
duration_minutes = 60
recurrence = "weekly-opt-in"
category = "passive-consumption"
tags = ["leisure", "neutral", "hard-cap"]
sticker_id = "bat-watching-phone"

[[entries]]
id = "reading-session"
title = "good boy reads 🦇"
neutral_title = "reading session"
duration_minutes = 45
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral"]
sticker_id = "bat-with-book"
subbeats = [
  { id = "phone-away", duration_minutes = 2 },
  { id = "settle", duration_minutes = 3 },
  { id = "read", duration_minutes = 38 },
  { id = "bookmark", duration_minutes = 2 },
]

[[entries]]
id = "audiobook-while-doing-something"
title = "audiobook + chore combo"
neutral_title = "audiobook while doing a chore"
duration_minutes = 45
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral", "combo"]
sticker_id = "bat-with-headphones"
combo_with = ["fold-laundry", "wash-dishes", "tidy-living-room"]

[[entries]]
id = "podcast-listen"
title = "podcast time"
neutral_title = "podcast"
duration_minutes = 45
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral"]
sticker_id = "bat-with-headphones"
combo_with = ["commute", "walk-no-purpose"]

[[entries]]
id = "music-deep-listen"
title = "music with nothing else"
neutral_title = "deep-listen music"
duration_minutes = 60
recurrence = "by-pick"
category = "passive-consumption"
tags = ["leisure", "neutral"]
sticker_id = "bat-headphones-closed-eyes"

[[entries]]
id = "social-media-check"
title = "scroll check (15min cap)"
neutral_title = "social media check"
duration_minutes = 15
recurrence = "daily-opt-in"
category = "passive-consumption"
tags = ["leisure", "neutral", "hard-cap", "doomscroll-risk"]
sticker_id = "bat-side-eye-at-phone"

[[entries]]
id = "news-check"
title = "news catch-up"
neutral_title = "news check"
duration_minutes = 15
recurrence = "configurable"
category = "passive-consumption"
tags = ["leisure", "neutral", "hard-cap"]
sticker_id = "bat-with-newspaper"

# ── active play ──────────────────────────────────────────────────────

[[entries]]
id = "video-games-session"
title = "video game time 🦇"
neutral_title = "video game session"
duration_minutes = 60
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-controller"
subbeats = [
  { id = "hydrate", duration_minutes = 2 },
  { id = "position-check-if-caged", duration_minutes = 1 },
  { id = "start", duration_minutes = 28 },
  { id = "break-at-30min", duration_minutes = 3 },
  { id = "continue", duration_minutes = 24 },
  { id = "wrap", duration_minutes = 2 },
]

[[entries]]
id = "video-games-session-caged"
kink_variant_of = "video-games-session"
title = "good boy gets video-game time while caged"
neutral_title = "video game session"
duration_minutes = 60
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "kink"]
sticker_id = "bat-controller-locked"

[[entries]]
id = "board-games"
title = "board games"
neutral_title = "board games"
duration_minutes = 90
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-dice-paw"

[[entries]]
id = "tabletop-rpg-session"
title = "ttrpg night"
neutral_title = "tabletop rpg session"
duration_minutes = 180
recurrence = "weekly-opt-in"
category = "active-play"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-d20"

[[entries]]
id = "puzzle-time"
title = "puzzle"
neutral_title = "puzzle time"
duration_minutes = 30
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-puzzle-piece"

[[entries]]
id = "hobby-project"
title = "good-boy hobby time 🦇"
neutral_title = "hobby project"
duration_minutes = 60
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "neutral", "user-customizable"]
sticker_id = "bat-craft-paws"

[[entries]]
id = "creative-burst"
title = "make-something time"
neutral_title = "creative burst"
duration_minutes = 45
recurrence = "by-pick"
category = "active-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-pencil"

[[entries]]
id = "learning-pleasure"
title = "learn-for-fun"
neutral_title = "learning (pleasure)"
duration_minutes = 30
recurrence = "daily-opt-in"
category = "active-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-graduation-hat"

# ── movement-play ────────────────────────────────────────────────────

[[entries]]
id = "walk-no-purpose"
title = "walk (no errand)"
neutral_title = "purposeless walk"
duration_minutes = 30
recurrence = "by-pick"
category = "movement-play"
tags = ["leisure", "neutral", "outdoors"]
sticker_id = "bat-walking"

[[entries]]
id = "dog-walk"
title = "good boy walks the doggy 🦇"
neutral_title = "dog walk"
duration_minutes = 35
recurrence = "per-dog-cadence"
category = "movement-play"
tags = ["leisure", "neutral", "pet-care-adjacent"]
sticker_id = "bat-with-dog-leash"
subbeats = [
  { id = "leash", duration_minutes = 1 },
  { id = "poop-bag", duration_minutes = 1 },
  { id = "water-for-dog", duration_minutes = 2 },
  { id = "route-pick", duration_minutes = 1 },
  { id = "actual-walk", duration_minutes = 25 },
  { id = "post-walk-dog-water", duration_minutes = 2 },
  { id = "paw-wipe", duration_minutes = 3 },
]

[[entries]]
id = "dog-play-time"
title = "play with doggy"
neutral_title = "dog play time"
duration_minutes = 20
recurrence = "daily"
category = "movement-play"
tags = ["leisure", "neutral", "pet-care-adjacent"]
sticker_id = "bat-throwing-ball"
subbeats = [
  { id = "fetch", duration_minutes = 8 },
  { id = "tug", duration_minutes = 6 },
  { id = "training-session", duration_minutes = 6 },
]

[[entries]]
id = "bike-ride-leisure"
title = "bike for fun"
neutral_title = "leisure bike ride"
duration_minutes = 45
recurrence = "by-pick"
category = "movement-play"
tags = ["leisure", "neutral", "outdoors"]
sticker_id = "bat-bicycle"

[[entries]]
id = "swim-leisure"
title = "swim"
neutral_title = "leisure swim"
duration_minutes = 45
recurrence = "by-pick"
category = "movement-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-swim-goggles"

[[entries]]
id = "playground-or-park-visit"
title = "park visit"
neutral_title = "park / playground visit"
duration_minutes = 60
recurrence = "by-pick"
category = "movement-play"
tags = ["leisure", "neutral", "outdoors"]
sticker_id = "bat-on-swing"

[[entries]]
id = "dance-around-the-house"
title = "dance break 🦇"
neutral_title = "dance around the house"
duration_minutes = 10
recurrence = "daily-opt-in"
category = "movement-play"
tags = ["leisure", "neutral", "joy-anchor"]
sticker_id = "bat-dancing"

[[entries]]
id = "stretching-not-yoga"
title = "lay on the floor"
neutral_title = "stretch on the floor"
duration_minutes = 10
recurrence = "by-pick"
category = "movement-play"
tags = ["leisure", "neutral"]
sticker_id = "bat-on-the-floor"

# ── social-recreation ────────────────────────────────────────────────

[[entries]]
id = "call-a-friend"
title = "call a friend"
neutral_title = "call a friend"
duration_minutes = 30
recurrence = "weekly-opt-in"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-on-phone"

[[entries]]
id = "call-family"
title = "call family"
neutral_title = "call family"
duration_minutes = 30
recurrence = "weekly-opt-in"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-on-phone-warm"

[[entries]]
id = "text-someone-i-havent"
title = "reach out (haven't talked in 3wks)"
neutral_title = "reach out to someone"
duration_minutes = 10
recurrence = "weekly-opt-in"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-typing-paw"

[[entries]]
id = "coffee-with-friend"
title = "coffee with a friend"
neutral_title = "coffee with a friend"
duration_minutes = 75
recurrence = "by-pick"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-with-coffee-cup"

[[entries]]
id = "dinner-with-friend"
title = "dinner with a friend"
neutral_title = "dinner with a friend"
duration_minutes = 120
recurrence = "by-pick"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-with-fork"

[[entries]]
id = "group-hangout"
title = "group hangout"
neutral_title = "group hangout"
duration_minutes = 180
recurrence = "by-pick"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-with-friends"
subbeats = [
  { id = "arrive", duration_minutes = 10 },
  { id = "be-present", duration_minutes = 160 },
  { id = "leave-on-time", duration_minutes = 10 },
]

[[entries]]
id = "online-game-with-friends"
title = "online game w/ friends"
neutral_title = "online game with friends"
duration_minutes = 90
recurrence = "by-pick"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-headset-controller"

[[entries]]
id = "voice-chat-discord-hangout"
title = "discord vc hangout"
neutral_title = "voice chat hangout"
duration_minutes = 60
recurrence = "by-pick"
category = "social-recreation"
tags = ["leisure", "neutral", "social"]
sticker_id = "bat-discord-paw"

# ── solo-decompress ──────────────────────────────────────────────────

[[entries]]
id = "bath-no-purpose"
title = "good boy gets a long bath 🦇"
neutral_title = "leisurely bath"
duration_minutes = 45
recurrence = "weekly-opt-in"
category = "solo-decompress"
tags = ["leisure", "neutral"]
sticker_id = "bat-in-bubble-bath"

[[entries]]
id = "nap-permitted-power"
title = "20min power nap"
neutral_title = "power nap"
duration_minutes = 20
recurrence = "daily-opt-in"
category = "solo-decompress"
tags = ["leisure", "neutral", "rest"]
sticker_id = "bat-zzz-small"

[[entries]]
id = "nap-permitted-full"
title = "90min full nap"
neutral_title = "full-cycle nap"
duration_minutes = 90
recurrence = "weekly-opt-in"
category = "solo-decompress"
tags = ["leisure", "neutral", "rest"]
sticker_id = "bat-zzz-big"

[[entries]]
id = "nothing-time"
title = "literally do nothing"
neutral_title = "nothing time"
duration_minutes = 15
recurrence = "daily-opt-in"
category = "solo-decompress"
tags = ["leisure", "neutral", "rest"]
sticker_id = "bat-staring-at-wall"
subbeats = [
  { id = "phone-out-of-arm-reach", duration_minutes = 1 },
  { id = "be", duration_minutes = 14 },
]

[[entries]]
id = "daydream-walk"
title = "no-phone daydream walk"
neutral_title = "daydream walk"
duration_minutes = 15
recurrence = "by-pick"
category = "solo-decompress"
tags = ["leisure", "neutral", "outdoors"]
sticker_id = "bat-cloud-thinking"

[[entries]]
id = "meditation"
title = "meditate"
neutral_title = "meditation"
duration_minutes = 15
recurrence = "configurable"
category = "solo-decompress"
tags = ["leisure", "neutral"]
sticker_id = "bat-lotus-paws"

[[entries]]
id = "journaling-personal"
title = "private journaling"
neutral_title = "personal journaling"
duration_minutes = 20
recurrence = "configurable"
category = "solo-decompress"
tags = ["leisure", "neutral", "private"]
sticker_id = "bat-with-diary"

# ── caged-play-affordances (kink-coded; hidden in neutral-mode) ──────

[[entries]]
id = "cage-comfort-check"
title = "good boy comfort check 🦇"
neutral_title = "comfort check"
duration_minutes = 5
recurrence = "every-30min-during-2hr-leisure"
category = "caged-play-affordances"
tags = ["leisure", "kink", "safety"]
sticker_id = "bat-paw-on-chest"
subbeats = [
  { id = "position-shift", duration_minutes = 1 },
  { id = "water", duration_minutes = 1 },
  { id = "breath", duration_minutes = 1 },
  { id = "still-good", duration_minutes = 2 },
]

[[entries]]
id = "locked-leisure-marker"
title = "this leisure block happens locked 🦇"
neutral_title = "leisure block (state marker)"
duration_minutes = 0
recurrence = "state-event"
category = "caged-play-affordances"
tags = ["leisure", "kink", "state-marker", "review-feed-surface"]
sticker_id = "bat-with-lock-on-collar"

[[entries]]
id = "safeword-aware-leisure"
title = "safeword stays open 🦇"
neutral_title = "safeword reminder"
duration_minutes = 1
recurrence = "at-50-percent-of-kink-leisure-block"
category = "caged-play-affordances"
tags = ["leisure", "kink", "safety", "soft-reminder"]
sticker_id = "bat-paw-up-stop"
safeword_remind = true

# ── recovery-leisure ─────────────────────────────────────────────────

[[entries]]
id = "aftercare-time"
title = "good boy aftercare 🦇"
neutral_title = "aftercare time"
duration_minutes = 45
recurrence = "post-scene"
category = "recovery-leisure"
tags = ["leisure", "kink", "recovery"]
sticker_id = "bat-with-blanket-tea"
subbeats = [
  { id = "water", duration_minutes = 3 },
  { id = "snack", duration_minutes = 7 },
  { id = "blanket", duration_minutes = 2 },
  { id = "cuddle-or-alone-time", duration_minutes = 25 },
  { id = "debrief", duration_minutes = 8 },
]

[[entries]]
id = "post-work-decompress"
title = "transition off work"
neutral_title = "post-work decompression"
duration_minutes = 15
recurrence = "daily"
category = "recovery-leisure"
tags = ["leisure", "neutral", "transition"]
sticker_id = "bat-tie-loosen"

[[entries]]
id = "post-social-recovery"
title = "recharge after people"
neutral_title = "post-social recovery"
duration_minutes = 45
recurrence = "proportional-to-group-size-and-duration"
category = "recovery-leisure"
tags = ["leisure", "neutral", "introvert-care"]
sticker_id = "bat-in-cave"

[[entries]]
id = "sick-day-rest"
title = "actually sick — rest mode"
neutral_title = "sick day rest"
duration_minutes = 1440
recurrence = "opt-in-day-mode"
category = "recovery-leisure"
tags = ["leisure", "neutral", "rest", "day-mode"]
sticker_id = "bat-with-thermometer"

# ── long-cycle-leisure-cadence ───────────────────────────────────────

[[entries]]
id = "weekly-treat"
title = "friday-night flavor 🦇"
neutral_title = "weekly treat"
duration_minutes = 180
recurrence = "weekly"
category = "long-cycle-leisure-cadence"
tags = ["leisure", "neutral", "ritual"]
sticker_id = "bat-with-cake-slice"

[[entries]]
id = "monthly-day-off"
title = "monthly off-day"
neutral_title = "monthly day off"
duration_minutes = 1440
recurrence = "monthly"
category = "long-cycle-leisure-cadence"
tags = ["leisure", "neutral", "day-mode", "mini-vacation-overlay"]
sticker_id = "bat-hammock"

[[entries]]
id = "quarterly-mini-trip"
title = "weekend away"
neutral_title = "quarterly mini-trip"
duration_minutes = 4320  # 3 days
recurrence = "quarterly"
category = "long-cycle-leisure-cadence"
tags = ["leisure", "neutral", "trip", "ties-to-hv-f"]
sticker_id = "bat-with-tiny-suitcase"

[[entries]]
id = "annual-real-vacation"
title = "real vacation"
neutral_title = "annual vacation"
duration_minutes = 10080  # ~7 days
recurrence = "yearly"
category = "long-cycle-leisure-cadence"
tags = ["leisure", "neutral", "trip", "ties-to-hv-f"]
sticker_id = "bat-on-beach-towel"
```

---

## Phase HV-Q — Free vs strictly-kept mode + review-feed + AI-dom-agent + migration paths

**The big one.** Per user explicit direction: *"in free mode you're
free to change anything. in strictly kept mode all changes that you
make (or your claude) get bubbled up to a feed of reviews. like the
changes should still apply, since you made them but your dom/master
will know and write comments on them or will review them."*

### HV-Q.1 — Mode lock (free vs strictly-kept)

Per-calendar AND per-repo mode setting (default per-repo, override per-
calendar):

- [ ] **HV-Q.1.1** Add `mode.toml` at calendar-repo root with
      `mode = "free"` as the default on repo creation. Per-calendar
      override via `[calendars.<id>] mode = "..."` block.
- [ ] **HV-Q.1.2** Lock the contract: in `mode = "free"` user
      edits apply immediately, NO review surface, NO notification
      fan-out to a dom.
- [ ] **HV-Q.1.3** Lock the contract: in `mode = "strictly-kept"`
      user edits STILL APPLY (no blocking, no approval gate, no
      veto). Every commit to the repo triggers a review-feed entry
      under `reviews/<commit-sha>/`. The dom (real or AI) sees the
      diff + writes comments via the existing Phase YY cross-repo
      feedback mechanism.
- [ ] **HV-Q.1.4** Mode itself is committed to the repo at
      `mode.toml`. Mode changes appear in the git log just like
      any other change. Intentional: mode transitions ARE part of
      the boy's history. No mode flag is app-private or
      hidden-state.
- [ ] **HV-Q.1.5** Implement the resolver-side mode read on every
      HEAD-change scan; surface the active mode to the app chrome
      via the existing now-state pipeline.

### HV-Q.2 — Review-feed mechanic

- [ ] **HV-Q.2.1** When `mode = "strictly-kept"`: a post-commit
      hook (in-app, NOT a git hook on disk — this is the JGit
      commit-completion callback) materializes a
      `reviews/<commit-sha>/reviewable_change.md` file containing:
      commit SHA, author, timestamp, list-of-changed-paths,
      auto-generated summary (e.g. "added 3 events to cal-work
      for next week"; "moved Wednesday workout to Friday";
      "deviation logged for tonight's brush-teeth"), the actual
      diff hunks (in a collapsed markdown details section), and an
      empty `responses/` subdirectory.
- [ ] **HV-Q.2.2** Auto-summary generator: a small in-app
      function maps changed-path families to register-aware
      summaries (using `identity.toml`'s praise term where
      appropriate). Test fixtures cover the common diff shapes:
      new-event, moved-event, deleted-event, deviation-logged,
      recurrence-edit, attachment-add, mode-flip.
- [ ] **HV-Q.2.3** The dom's app picks up unreviewed entries via
      the existing Phase YY cross-repo resolver and surfaces them
      in a "Reviews" tab. Sorting: newest-first, with an unread
      pill per entry.
- [ ] **HV-Q.2.4** The dom leaves responses by writing to
      `reviews/<commit-sha>/responses/<dom-fingerprint>-<timestamp>.md`
      IN THE DOM'S OWN REPO (cross-repo feedback per Phase YY —
      write-back-target). Canonical reactions:
      `locked` / `collar` / `good-boy` / `paw` / `heart` /
      `fire` / `thumbsup` / `🦇` / `smirk`. Free-text comments
      below.
- [ ] **HV-Q.2.5** The boy's app surfaces responses on the
      original commit in their feed (the existing per-commit
      feedback surface, extended with the review-kind sealed
      type).
- [ ] **HV-Q.2.6** Auto-acknowledgement: a response with no text
      + a `good-boy` reaction is the cute-coded version of an
      LGTM. Render specially in the boy's feed (the dom looked,
      the dom approved, sometimes he doesn't need to say more).

### HV-Q.3 — AI-dom-agent persona

The new first-class mode. Per user explicit: *"even when single claude
can roleplay like a dom :3 the strictly kept single is kept by an AI
dom that controls their schedule which can be any agent since they have
git access."*

- [ ] **HV-Q.3.1** Lock: an AI-dom is just an agent (Claude Code,
      Claude Desktop with MCP, automated cron-Claude) with git RW
      access to the boy's repo and its own dom-side repo for
      storing responses. No special API. Standard SSH + JGit.
- [ ] **HV-Q.3.2** The boy chooses a **dom-persona** during
      wizard or settings: `stern-but-fair`, `playful-tease`,
      `kinky-affectionate`, `daddy-warmth`,
      `bratty-switch-energy`, `clinical-protocol`,
      `custom-prompt`. Each persona is a prompt-template stored
      at `~/.config/skb/dom-personas/<persona>.md` (app-private,
      NOT in the user's calendar repo — Claude reads this per
      session).
- [ ] **HV-Q.3.3** The dom-persona file describes register,
      frequency of intervention, tone, what kinds of commits
      trigger immediate vs. weekly-batch response.
- [ ] **HV-Q.3.4** Lock: persona prompts SHIP with the app as
      defaults; user can customize. Personas are NOT in the
      calendar repo (they're agent-tooling, not user-data).
- [ ] **HV-Q.3.5** The AI-dom runs on a cadence configurable by
      the user: `realtime` (responds to every commit within
      minutes), `end-of-day` (batches the day's commits into one
      review), `weekly` (Sunday-evening recap). Default:
      `end-of-day`.
- [ ] **HV-Q.3.6** The boy's calendar repo's `AGENTS.md` carries
      a section describing how to be an AI-dom for THIS boy: read
      `identity.toml` for praise + pronouns, see dom-persona at
      runtime, write reviews to
      `reviews/<commit>/responses/...`, never use unscheduled
      tone changes, never propose mode-changes the boy hasn't
      asked about, never withhold a response as a punitive
      gesture (silence is allowed but it has to be the cadence,
      not a register move).
- [ ] **HV-Q.3.7** Safety lock: AI-dom has explicit-content
      guardrails. Even a `kinky-affectionate` dom persona will
      NOT generate sexually explicit content unless the boy has
      explicitly opted in via a setting AND age-gated past K-6.
      Default kink-mode AI-dom is *suggestive-tone, no explicit
      content*. (See D-NEXT.K.)

### HV-Q.4 — Migration paths between modes and between doms

Per user explicit: *"a boy is kept by an ai dom for a few months,
enjoys the lifestyle and then wants to be domed by a real person (who
also probably uses claude let's be real) — or a boy is super addicted
to the lifestyle but has a 'toxic' dom but wants to not stay together
but enjoys the lifestyle.. so he has to be their own master for a
while."*

Locked migration flows:

- [ ] **HV-Q.4.1** **Free → strictly-kept-by-AI**: Settings →
      "I want to be kept" → pick dom-persona → confirm. Mode
      switch commit lands. AI-dom starts watching on next
      commit. The wizard re-runs Screen 4 (lifestyle) with the
      new selection.
- [ ] **HV-Q.4.2** **Free → strictly-kept-by-human**:
      Settings → "I want to be kept by [partner]" → triggers a
      deep-link offer to the partner (Phase RR share-this-repo
      flow). Partner accepts → the partner's repo becomes the
      write-back-target for reviews. Mode switch commit lands.
- [ ] **HV-Q.4.3** **Strictly-kept-by-AI → strictly-kept-by-
      human**: same as HV-Q.4.2; the AI-dom-persona is archived
      (a single commit notes the transition); the partner takes
      over. Old AI-dom reviews remain readable in history. The
      new human-dom's responses begin overlaying as soon as the
      handoff commit lands.
- [ ] **HV-Q.4.4** **Strictly-kept → free (un-kept)**:
      Settings → "I want to leave this dynamic" → 24h cooling-off
      period (the boy types a confirmation; the system surfaces
      a "are you sure" with the dom-persona's voice asking them
      to reconsider). Confirm again → mode switch commit. The
      kept-history remains in the repo as a record. The boy can
      re-enter strictly-kept at any time.
- [ ] **HV-Q.4.5** **Strictly-kept-by-human → self-keep**:
      special "the in-between" state. Mode stays `strictly-kept`
      but the write-back-target becomes the boy's OWN repo. The
      boy commits their own reviews on themselves — a
      self-discipline mode. Useful as a transition out of a
      toxic dynamic without losing the lifestyle. The wizard
      offers this as a peer option to "go fully free".
- [ ] **HV-Q.4.6** **Self-keep → strictly-kept-by-human (new
      dom)**: same as the standard handoff flow — deep-link
      offer to the new partner.
- [ ] **HV-Q.4.7** All transitions are COMMITTED. The git log is
      the authoritative record of who held the keys when. No
      transition is hidden-state; no transition is reversible
      without its own commit.

### HV-Q.5 — Toxic-dom safety affordances

Lock the safeties explicitly:

- [ ] **HV-Q.5.1** The boy ALWAYS retains write access to their
      own repo. `Strictly-kept` does NOT mean the dom can lock
      the boy out. (Per Phase B / ZZ the boy controls the SSH
      keys / OAuth tokens.)
- [ ] **HV-Q.5.2** The boy can revoke the dom's read access at
      any time (via Phase ZZ remote-removal). This is the "kick
      him out of my repo" affordance. Discoverable from
      Settings → Connections → [Dom's name] → "remove this dom".
- [ ] **HV-Q.5.3** Mode transitions back to `free` cannot be
      blocked by the dom — the cooling-off is just a
      confirmation, not a gate the dom can prolong.
- [ ] **HV-Q.5.4** The boy's app surfaces an always-visible
      "transition my mode" affordance even in the most-kept UI
      state. NOT buried. Per the kink-positive safety norm: the
      safeword surface is always reachable.
- [ ] **HV-Q.5.5** Document that the dom-side app receives a
      neutral notification on mode-flip ("the boy you keep has
      changed their mode to `free`") — informational, not a
      negotiation surface. The dom does NOT get a "veto" UI.
- [ ] **HV-Q.5.6** Self-keep is the fully-supported exit ramp:
      a kept-life-without-this-dom mode. Identity.toml carries
      over unchanged. All reviews remain readable as history.

---

## Phase HV-R — Pronouns + praise term wizard + identity.toml + the AGENTS.md exclusion

Per user explicit: *"please put into the wizzard at the beginning if
someone is a good boy, a good girl or other. basically they should be
able to choose their pronouns and their praise. this should be
changeable in the settings and can be commited into the calendar repos
for your own, but not into claude.md/agents.md this will be a special
mode."*

### HV-R.1 — `identity.toml` schema

- [ ] **HV-R.1.1** Lock the schema. Lives at calendar-repo-root
      `identity.toml`. IS committed.
- [ ] **HV-R.1.2** Inline FULL TOML schema:

```toml
# identity.toml — per-repo identity + tone preferences.
# Read by the app for rendering chrome, by agents for register-matching.
# This file IS committed; CLAUDE.md / AGENTS.md DO NOT carry this content
# (those are about architecture, not personal preferences).

[praise]
# Pick one or write your own. Used in template titles, dom-Claude register,
# briefing salutations, etc.
term = "good boy"   # one of: "good boy" | "good girl" | "good pet" | "good kitten" |
                    #         "good pup" | "good bun" | "good fox" | "good cub" |
                    #         "good thing" | "good one" | <custom string>

# Optional second praise — used in alternation so the chrome doesn't feel
# repetitive ("good boy ... good thing ... good boy ... good pet").
alt_terms = ["good thing", "sweet boy"]

[pronouns]
subject = "he"      # he | she | they | it | <custom>
object = "him"
possessive = "his"
reflexive = "himself"
# Multiple pronoun sets are supported — agents will alternate:
extra_sets = [
  { subject = "they", object = "them", possessive = "their", reflexive = "themself" }
]

[honorific_for_dom]
# What the boy calls his dom (or his AI-dom). Used in dom-Claude responses
# and in the boy's app chrome.
term = "Sir"        # one of: "Sir" | "Daddy" | "Master" | "Mistress" | "Owner" |
                    #         "Keeper" | "Captain" | <custom>

[tone]
# How register-y the chrome runs. Affects template title rendering, briefing
# salutations, dom-Claude verbosity.
register = "soft-kinky"   # one of: "soft-kinky" | "playful" | "warm-neutral" |
                          #         "clinical" | "strict-clinical"
emoji_density = "medium"  # off | light | medium | heavy
```

- [ ] **HV-R.1.3** Locked default: on repo creation, write a
      `identity.toml` with `praise.term = "good boy"`,
      `pronouns = he/him/his/himself`,
      `honorific_for_dom.term = "Sir"`,
      `tone.register = "soft-kinky"`,
      `tone.emoji_density = "medium"`. These defaults reflect the
      project's locked good-boy-coded register; the wizard's job
      is to confirm or override, not to ship empty.

### HV-R.2 — Wizard screen (extends LW)

Insert as **LW Screen 3.5** (between LW-D Alignment and LW-E
Lifestyle): **Praise + pronouns**.

- [ ] **HV-R.2.1** Title: *"what do we call you?"*
- [ ] **HV-R.2.2** Praise picker: 10 default chips
      (`good boy` / `good girl` / `good pet` / `good kitten` /
      `good pup` / `good bun` / `good fox` / `good cub` /
      `good thing` / `good one`) + a "custom" text input.
      Multi-select supported for alternation (writes
      `[praise].alt_terms`).
- [ ] **HV-R.2.3** Pronouns: `he` / `she` / `they` / `it` /
      `custom-set` (with subject/object/possessive/reflexive
      fields). Multi-set toggle writes
      `[pronouns].extra_sets`.
- [ ] **HV-R.2.4** Honorific (SKIPPED if alignment =
      `dominant` or `unaligned-private`): how you'd like a dom
      to address you / how you'd address your dom. Chips:
      `Sir` / `Daddy` / `Master` / `Mistress` / `Owner` /
      `Keeper` / `Captain` / custom.
- [ ] **HV-R.2.5** Tone register: `soft-kinky` / `playful` /
      `warm-neutral` / `clinical` / `strict-clinical`. Defaults
      to `soft-kinky` for kinky alignments,
      `warm-neutral` for `unaligned-private`.
- [ ] **HV-R.2.6** Emoji density: `off` / `light` / `medium` /
      `heavy`. Default `medium`.
- [ ] **HV-R.2.7** Bat-mascot sticker: `bat-holding-name-tag` —
      ears tilted, fang-flash. NEW LW-K sticker beat — added to
      LW-K's sticker list via HV-J.20 integration note.
- [ ] **HV-R.2.8** ALL fields revisable in Settings → Identity
      later. On save: write `identity.toml`, commit. In kept
      mode the commit goes through the review-feed like any
      other commit.

### HV-R.3 — Settings → Identity surface

- [ ] **HV-R.3.1** Same fields as the wizard screen.
- [ ] **HV-R.3.2** Extra: "Reset to wizard defaults" button.
- [ ] **HV-R.3.3** Extra: "Show me how the app will render with
      these" — a live-preview panel that renders, side-by-side
      with the form, a now-card + a template title + a briefing
      salutation + a dom-Claude response using the
      currently-pending `identity.toml` values. Updates
      live-on-edit.
- [ ] **HV-R.3.4** Identity changes commit to `identity.toml`.
      Mode-aware: in `strictly-kept` mode, identity changes go
      through the review-feed like any other commit.

### HV-R.4 — Why NOT in CLAUDE.md / AGENTS.md (explicit justification — LOCK as a decision)

- [ ] **HV-R.4.1** `CLAUDE.md` and `AGENTS.md` carry
      **architectural** guidance: the D.3 file layout, the
      entity ID scheme, recurrence-rule conventions, the
      no-index-file rule, etc. These are about HOW agents
      should structure their writes to the repo.
- [ ] **HV-R.4.2** `identity.toml` carries **personal** content:
      praise term, pronouns, tone preferences. These are about
      WHO the user is and WHAT register feels right to them.
- [ ] **HV-R.4.3** The split matters because:
      - `AGENTS.md` is loaded into agent context on every repo
        touch. We don't want it bloated with personal
        preferences that don't shape file-structure decisions.
      - The user may share their repo with collaborators (a
        partner, a sub, a friend) — `AGENTS.md` is shared
        editorial content; `identity.toml` is the OWNER's
        preference and should not propagate into how OTHER
        repos render.
      - When a boy migrates between doms (per HV-Q.4), their
        `identity.toml` is THEIRS and follows them; `AGENTS.md`
        is the repo's architectural rules and stays with the
        repo.
      - File layout is debuggable independently of register
        choices.
- [ ] **HV-R.4.4** `AGENTS.md` MAY contain a single bridge
      line: *"See `identity.toml` at repo root for the user's
      praise term, pronouns, and tone register — use these
      when generating content for this user."* That's the
      bridge. No further personal-preference content embedded.
- [ ] **HV-R.4.5** Lock as **D-NEXT.I**: *"Personal-preference
      content (praise, pronouns, tone) lives in `identity.toml`;
      architectural guidance lives in `AGENTS.md` / `CLAUDE.md`.
      AGENTS.md references identity.toml but does not embed its
      content."*

### HV-R.5 — Agent integration

- [ ] **HV-R.5.1** Agents (including dom-Claude in HV-Q.3) read
      `identity.toml` on session start.
- [ ] **HV-R.5.2** Template-title renderer resolves
      `{{praise}}` placeholders to the chosen term (with
      alternation across `alt_terms` if set).
- [ ] **HV-R.5.3** Briefing salutations use the chosen term +
      emoji density ("good morning, good boy 🦇").
- [ ] **HV-R.5.4** Dom-Claude response register alternates
      between the praise terms, uses chosen honorific in
      voice-of-boy ("yes Sir", "good morning Daddy"), uses
      emoji per density setting.
- [ ] **HV-R.5.5** Notification body text (when NOT
      lockscreen-suppressed by privacy flag) uses the chosen
      praise term + emoji density. When suppressed, falls back
      to the neutral-mode template body per AT-C.2.
- [ ] **HV-R.5.6** Sticker beat `bat-holding-name-tag` (ears
      tilted, fang-flash) renders on LW-Screen-3.5 final step;
      neutral-mode variant `bat-with-clipboard` for
      `tone.register = "warm-neutral"` and below.

---



This phase is the bridge to the canonical plan files. No code lands in
HV-J; it lists exact edits the integration agent must perform.

- [ ] **HV-J.1** `templates-demo-wizard.md` — extend Phase TW-I (the
      atomic-template surface) with four new template file
      entries: `atomic-household.toml`, `atomic-travel-prep.toml`,
      `atomic-flight-day.toml`, `atomic-vacation-daily.toml`.
      Add a TW-I.* sub-step per template referencing the LOCKED
      content here (HV-A through HV-D).
- [ ] **HV-J.2** `resolver.md` — add new phase `RV-S` (Supersedence)
      after the existing RV-N (atomic-activities). Reproduce
      invariants S1..S5 verbatim from HV-E.6 and the resolver-pass
      pseudocode from HV-E.2. Update the cache spec section to
      include the `event_visibility` table from HV-E.5.
- [ ] **HV-J.3** `data-model.md` — extend the calendar-frontmatter
      section with `supersedes`, `superseded_during`,
      `nonSuperseable` fields (HV-E.1). Add the new `overrides/`
      directory to the repo layout section, alongside `exceptions/`
      and `deviations/`. Document the `force-show` and
      `force-show-for-range` `kind` values.
- [ ] **HV-J.4** `decisions.md` — append four new D entries (let the
      integration agent renumber from current D-tip):
      - `D-NEXT.A` Supersedence is hide-not-delete (full text in
        HV-E.7).
      - `D-NEXT.B` Supersedence is non-cascading.
      - `D-NEXT.C` `nonSuperseable` exists at both calendar and
        event-tag granularity.
      - `D-NEXT.D` `overrides/` is a new directory parallel to
        `exceptions/` and `deviations/`.
- [ ] **HV-J.5** `main.md` — add a NEW phase letter (suggest `AAA`,
      title "Household + travel templates + vacation wizard +
      supersedence"). The phase has sub-steps mirroring HV-A through
      HV-I. Cross-reference Phase XX (atomic-activities), Phase E
      (resolver), Phase K (lifestyle wizard) as upstream
      dependencies. Alternatively the integration agent may fold the
      template-content sub-phases into Phase XX and lift HV-E
      (supersedence) into Phase E directly; HV-F / HV-G / HV-H land
      in the new AAA either way.
- [ ] **HV-J.6** `cli-tooling.md` — add three new CLI commands from
      HV-E.8 under a new CLI-S sub-section ("Supersedence + overrides
      CLI surface").
- [ ] **HV-J.7** `notifications-sharing-import.md` — no edits needed
      for this draft; supersedence does NOT affect notifications
      (hidden events simply don't schedule alarms — the resolver's
      Room cache invalidation in HV-E.5 cascades into alarm
      cancellation via the existing alarm-from-cache path).
- [ ] **HV-J.8** `ui-spec.md` — add HV-G entry-point spec under the
      Settings-screen and Calendar-detail sections. Add the
      "manage overlays" strikethrough+grey treatment from HV-E.3
      under the Manage-Overlays section. Also add the
      `AttachmentList` Composable spec (HV-M.4) under Event-detail
      screen; the notification-stacking expansion behavior (HV-N.7)
      under Notifications surface; the briefing-event render
      template (HV-N.6) under System-events surface.
- [ ] **HV-J.9** `data-model.md` — extend the event-frontmatter
      schema in DM-* with the LOCKED `attachments: List<Attachment>`
      array (HV-M.2 sealed kinds: link / qr / file / barcode /
      vcard / location), the LOCKED `reminders: List<Reminder>`
      array (HV-N.1 + HV-N.2 sealed kinds), the LOCKED
      `off_schedule: Boolean` derived flag, and the LOCKED
      `all_day: Boolean` interaction with `all_day_banner`
      reminders (HV-N.5). Extend calendar-frontmatter with the
      LOCKED `baseline_cadence` block (HV-N.4). Document the new
      `attachments/<event-id>/` repo-relative directory and the
      LFS-at-100KB rule (HV-M.3) alongside the existing
      `exceptions/`, `deviations/`, `overrides/` documentation.
      Add new section `DM-O — attachments + multi-reminder` (or
      next available letter).
- [ ] **HV-J.10** `notifications-sharing-import.md` — extend
      Phase M / NS-Y with the LOCKED multi-reminder model (HV-N.1
      through HV-N.3 defaults), the LOCKED briefing surfaces
      (HV-N.6 morning + evening on `cal-briefings`), the LOCKED
      notification stacking (HV-N.7), and the LOCKED
      `post_event_checkin` opt-in flow (HV-N.8). Add new section
      `NS-Z — briefings + multi-reminder + stacking`. Import
      surface (NS-import-*) gains the `.pkpass` and `.eml`
      attachment-lifting paths from HV-M.6.
- [ ] **HV-J.11** `templates-demo-wizard.md` — extend Phase TW-I
      with FOUR new template file entries:
      `atomic-adhd-anchors.toml` (HV-K), `atomic-medication.toml`
      (HV-L.A), `atomic-menstrual-cycle.toml` (HV-L.B), and the
      auto-generated `cal-briefings` system calendar with its
      morning + evening briefing recurring events (HV-N.6).
      Wizard composition rules:
      - Role-toggle "ADHD anchors" enables `atomic-adhd-anchors`.
      - Role-toggle "managing meds" enables `atomic-medication`
        AND surfaces a per-med wizard sheet for adherence entries
        from HV-K.4.
      - Role-toggle "menstrual cycle" enables
        `atomic-menstrual-cycle`; sub-toggle "pause kink-care
        during period?" wires up `cal-period-grace` per HV-L.B.5.
      - Briefings calendar always created, default
        `active_toggle = true`; user can disable.
- [ ] **HV-J.12** `main.md` — add a sub-step to Phase M for the
      multi-reminder + briefing surfaces (`M.8` — briefings calendar
      auto-scaffold + multi-reminder firing + notification
      stacking). Mention attachment-handling (HV-M) in Phase P
      (import/export) — add `P.N` sub-step "attachment-lift from
      .ics / .eml / .pkpass per HV-M.6". The new AAA phase from
      HV-J.5 absorbs HV-K through HV-O sub-phases too.
- [ ] **HV-J.13** `cli-tooling.md` — add three new CLI commands:
      `skb attach <event> <file>` (HV-M.8), `skb reminder add
      <event> <offset> <kind>`, `skb reminder rm <event> <index>`,
      `skb briefing show <date>` (renders the briefing body
      for the given date). Under a new CLI-T sub-section
      "Attachments + reminders + briefings".
- [ ] **HV-J.14** `decisions.md` — append four new D entries (let
      the integration agent renumber from current D-tip):
      - `D-NEXT.E` Default reminder cadences per template category
        (full table from HV-N.3).
      - `D-NEXT.F` Off-schedule detection algorithm — per-calendar
        `baseline_cadence` block; events outside the window are
        flagged; flagging is non-cascading (doesn't promote other
        events).
      - `D-NEXT.G` Briefing surfaces — `cal-briefings` system
        calendar; morning + evening briefing events with
        AUTO-GENERATED bodies; non-user-editable bodies.
      - `D-NEXT.H` `post_event_checkin` is opt-in per event /
        template; default off everywhere; inverted-habit default-by-
        schedule auto-completion remains the canonical behavior.
- [ ] **HV-J.15** `main.md` — note HV-Q creates a new sub-flow in
      Phase K wizard (the praise/pronouns Screen 3.5 from HV-R)
      and adds the mode-toggle to Phase S settings; HV-P simply
      adds atomic templates referenced from Phase XX (no new
      phase letter needed — `atomic-leisure.toml` joins the
      template family alongside the household / travel / vacation
      / adhd / medication / menstrual templates). The new
      AAA phase from HV-J.5 absorbs HV-K through HV-R sub-phases.
      Add an explicit cross-reference to Phase YY (cross-repo
      feedback) and Phase OO (cross-repo state) from the new
      AAA phase, since the review-feed contract from HV-Q.2
      builds directly on those.
- [ ] **HV-J.16** `decisions.md` — append five new D entries (let
      the integration agent renumber from current D-tip):
      - `D-NEXT.I` *Personal-preference content* (praise term,
        pronouns, honorific, tone register, emoji density) lives
        in `identity.toml` at calendar-repo root; *architectural
        guidance* lives in `AGENTS.md` / `CLAUDE.md`. `AGENTS.md`
        contains exactly one bridge line referencing
        `identity.toml`; it does NOT embed praise / pronoun /
        register content. Rationale: AGENTS.md is loaded into
        every agent context (don't bloat with personal content
        that doesn't shape file-structure decisions); calendar
        repos may be shared with collaborators (a partner / sub /
        friend) and AGENTS.md is shared editorial content while
        identity.toml is OWNER-private preference; when a boy
        migrates between doms the identity.toml follows him,
        AGENTS.md stays with the repo; file layout is debuggable
        independently of register choices. (Full text in HV-R.4.)
      - `D-NEXT.J` *Free-vs-kept mode contract*: per-calendar
        AND per-repo `mode = "free" | "strictly-kept"`, default
        per-repo override per-calendar; mode lives in committed
        `mode.toml` at calendar root (mode transitions ARE
        history); in kept mode every commit's diff materializes a
        `reviews/<commit-sha>/` entry consumed by the dom via
        Phase YY cross-repo feedback; commits APPLY immediately
        regardless of mode (no blocking, no approval gate, no
        veto). Dom responses are reactions
        (locked/collar/good-boy/paw/heart/fire/thumbsup/🦇/smirk)
        + free-text written to the dom's OWN repo and surfaced
        back via cross-repo pull.
      - `D-NEXT.K` *AI-dom-persona safety*: dom-Claude personas
        ship as app-private prompt templates at
        `~/.config/skb/dom-personas/<name>.md`, NOT in the
        calendar repo (they are agent-tooling not user-data);
        shipped personas are `stern-but-fair`, `playful-tease`,
        `kinky-affectionate`, `daddy-warmth`,
        `bratty-switch-energy`, `clinical-protocol`,
        `custom-prompt`; default kink-mode AI-dom emits
        *suggestive-tone-no-explicit-content* unless the boy has
        explicitly opted in via setting AND age-gated past K-6;
        AI-dom cadence configurable: realtime / end-of-day /
        weekly with default end-of-day.
      - `D-NEXT.L` *Mode-transition cooling-off + no-block-by-dom
        invariant*: transitions from `strictly-kept` → `free`
        require a 24h cooling-off confirmation by the boy; the
        cooling-off is a boy-side confirmation, NOT a dom-gate
        the dom can prolong; the boy ALWAYS retains write access
        to their own repo; the boy can revoke the dom's read
        access at any time via Phase ZZ remote-removal; the
        always-visible "transition my mode" affordance is
        reachable from the most-kept UI state (NOT buried);
        special migration target `self-keep` exists as the
        in-between transition out of a kept dynamic without
        losing the lifestyle — same `mode = "strictly-kept"` but
        write-back-target = the boy's own repo.
      - `D-NEXT.M` *Leisure-is-scheduled philosophy*: downtime,
        passive consumption, active play, movement-play,
        social-recreation, solo-decompress, caged-play
        affordances, recovery-leisure, and long-cycle leisure
        cadence (weekly-treat / monthly-day-off / quarterly-mini-
        trip / annual-real-vacation) are FIRST-CLASS scheduled
        atomic entries with inverted-habit defaults
        (default-by-schedule = done = took the rest). "I skipped
        my downtime to grind work" is its own deviation the
        schedule catches. Rationale: a good boy without
        scheduled play turns feral; the schedule lets
        master/dom see at-a-glance whether the boy is busy or
        available, and lets the boy himself say yes to free
        time without guilt because it is ON the schedule.
- [ ] **HV-J.17** `data-model.md` — append section
      `DM-P — mode + identity + reviews`:
      - LOCKED `mode.toml` schema at calendar-repo root:
        `mode = "free" | "strictly-kept"`; optional
        `write_back_target = "<repo-url-or-self>"`;
        `dom_persona = "<persona-name-or-null>"`;
        `dom_cadence = "realtime" | "end-of-day" | "weekly"`;
        `kept_since = "<ISO-8601>"`; per-calendar override
        block `[calendars.<id>] mode = "..."`.
      - LOCKED `identity.toml` schema (full text reproduced
        inline from HV-R.1 — `[praise]` / `[pronouns]` /
        `[honorific_for_dom]` / `[tone]`).
      - LOCKED `reviews/<commit-sha>/` directory layout:
        `reviewable_change.md` (commit SHA + author + timestamp
        + changed paths + auto-generated summary + collapsed
        diff hunks) plus `responses/<dom-fingerprint>-<ts>.md`
        files (one per dom response), each carrying a
        `reactions: [..]` frontmatter array drawn from the
        canonical reaction set
        (locked/collar/good-boy/paw/heart/fire/thumbsup/🦇/smirk)
        plus free-text markdown body.
      - Cross-reference: `cal-briefings` already covered in
        HV-N / DM-O.
- [ ] **HV-J.18** `shared-schedules.md` — extend Phase YY
      cross-repo feedback semantics with the review-feed
      contract: the existing per-commit feedback mechanism is
      reused; review-feed entries are a NEW sealed-kind of
      cross-repo feedback object (`kind = "review"` joining the
      existing reaction / comment kinds); the dom's write-back-
      target repo stores responses under
      `reviews/<commit-sha>/responses/`; cross-repo resolver
      surfaces unreviewed entries to the dom's "Reviews" tab and
      surfaces responses to the boy's per-commit feed. Phase OO
      (cross-repo state) gains a new pointer:
      `dom_persona_pointer` — a per-link state file recording
      which AI-dom-persona is currently active for which
      kept-link (allows the persona to be swapped without
      churning the cross-repo link itself).
- [ ] **HV-J.19** `cli-tooling.md` — under new CLI-U sub-section
      "Mode + dom + identity":
      - `skb mode <free|kept> [--cooling-off]` — flip mode;
        commits `mode.toml`; with `--cooling-off` runs the 24h
        confirmation flow.
      - `skb dom set-persona <persona-name>` — set the active
        AI-dom-persona; writes `dom_persona` in `mode.toml`.
      - `skb dom respond <commit-sha>` — open the dom-side
        response composer for the given commit; writes
        `reviews/<sha>/responses/<dom-fp>-<ts>.md` in the
        dom's own repo.
      - `skb dom cadence <realtime|end-of-day|weekly>` —
        configure when the AI-dom-agent fires.
      - `skb identity edit` — open `identity.toml` for editing;
        validates schema on save; in kept mode the resulting
        commit goes through the review-feed like any other
        commit.
      - `skb identity preview` — render a sample now-card +
        template title + briefing salutation + dom-Claude
        response using the current `identity.toml` (same
        live-preview as the Settings → Identity panel).
      - `skb review list` — list unreviewed `reviewable_change`
        entries (dom-side surface).
- [ ] **HV-J.20** `templates-demo-wizard.md` — extend Phase TW-I
      with a fifth+ new template file entry:
      `atomic-leisure.toml` (HV-P). Wizard composition rules:
      - `atomic-leisure` is ALWAYS scaffolded (leisure is
        first-class scheduled content per D-NEXT.M).
      - Role-toggle "has a dog" enables the `dog-walk` +
        `dog-play-time` family with the named 7 sub-beats.
      - Role-toggle "owns a video-game console / PC gaming"
        enables the `video-games-session` family including
        the kink variant *"good boy gets video-game time while
        caged"* (gated on K-mode).
      - Sub-toggle "kink leisure affordances" gates the
        cage-comfort-check + locked-leisure-marker +
        safeword-aware-leisure entries (default OFF until
        K-mode is ON).
      Also register the new wizard screen LW-Screen-3.5
      (praise + pronouns + honorific + tone register + emoji
      density) — see HV-R.2; sticker beat
      `bat-holding-name-tag` joins LW-K's sticker list.
- [ ] **HV-J.21** `ui-spec.md` — add four new surfaces:
      - *Reviews tab spec*: dom-side surface listing
        unreviewed `reviewable_change` entries grouped by date;
        per-entry preview shows boy's identity (praise term +
        honorific + bat sticker), auto-summary, and a
        reaction-strip + free-text composer; tapping a
        reaction with empty text writes a "good-boy LGTM"
        response.
      - *Mode-aware chrome*: app chrome carries a small
        always-visible mode pill (free / kept / self-keep)
        with the "transition my mode" affordance reachable
        from a long-press on the pill; in kept mode the
        chrome carries a small dom-presence indicator.
      - *Dom-persona picker UI*: settings sub-screen listing
        the six shipped personas + custom-prompt with a
        register preview-snippet per persona; per-persona
        cadence control (realtime / end-of-day / weekly).
      - *Identity preview panel*: live-preview rendered at
        Settings → Identity AND on LW-Screen-3.5 final-step;
        renders the now-card + a template title + a briefing
        salutation + a dom-Claude response using the
        currently-pending `identity.toml` values; "Reset to
        wizard defaults" button.

---

## Closing note

Aggregate entry count across this draft (HV-A..HV-R):

- `atomic-household.toml`: ~88 base entries + 4 kink variants across
  13 categories.
- `atomic-travel-prep.toml`: ~52 entries across 7 lead-time tiers,
  ~50 sub-beats within pack-* entries.
- `atomic-flight-day.toml`: 19 parameterized entries per flight leg.
- `atomic-vacation-daily.toml`: 15 anchors (11 self-care + 4 kink).
- `atomic-adhd-anchors.toml`: 34 anchors across 6 categories
  (hydration 5, food 5, meds adherence 8, body-state 5, cognitive
  4, sleep-hygiene 4, maintenance 6 — incl. hyperfocus-recovery
  trigger). All sub-beats validated against atomic envelope.
- `atomic-medication.toml`: 22 management entries (supply chain,
  emergency stash, vaccinations, specialist follow-ups,
  preventative screenings, bodywork). All `privacy_flag = true` by
  default.
- `atomic-menstrual-cycle.toml`: 12 entries (cycle anchors,
  contraception, preventative screenings, supplies); period-grace
  overlay opt-in for kink-care supersedence.
- `cal-briefings` system calendar: 2 recurring events (morning +
  evening briefing) with auto-generated bodies.
- `atomic-leisure.toml`: ~46 entries across 8 categories
  (passive-consumption 10, active-play 8, movement-play 8,
  social-recreation 8, solo-decompress 7, caged-play-affordances 3
  incl. one kink variant, recovery-leisure 4, long-cycle-leisure-
  cadence 4). One kink variant on `video-games-session`. The
  `dog-walk` atomic carries the named 7 sub-beats (leash /
  poop-bag / water-for-dog / route-pick / actual-walk /
  post-walk-dog-water / paw-wipe).
- `mode.toml`: per-calendar-repo mode contract
  (free / strictly-kept) committed; per-calendar override block.
- `identity.toml`: per-repo identity surface
  (praise / pronouns / honorific / tone / emoji density)
  committed. Locked separation: `AGENTS.md` carries only a
  one-line reference; never embeds personal content.
- `reviews/<commit-sha>/`: review-feed materialization in
  strictly-kept mode; auto-summary + collapsed diff +
  per-dom-response files (reactions
  locked/collar/good-boy/paw/heart/fire/thumbsup/🦇/smirk +
  free-text); dom-side responses write to dom's OWN repo via
  Phase YY write-back-target.
- AI-dom-personas: 6 shipped (stern-but-fair / playful-tease /
  kinky-affectionate / daddy-warmth / bratty-switch-energy /
  clinical-protocol) + custom-prompt; stored at
  `~/.config/skb/dom-personas/<name>.md` (app-private,
  agent-tooling, NOT in calendar repo); cadence realtime /
  end-of-day / weekly with default end-of-day; explicit-content
  guardrails default ON (opt-in setting + K-6 age-gate to
  unlock). Six migration paths locked (free → kept-by-AI /
  kept-by-human; kept-by-AI ↔ kept-by-human; kept → free with
  24h cooling-off; kept-by-human → self-keep ↔ kept-by-human).
  Toxic-dom safeties non-negotiable: boy always retains write
  access, can revoke dom read at any time, mode-flip cannot be
  blocked by dom, "transition my mode" always reachable.

Schema additions (LOCKED): `attachments: List<Attachment>` array on
events (sealed kinds: link / qr / file / barcode / vcard / location)
with Git-LFS aware storage + privacy inheritance; `reminders:
List<Reminder>` array on events (sealed kinds: heads_up /
all_day_banner / tomorrow_briefing / pre_event / at_start /
post_event_checkin); `baseline_cadence` block on calendars driving
off-schedule detection; notification stacking when reminders
collide; `post_event_checkin` opt-in surface tying back to the
inverted-habit model.

The kink register stays *furry-cute-subby-want-to-be-a-good-boy*
throughout; neutral mode is a complete swap, not a censor. Vacation
mode is *anti-slob-drift*, not *full-routine-during-rest*. Supersedence
is *temporal hide*, not delete; meds + pets + vet never get paused.
ADHD anchors are *gentle hand on the shoulder*, not surveillance;
default-by-schedule auto-completion means the user only acts on
deviation. Med + cycle data is K-2 private by default; lockscreen
previews stay opaque.
