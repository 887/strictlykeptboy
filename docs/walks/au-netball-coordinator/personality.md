# Persona — Marnie Faulkner, netball-club coordinator

> One realistic user persona for replayable QA walks. Invented neutral
> Australian name. Not a real public figure.

## Snapshot

- **Name:** Marnie Faulkner
- **Age:** 41
- **Location:** Wynnum, on the bayside of Brisbane, Queensland.
- **Family:** Two school-age kids — Tilly (10, plays netball at the
  club Marnie helps run) and Cooper (8, plays junior AFL on Saturdays).
  Partner works FIFO rosters, so the family calendar is often
  Marnie-against-the-world.
- **Jobs:**
  - **Part-time bookkeeper** — does the books for a couple of local
    bayside small businesses two-and-a-bit days a week, mostly from
    the kitchen table.
  - **Volunteer coordinator** for the **Wynnum Waders Netball Club** —
    unpaid, but it eats more hours than the bookkeeping. She wrangles
    training slots, court bookings, the other volunteer parents, the
    fixture calendar, and the dreaded school-holiday clinics.

## Devices

- **Phone:** Pixel 7a, Android 16. Her main everything-device. Lives
  in her back pocket at the courts.
- **Tablet:** an older Samsung Galaxy Tab she keeps on the kitchen
  bench as the "family command centre". She likes that strictlykeptboy
  goes two-pane on the tablet so she can see the week and the detail at
  once.
- No laptop habit. She has one for the bookkeeping software but does
  *not* want to live in it. Anything she can do on the phone, she does
  on the phone.

## Tech comfort

Moderately tech-savvy. She's the person at the club who "is good with
the app stuff", but she is **not** a developer. She has a GitHub
account because a previous club treasurer set the shared club repo up
and added her as a collaborator — she signs in with **GitHub over
HTTPS using the OAuth device-flow** (type a code on github.com,
approve, done). She has never generated an SSH key and would rather not.
She does not know what an RRULE is by name, but she absolutely
understands "every Tuesday during term, except the one we skip for the
public holiday".

She reads the words on screen. Jargon ("supersedence", "overlay
resolver") loses her; plain-language labels ("pause my routine while
we're away") keep her. **This is a QA signal: anywhere the UI shows the
internal term instead of the plain one, note it.**

## Why strictlykeptboy over Google Calendar

She tried to run the club on shared Google Calendars and it fell apart:

1. **Shared editing was a mess.** Three volunteer parents all had edit
   rights, nobody could tell who changed what, and a training time got
   silently moved with no trail. strictlykeptboy puts an **author chip**
   on every event — she can see at a glance that *Priya* moved Tuesday
   training, not a phantom.
2. **She wanted the club calendar separate from family, but layerable.**
   Google made her flip between accounts. strictlykeptboy's **overlay
   model** lets her run a **family** calendar and the **club** calendar
   side by side, toggle each on/off, and the resolver builds one day view.
3. **Finding a training slot that works for everyone** was endless group
   chats. The **common-time finder across repos** does the sweep for her.
4. **School holidays** wrecked her recurring routines every term — she'd
   manually delete a fortnight of events. strictlykeptboy's **vacation /
   supersedence overlay** pauses the routine calendars for the holiday
   window instead, and a **miss is just an explicit deviation file**, not
   a destroyed series.
5. She likes that **the data is just files in a repo** she can hand to
   someone else when she eventually stops volunteering. The club's
   schedule shouldn't die when she burns out.

## Her repos + identity / mode choices

| Repo | Kind | Remote / mode | Identity | Why |
|------|------|---------------|----------|-----|
| **Family** | own, **phone-only (no-origin)** | local-only, **free** mode | "Marnie" | Private household logistics. Never wants this on a server. The phone-only / no-origin path is exactly her comfort level. |
| **Wynnum Waders Netball** | **shared** repo (she's a collaborator) | GitHub, **HTTPS + OAuth device flow**, free mode | "Marnie F (Coord)" | The club's shared source of truth. Other volunteers commit too; she relies on author chips. |
| **Bookwork** | own, phone-only | local-only, free mode | "Marnie (books)" | Her bookkeeping deadlines + client visits. Kept off the cloud on purpose. |

She is firmly in **free** mode everywhere (not strictly-kept). The
lifestyle / dom-persona framing isn't her use case — she picked
**Unaligned / private** in the wizard so the kink surfaces stay off, and
she just wants a calm, plain calendar. **QA signal:** the wizard must let
her get to a working family calendar without ever forcing the lifestyle
framing on her.

## Frustrations (what makes her put an app down)

- Being made to set up an account / remote before she can even add one
  family event. She wants to *start local and add a remote later if ever*.
- Recurring events that can't skip a single week cleanly.
- Not knowing who changed a shared event.
- Apps that hide the "find a time everyone's free" feature, or make it
  only work inside one calendar.
- Jargon she has to decode.
- Losing a whole recurring series because she went on holiday.

## What success looks like

- First launch → a working **phone-only family calendar** with this
  week's kid logistics in it, in under five minutes, no sign-in.
- The **club training** recurs every Tuesday in term, she can **cancel
  one instance** for a public holiday without nuking the series, and the
  rest of the volunteers see her change with **her author chip** on it.
- When she needs a holiday-clinic slot, the **common-time finder**
  hands her ranked free slots across **family + club**, and she books one
  in two taps.
- When the **school holidays** start, she drops a **vacation overlay**
  over the holiday week, and her **routine calendars pause** (superseded)
  while the one-off clinic and anything she explicitly keeps stays on.
- She never has to think about Git. It just syncs.
