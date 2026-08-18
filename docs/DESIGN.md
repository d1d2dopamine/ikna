# Design

Why the app looks and behaves the way it does. The scheduler is in
[`GOVERNOR.md`](GOVERNOR.md) and [`GRADING.md`](GRADING.md); this file is the
surface.

## What it looks like

A card, thrown. Answering is a gesture, not a row of buttons: throw the card away
from you when you knew it, towards you when you did not, and the direction is the
answer. Tapping it turns it over. Everything else — the estimate, the deck name,
the speak mark — lives in a thin row above, never on the card itself.

No screenshots, on purpose: a still frame is a poor description of an app whose
whole answer mechanism is a movement, and a recording of a swipe looks like a
stutter. Build it and see it; it takes one `gradlew assembleDebug`.

## Core decisions

| Decision | Value |
| --- | --- |
| Unit of learning | chunk = phrase + carrier sentence + translation + `target_span` |
| Presentation levels | 0 recognition, 1 cloze, 2 production |
| Scheduler | FSRS-6 (21 parameters), local optimisation later |
| Second memory layer | component-level (lemma) state, one-directional influence |
| New-material control | `LoadGovernor` — a forecast-aware valve |
| Debt handling | amnesty pool, 20% of each session, never a visible backlog number |
| Streaks | none. The metric is *days with a session in the last 30* |
| Daily minimum | 1 card |
| Answering | one axis: left *not known*, right *known*. Nothing else is an answer |
| Audio | the phone's own engine by default; a neural engine inside, for a model you add |
| Colour | nine palettes, each in two lightings. Every pair passes 4.5:1, enforced by a test |
| Interface languages | Russian, English, Polish |
| Network | one request, switchable off: is there a newer release. Nothing is uploaded, ever |

## A session

The day's plan is decided once and persisted. It can only shrink as you answer, and
it grows only when you ask for more — the counter at the top is not allowed to go up
while you work, because watching the finish line move away is how a session ends
early.

Undo is an inserted row, not an edit: the log is append-only, and taking an answer
back is recorded as a retraction of it.

## When a day starts

Not at midnight. `dayStartHour = 4` in `governor.json`, and every day key, daily
counter, activity mark and "nothing new tonight" rule is measured from there.

Delayed sleep phase comes with the territory. The session at 01:00 is the *evening*
session; with a midnight boundary it was filed under a day that had not begun yet,
so the activity map grew a hole for a day that was actually worked, the measured
norm dropped, and the governor throttled new material because of a break that never
happened. Four in the morning is late enough to catch almost every real night
session and early enough that nobody works through it by accident.

## Appearance

Flat right angles, hand-drawn marks, no Material components anywhere — not a style
preference but a requirement, since a Material button ignores the theme's shape
scheme and rounds itself back at every opportunity.

Colour is two choices, not one. **Which palette:**

| Palette | | Character |
| --- | --- | --- |
| Уголь | *ember* | warm near-black and ember. The default |
| Библиотека | *library* | dark green and brass |
| Чернила | *ink* | navy and coral |
| Слива | *plum* | aubergine and mint |
| Роза | *rose* | wine, and a rose that reads as a highlighter |
| Иней | *frost* | the one with nothing warm in it anywhere |
| Фосфор | *phosphor* | a phosphor tube: the ink itself is the colour, not just the accent |
| Ноль | *zero* | pure black and white, nothing else |
| Нейтральная | *neutral* | grey that gets out of the way |

**And how it is lit:** dark, light, as the phone is set, or four colours picked by
hand (background, ink, muted, accent).

A palette is not a theme: the same one exists in both lightings and keeps its hue in
both, so the light version is tinted paper rather than white with the colour drained
out. The nine are chosen from tiles painted in themselves rather than from a list of
names. Every pair of colours in every palette — including the warning red, which
each lighting defines for itself and which steps aside entirely when the palette's
accent is already a warm red — is held to 4.5:1 by a unit test, and the hand-picked
scheme gets the same check live, refusing combinations that cannot be read.

Any `.ttf` or `.otf` on the phone can be used, and it is applied to the entire
interface — headings, body, section marks, captions and counters alike. The file is
validated before it is accepted, so a broken font cannot leave the app unreadable.

The wordmark in the bottom bar is the real artwork, tinted at runtime: the letters
take the ink of the current palette and the square dot over the `i` takes its
accent. The letterforms are never redrawn in code.

## What is deliberately not customisable

A background of your own was designed and then dropped, and the reason is worth
keeping written down: **the app feeds you, you do not feed the app.**

Every hour spent choosing a wallpaper, a mascot or a badge is an hour that feels
like studying and is not. Apps that let you decorate them end up being decorated,
and the decoration becomes the thing the user comes back for. So the palette
chooses the whole screen at once, decks get one square of colour each, and there
is nothing here to arrange.

What is adjustable is what makes the text readable or the day workable: the
palette, four colours of your own, the font, the size of the day, the hour of the
reminder, which hand the controls sit under. Nothing that turns the app into a
project.

## Getting to a card

- **The widget.** One number on the home screen and a tap that opens the cards
  directly.
- **The reminder.** One notification a day, at your hour, and only if the day's
  minimum is still unmet. It never mentions a streak, a queue size or a number of
  missed days.

Both skip the deck list on purpose: tapping either one is already an answer to
"shall I study now", and asking again is where the intention is lost.

## Accessibility

Every mark in this app is drawn on a canvas, and a drawn shape has no text for a
screen reader to find — so each one is given a name, in all three languages, and the
switches are real toggles that announce their state. The system's per-app language
picker (Android 13+) lists the three languages through `res/xml/locales_config.xml`.

## Your answers are the only backup that matters

Once a week, and on demand, two files are written to `Documents/ikna/`:

| File | What it holds |
| --- | --- |
| `ikna-reviews-YYYY-MM-DD.jsonl` | the append-only review log |
| `ikna-settings-YYYY-MM-DD.json` | theme, colours, font, language, reminder |

Deliberately outside the app sandbox, so they survive an uninstall, a factory reset
and a new phone. *Restore* takes either file and works out which one it is from its
contents.

Restoring the log does not copy a database over the app — it **replays the
history**. Every answer is fed back through the same scheduler, and the cards, the
word layer and every statistic are recomputed from it. That is why the log may only
ever gain rows: given the answers, everything else is derivable, including by a
future version with a different algorithm.
