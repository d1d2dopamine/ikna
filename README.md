<p align="center">
  <img src="docs/logo.png" alt="ikna" width="420">
</p>

<h1 align="center" id="ikna"></h1>

<p align="center">▪</p>

<p align="center">
  <strong>English</strong> · <a href="#русский">Русский</a>
</p>

<p align="center">
  Anki, but reversed. The system feeds you; you never feed the system.<br>
  Android · offline · no accounts · no internet permission
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.1.0-proof"><img alt="release" src="https://img.shields.io/badge/release-0.1.0%20proof-crimson"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img alt="downloads" src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?logo=github&label=downloads&color=blueviolet"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img alt="build" src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build"></a>
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
  <a href="https://kotlinlang.org"><img alt="made with" src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?logo=kotlin&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/download/v0.1.0-proof/ikna-v0.1.0-proof.apk"><strong>Download</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">Changelog</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Developer docs</a>
</p>

---

## 🧩 What it is

ikna teaches a language in **chunks**: a short phrase, one natural sentence that
contains it, and what it means. Not a word and its translation — a phrase caught
in the wild, with the sentence that makes it obvious.

One idea no other flashcard app implements: a **Load Governor** that decides
*whether you are allowed new material today*, from a forecast of your upcoming
review load, your backlog, your recent accuracy and the days you missed. You do
not ration yourself. The app does it for you, before you can overcommit.

There is no "add card" button. There never will be.

Built for ADHD — not as a slogan, but as a list of constraints that rejected
features. No streaks. No guilt. No growing counter. No queue number. No choice
where a choice can be avoided. And a day that starts at four in the morning,
because that is when the previous one actually ends.

The name is lower case, always. A capital I is a bare vertical bar in most
sans-serif faces and reads as a lower case L, so "Ikna" invites being read as
"lkna".

---

## ⬇️ Download

| Platform | File in the release |
| --- | --- |
| **Android** 10+ (`minSdk 29`) | `ikna-v0.1.0-proof.apk` |

**[Download ikna-v0.1.0-proof.apk](https://github.com/d1d2dopamine/ikna/releases/download/v0.1.0-proof/ikna-v0.1.0-proof.apk)** ·
[all files](https://github.com/d1d2dopamine/ikna/releases/tag/v0.1.0-proof)

Download it and open it; Android asks once whether to allow installing from this
source. There is no store listing. Every release is signed with the key committed
to this repository, so a new APK installs over the old one and **your answers
survive the update**.

---

## 🏷️ Versions: proof and press

A version of ikna is a number **and a word**: `0.1.0 proof`. The number counts
releases inside an epoch; the word says **which epoch** the build belongs to, and
therefore what kind of change to expect from the next one.

The words come from printing. A *proof* is the copy you read back and correct
before the press runs; the *press* is the run itself.

| Epoch | Meaning | What lands in it |
| --- | --- | --- |
| 📄 **proof** | **The proof stage.** Everything is set. What is left is reading it back and correcting what is wrong. | Testing, bug fixes, small corrections, wording, polish. No new pillars. |
| 🖨️ **press** | **The press run.** Opens only when `proof` has nothing left to correct. | The next generation of the app — whatever is big enough to deserve a new word. |

Epoch words are lower case, like the app's own name: `0.1.0 proof`, never
`0.1.0 PROOF`. A version is a label on a build, not an announcement.

Why the numbering restarted at `0.1.0`: the pre-epoch `0.x` line (up to `0.6.1`)
counted the app being assembled. The `proof` epoch counts a finished app being
hardened, so carrying `0.6.1` forward would have implied the two scales are
comparable. They are not.

One thing does **not** restart: `appVersionCode`. Android refuses to install an
APK whose code is lower than the installed one, and the only irreplaceable thing
in this app is the review log inside that install — so the internal counter keeps
climbing straight across the reset (`proof` starts at `100010000`). The version
**you read** restarted; the version **Android compares** never did.

Git tags carry the same string with the space turned into a dash, because a tag
cannot contain one: `v0.1.0-proof`. The release workflow refuses to publish if the
tag and the build file disagree.

---

## 👁️ What it looks like

A card, thrown. Answering is a gesture, not a row of buttons: throw the card away
from you when you knew it, towards you when you did not, and the direction is the
answer. Tapping it turns it over. Everything else — the estimate, the deck name,
the speak mark — lives in a thin row above, never on the card itself.

No screenshots here yet, on purpose: a still frame is a poor description of an app
whose whole answer mechanism is a movement, and a recording of a swipe looks like
a stutter. Build it and see it; it takes one `gradlew assembleDebug`.

---

## 🧠 Core design decisions

| Decision | Value |
| --- | --- |
| Unit of learning | chunk = phrase + carrier sentence + translation + `target_span` |
| Presentation levels | 0 recognition, 1 cloze, 2 production |
| Scheduler | FSRS-4.5 (17 parameters), local optimisation later |
| Second memory layer | component-level (lemma) state, one-directional influence |
| New-material control | `LoadGovernor` — a forecast-aware valve |
| Debt handling | amnesty pool, 20% of each session, never a visible backlog number |
| Streaks | none. The metric is *days with a session in the last 30* |
| Daily minimum | 1 card |
| Answering | one axis: left *not known*, right *known*. Nothing else is an answer |
| Audio | the phone's own speech engine, offline, beta, off by default. No voice ships in the APK |
| Colour | nine palettes, each in two lightings. Every pair passes 4.5:1, enforced by a test |
| Interface languages | Russian, English, Polish |
| Content | pre-baked packs, generated offline in `tools/genpack` |
| Network | none. The app has no internet permission |

---

## 🃏 How a session works

The day's plan is decided once and persisted. It can only shrink as you answer,
and it grows only when you ask for more — the counter at the top is not allowed to
go up while you work, because watching the finish line move away is how a session
ends early.

Undo is an inserted row, not an edit: the log is append-only, and taking an answer
back is recorded as a retraction of it.

How two directions produce four grades — timing, hesitation, and a rolling window
of your own answers rather than a fixed threshold — is written down in
[`docs/GRADING.md`](docs/GRADING.md).

---

## 🕓 When a day starts

Not at midnight. `dayStartHour = 4` in `governor.json`, and every day key, daily
counter, activity mark and "nothing new tonight" rule is measured from there.

Delayed sleep phase comes with the territory. The session at 01:00 is the
*evening* session; with a midnight boundary it was filed under a day that had not
begun yet, so the activity map grew a hole for a day that was actually worked, the
measured norm dropped, and the governor throttled new material because of a break
that never happened. Four in the morning is late enough to catch almost every real
night session and early enough that nobody works through it by accident.

---

## 🃋 Decks

| Deck | Chunks | Shipped |
| --- | --- | --- |
| `en-ru-core` — English core chunks | 121 | on |
| `pl-ru-core` — Polish core chunks | 121 | off |

A second language ships **off** on purpose: two active decks interleave two
languages inside one session, and the switch lives on the deck screen. Everything
else about a Polish chunk is identical to an English one — same three levels, same
FSRS state, same governor, same component layer — because a chunk is content, and
none of the machinery knows what language it is looking at.

Turning a deck off only stops **new** chunks coming from it. Everything already
started keeps its schedule and its history, so switching decks is never a decision
with consequences — and a decision with consequences is a decision that gets
avoided.

Both packs are built offline by the same generator, from a three-column TSV
(`phrase`, `carrier sentence`, `translation`):

```
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks_pl.tsv \
  --out app/src/main/assets/packs \
  --pack-id pl-ru-core --lang pl --title "Polish core chunks" --inactive --strict
```

Polish is tokenised but deliberately **not** lemmatised: Polish inflection needs a
real morphological analyser, and guessed lemmas are worse than none — a wrong
lemma merges unrelated words in the component layer and hands out credit nobody
earned. Surface forms aggregate less, but they never lie.

Your own pack can be imported from the deck screen.

---

## 💾 Your answers are the only backup that matters

Once a week, and on demand, two files are written to `Documents/ikna/`:

| File | What it holds |
| --- | --- |
| `ikna-reviews-YYYY-MM-DD.jsonl` | the append-only review log |
| `ikna-settings-YYYY-MM-DD.json` | theme, colours, font, language, reminder |

Deliberately outside the app sandbox, so they survive an uninstall, a factory
reset and a new phone. *Restore* takes either file and works out which one it is
from its contents.

Restoring the log does not copy a database over the app — it **replays the
history**. Every answer is fed back through the same scheduler, and the cards, the
word layer and every statistic are recomputed from it. That is why the log may only
ever gain rows: given the answers, everything else is derivable, including by a
future version with a different algorithm.

---

## 🎨 Appearance

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

A palette is not a theme: the same one exists in both lightings and keeps its hue
in both, so the light version is tinted paper rather than white with the colour
drained out. The nine are chosen from tiles painted in themselves rather than from
a list of names. Every pair of colours in every palette — including the warning
red, which each lighting defines for itself and which steps aside entirely when the
palette's accent is already a warm red — is held to 4.5:1 by a unit test, and the
hand-picked scheme gets the same check live, refusing combinations that cannot be
read.

Any `.ttf` or `.otf` on the phone can be used, and it is applied to the entire
interface — headings, body, section marks, captions and counters alike. The file is
validated before it is accepted, so a broken font cannot leave the app unreadable.

The wordmark in the bottom bar is the real artwork, tinted at runtime: the letters
take the ink of the current palette and the square dot over the `i` takes its
accent. The letterforms are never redrawn in code.

---

## 📲 Getting to a card

- **The widget.** One number on the home screen and a tap that opens the cards
  directly.
- **The reminder.** One notification a day, at your hour, and only if the day's
  minimum is still unmet. It never mentions a streak, a queue size or a number of
  missed days.

Both skip the deck list on purpose: tapping either one is already an answer to
"shall I study now", and asking again is where the intention is lost.

---

## ♿ Accessibility

Every mark in this app is drawn on a canvas, and a drawn shape has no text for a
screen reader to find — so each one is given a name, in all three languages, and
the switches are real toggles that announce their state. The system's per-app
language picker (Android 13+) lists the three languages through
`res/xml/locales_config.xml`.

---

## 🔒 Privacy

No servers, no accounts, no analytics, **no internet permission**. The details, and
what is stored where, are in [PRIVACY.md](PRIVACY.md).

---

## 🔨 Build

Push to `main`, or run the `build` workflow by hand, and download the `ikna-apk`
artifact. No Gradle wrapper jar is committed; CI provisions Gradle itself. Every
build carries the version written in `app/build.gradle.kts` — there is exactly one
place where a version number exists.

Building without the committed key is one flag, and produces an unsigned APK:

```
./gradlew assembleRelease -Pikna.unsigned=true
```

### 🗝️ Why the signing key is fixed

Gradle generates a throwaway `debug.keystore` on every clean machine. In GitHub
Actions that means **every build has a different signature**, so a new APK cannot
be installed over the old one and you lose your entire `reviews` history. ikna
signs *both* debug and release with one fixed keystore committed to this repository
as `ikna.keystore`. Nothing to generate, no repository secrets to configure. The
trade-off is written down in [`docs/KEYSTORE.md`](docs/KEYSTORE.md).

The review log is the only irreplaceable asset in this app. Packs can be rebuilt,
FSRS parameters can be recomputed, four months of answers cannot.

### 🚀 Release

Bump the two version lines at the top of `app/build.gradle.kts`, then tag the
commit with the same string, space replaced by a dash:

```
val appVersionName = "0.1.0 proof"
val appVersionCode = 100010000    // epoch offset + major * 100000 + minor * 10000 + patch * 100
```

```
git tag v0.1.0-proof
git push origin v0.1.0-proof
```

The `release` workflow refuses to continue if the tag and `appVersionName`
disagree, then runs the tests, builds a signed release APK, names it after the tag
and attaches it to the GitHub release. The About line in the app and the file on
the release page therefore cannot drift apart.

---

## 🗂️ Layout

```
app/src/main/java/dev/ikna/
  data/db         Room entities, DAOs, migrations
  data/pack       chunk pack models, loader, three-column parser
  data/repo       repositories, restore-by-replay
  data/export     weekly dump of the review log and the settings
  data/prefs      settings and font storage
  domain/fsrs     FSRS-4.5 + scheduler
  domain/governor GovernorConfig, LoadGovernor, ChunkSelector
  domain/session  session assembly
  domain/time     the 04:00 day boundary
  audio           the phone's speech engine, wrapped
  work            WorkManager jobs: daily plan, export, reminder
  widget          the home screen widget
  ui              Compose UI (onboarding, decks, session, stats, settings, theme, text)
app/src/debug/java/dev/ikna/ui/debug     the technical screen: governor log,
                                         plan rebuild. Debug builds only.
app/src/release/java/dev/ikna/ui/debug   its empty stand-in, so the screen is
                                         absent from a release rather than
                                         merely unreachable in one.
app/schemas       the database schema of every version, committed on purpose:
                  without the old one, a migration cannot be checked
tools/genpack     offline pack generator (Python)
docs              architecture, governor spec, grading design, keystore setup
```

---

## 📚 Docs

[`ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`GOVERNOR.md`](docs/GOVERNOR.md) ·
[`GRADING.md`](docs/GRADING.md) ·
[`KEYSTORE.md`](docs/KEYSTORE.md) ·
[`CHANGELOG.md`](CHANGELOG.md) ·
[`PRIVACY.md`](PRIVACY.md)

---

## ⚖️ License

ikna is free software, licensed under the **GNU General Public License, version 3
or (at your option) any later version**. The full text is in [LICENSE](LICENSE).

This licence covers **the whole repository**: every file, every commit, every
branch and every release — the ones published before this notice was added as well
as every future one. No per-file headers and no per-release notices are needed;
this section and the `LICENSE` file are the entire statement.

    Copyright (C) 2026 the ikna authors

    This program is free software: you can redistribute it and/or modify it under the terms of
    the GNU General Public License as published by the Free Software Foundation, either version 3
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <https://www.gnu.org/licenses/>.

For tools that ask: `SPDX-License-Identifier: GPL-3.0-or-later`.

---

<h2 align="center" id="русский">Русский</h2>

<p align="center">▪</p>

<p align="center">
  <a href="#ikna">English</a> · <strong>Русский</strong>
</p>

<p align="center">
  Anki наоборот. Система кормит вас, а не вы её.<br>
  Android · офлайн · без аккаунтов · без доступа в интернет
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.1.0-proof"><img alt="release" src="https://img.shields.io/badge/release-0.1.0%20proof-crimson"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img alt="downloads" src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?logo=github&label=downloads&color=blueviolet"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img alt="build" src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build"></a>
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
  <a href="https://kotlinlang.org"><img alt="made with" src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?logo=kotlin&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/download/v0.1.0-proof/ikna-v0.1.0-proof.apk"><strong>Скачать</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">История изменений</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Документация</a>
</p>

---

## 🧩 Что это

ikna учит язык **чанками**: короткая фраза, одно живое предложение с ней и
то, что она значит. Не слово и перевод — фраза, пойманная в живой речи,
вместе с предложением, из которого её смысл очевиден.

Одна идея, которой нет ни в одном другом приложении с карточками:
**Load Governor** решает, *можно ли вам вообще брать новое сегодня* — по прогнозу
будущей нагрузки, по долгу, по точности последних ответов и по пропущенным
дням. Вы не нормируете себя сами. Это делает приложение — до того, как вы
наберёте лишнего.

Кнопки «добавить карточку» здесь нет. И не будет.

Сделано для СДВГ — не как лозунг, а как список ограничений, который выбросил
целые функции. Никаких стриков. Никакого чувства вины. Никакого растущего
счётчика и номера в очереди. Никакого выбора там, где его можно не делать. И
день, который начинается в четыре утра — потому что именно тогда заканчивается
предыдущий.

Название всегда со строчной. Заглавная I в большинстве шрифтов — голая
вертикальная палка, которая читается как строчная L, так что «Ikna» просится
прочитать как «lkna».

---

## ⬇️ Скачать

| Платформа | Файл в релизе |
| --- | --- |
| **Android** 10+ (`minSdk 29`) | `ikna-v0.1.0-proof.apk` |

**[Скачать ikna-v0.1.0-proof.apk](https://github.com/d1d2dopamine/ikna/releases/download/v0.1.0-proof/ikna-v0.1.0-proof.apk)** ·
[все файлы](https://github.com/d1d2dopamine/ikna/releases/tag/v0.1.0-proof)

Скачать и открыть; Android один раз спросит, разрешить ли установку из этого
источника. В магазинах приложения нет. Каждый релиз подписан ключом из
этого репозитория, поэтому новый APK ставится поверх старого и **ваши ответы
переживают обновление**.

---

## 🏷️ Версии: proof и press

Версия ikna — это номер **и слово**: `0.1.0 proof`. Номер считает релизы внутри
эпохи; слово говорит, **к какой эпохе** относится сборка, а значит — чего ждать
от следующей.

Слова взяты из типографского дела. *Proof* — это корректурный оттиск, который
перечитывают и правят перед тиражом; *press* — сам тираж.

| Эпоха | Смысл | Что в неё попадает |
| --- | --- | --- |
| 📄 **proof** | **Корректура.** Всё уже собрано. Осталось перечитать и исправить. | Тесты, баги, мелкие правки, формулировки, полировка. Без новых опор. |
| 🖨️ **press** | **Тираж.** Открывается только когда в `proof` править больше нечего. | Следующее поколение приложения — то, что достаточно крупное, чтобы заслужить новое слово. |

Слова эпох пишутся со строчной, как и имя приложения: `0.1.0 proof`, а не
`0.1.0 PROOF`. Версия — это подпись на сборке, а не объявление.

Почему нумерация началась заново с `0.1.0`: доэпошная линия `0.x` (до `0.6.1`)
считала сборку приложения. Эпоха `proof` считает доводку готового приложения,
и если бы мы перенесли `0.6.1` вперёд, это бы означало, что две шкалы
сравнимы. Они не сравнимы.

Одно **не** обнуляется: `appVersionCode`. Android откажется ставить APK с кодом
меньше установленного, а единственное невосполнимое здесь — журнал ответов
внутри этой установки, так что внутренний счётчик продолжает расти сквозь
сброс (`proof` начинается с `100010000`). Версия, **которую читаете вы**,
обнулилась; версия, **которую сравнивает Android**, нет.

Теги в git несут ту же строку, только пробел заменён дефисом, потому что тег с
пробелом невозможен: `v0.1.0-proof`. Релизный воркфлоу отказывается публиковать,
если тег и сборка расходятся.

---

## 👁️ Как это выглядит

Карточка, которую бросают. Ответ — жест, а не ряд кнопок: отбросьте карточку
от себя, если знали, и к себе, если нет — направление и есть ответ. Нажатие
переворачивает её. Всё остальное — оценка времени, имя колоды, отметка озвучки
— живёт в тонкой строке сверху, никогда на самой карточке.

Скриншотов здесь пока нет, и это осознанно: стоп-кадр плохо описывает
приложение, в котором весь механизм ответа — это движение, а запись свайпа
выглядит как подёргивание. Соберите и посмотрите — это один
`gradlew assembleDebug`.

---

## 🧠 Основные решения

| Решение | Значение |
| --- | --- |
| Единица обучения | чанк = фраза + несущее предложение + перевод + `target_span` |
| Уровни подачи | 0 узнавание, 1 пропуск, 2 производство |
| Планировщик | FSRS-4.5 (17 параметров), локальная оптимизация позже |
| Второй слой памяти | состояние по словам (леммам), влияние только в одну сторону |
| Контроль нового | `LoadGovernor` — клапан, смотрящий в прогноз |
| Долг | амнистия, 20% каждой сессии, без видимого числа долга |
| Стрики | нет. Метрика — *дни с сессией за последние 30* |
| Дневной минимум | 1 карточка |
| Ответ | одна ось: влево *не знаю*, вправо *знаю*. Больше ответов нет |
| Озвучка | речевой движок телефона, офлайн, бета, выключена. Голосов в APK нет |
| Цвет | девять палитр, каждая в двух светах. Каждая пара держит 4.5:1, это проверяет тест |
| Языки интерфейса | русский, английский, польский |
| Контент | готовые паки, собранные офлайн в `tools/genpack` |
| Сеть | нет. У приложения нет разрешения на интернет |

---

## 🃏 Как идёт сессия

План дня решается один раз и сохраняется. По ходу ответов он может только
уменьшаться, а вырасти может лишь если вы сами попросите добавки: счётчику
сверху запрещено расти, пока вы работаете, потому что уходящая от вас финишная
линия — это ровно то, из-за чего сессия обрывается.

Отмена — это не правка, а новая строка: журнал только дописывается, и забранный
обратно ответ записывается как отзыв ответа.

Как из двух направлений получаются четыре оценки — по времени, заминке и
скользящему окну ваших же ответов, а не по фиксированному порогу — написано в
[`docs/GRADING.md`](docs/GRADING.md).

---

## 🕓 Когда начинается день

Не в полночь. `dayStartHour = 4` в `governor.json`, и от этой границы считаются
все ключи дня, дневные счётчики, отметки активности и правило «сегодня ничего
нового».

Сдвинутая фаза сна идёт в комплекте. Сессия в 01:00 — это *вечерняя* сессия; с
полуночной границей она попадала в день, который ещё не начался, поэтому в карте
активности появлялась дырка на месте реально отработанного дня, измеренная норма
падала, и говернор прикручивал новое из-за перерыва, которого не было. Четыре
утра — достаточно поздно, чтобы поймать почти любую ночную сессию, и достаточно
рано, чтобы никто не переработал через эту границу случайно.

---

## 🃋 Колоды

| Колода | Чанков | В сборке |
| --- | --- | --- |
| `en-ru-core` — английское ядро | 121 | вкл |
| `pl-ru-core` — польское ядро | 121 | выкл |

Второй язык поставляется **выключенным** осознанно: две активные колоды
перемешивают два языка внутри одной сессии, а переключатель живёт на экране
колод. Всё остальное в польском чанке идентично английскому — те же три уровня,
то же состояние FSRS, тот же говернор, тот же слой слов, — потому что чанк это
контент, и ни одна часть механизма не знает, на какой язык она смотрит.

Выключение колоды останавливает только **новые** чанки из неё. Всё уже начатое
сохраняет свой график и свою историю, поэтому переключение колод никогда не
является решением с последствиями — а решение с последствиями это решение,
которое откладывают.

Оба пака собраны офлайн одним генератором из трёх колонок TSV (`фраза`,
`несущее предложение`, `перевод`):

```
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks_pl.tsv \
  --out app/src/main/assets/packs \
  --pack-id pl-ru-core --lang pl --title "Polish core chunks" --inactive --strict
```

Польский токенизирован, но намеренно **не** лемматизирован: польскому словоизменению
нужен настоящий морфологический анализатор, а угаданная лемма хуже, чем никакая —
неверная лемма склеивает несвязанные слова в слое слов и выдаёт кредит, который
никто не заработал. Поверхностные формы обобщают меньше, зато никогда не врут.

Свой пак можно импортировать с экрана колод.

---

## 💾 Ваши ответы — единственный важный бэкап

Раз в неделю и по требованию в `Documents/ikna/` пишутся два файла:

| Файл | Что внутри |
| --- | --- |
| `ikna-reviews-ГГГГ-ММ-ДД.jsonl` | журнал ответов, только дописываемый |
| `ikna-settings-ГГГГ-ММ-ДД.json` | тема, цвета, шрифт, язык, напоминание |

Намеренно за пределами песочницы приложения, чтобы пережить удаление
приложения, сброс до заводских и новый телефон. *Восстановление* берёт любой из
двух файлов и само понимает по содержимому, какой это.

Восстановление журнала не копирует базу поверх приложения — оно **проигрывает
историю заново**. Каждый ответ прогоняется через тот же планировщик, и карточки,
слой слов и вся статистика считаются из него. Поэтому журналу разрешено только
прирастать строками: если есть ответы, всё остальное выводимо — в том числе
будущей версией с другим алгоритмом.

---

## 🎨 Внешний вид

Плоские прямые углы, рисованные от руки знаки, никаких компонентов Material — это
не вкусовщина, а требование: кнопка Material игнорирует форму из темы и
скругляется обратно при любой возможности.

Цвет — это два выбора, а не один. **Какая палитра:**

| Палитра | | Характер |
| --- | --- | --- |
| Уголь | *ember* | тёплый почти-чёрный и угли. По умолчанию |
| Библиотека | *library* | тёмная зелень и латунь |
| Чернила | *ink* | тёмно-синий и коралл |
| Слива | *plum* | баклажан и мята |
| Роза | *rose* | вино и розовый, который читается как маркер |
| Иней | *frost* | та, в которой нет ничего тёплого вообще |
| Фосфор | *phosphor* | фосфорная трубка: цветом стало само чернило, а не только акцент |
| Ноль | *zero* | чистые чёрный и белый, больше ничего |
| Нейтральная | *neutral* | серый, который не лезет в глаза |

**И как она освещена:** тёмная, светлая, как в системе, или четыре цвета,
выбранные вручную (фон, чернило, приглушённый, акцент).

Палитра — не тема: одна и та же существует в двух светах и держит свой тон в
обоих, поэтому светлая версия — это тонированная бумага, а не белый с
слитым цветом. Девять палитр выбираются плитками, каждая из которых
покрашена собой, а не из списка названий. Каждая пара цветов в каждой
палитре — включая предупреждающий красный, который каждый свет определяет
для себя и который вовсе уступает место, если акцент палитры сам тёпло-красный
— держит 4.5:1 под контролем юнит-теста, а ручная схема проверяется так же
на живую и отказывает сочетаниям, которые невозможно читать.

Любой `.ttf` или `.otf` с телефона подойдёт, и он применяется ко всему
интерфейсу — заголовки, тело, метки разделов, подписи и счётчики одинаково.
Файл проверяется до принятия, чтобы сломанный шрифт не оставил приложение
нечитаемым.

Логотип в нижней панели — это настоящая графика, тонируемая на ходу: буквы
берут чернило текущей палитры, а квадратная точка над `i` — её акцент.
Сами буквы никогда не перерисовываются кодом.

---

## 📲 Как дойти до карточки

Виджет на главном экране показывает, сколько осталось сегодня, и по нажатию
открывает сессию сразу. Напоминание одно в день, время выбираете вы, и если
день уже закрыт — оно молчит.

Напоминание никогда не называет цифру долга и никогда не говорит об утрате:
сообщение вроде «72 карточки ждут» — это ровно то, что заставляет человека с
СДВГ закрыть уведомление и не открывать приложение вообще.

---

## ♿ Доступность

Свайп-ответ — не единственный вариант. Карточка отвечает на действия
скринридера (*знаю* / *не знаю* / *перевернуть*), все цели нажатия не меньше
44 dp, а анимации исчезают, если в системе включено уменьшение анимаций.

---

## 🔒 Приватность

У приложения **нет разрешения `INTERNET`**. Не «мы не собираем данные», а
*отправить их технически невозможно*: любая попытка сетевого вызова будет
отклонена самой ОС. Нет аналитики, нет краш-репортов, нет аккаунтов.
Подробнее — [`PRIVACY.md`](PRIVACY.md).

---

## 🗝️ Почему ключ подписи лежит в репозитории

Ключ релизов зафиксирован и лежит открыто (`ikna.keystore`, пароль
`iknafixedkey`). Он ничего не защищает — он лишь гарантирует, что каждая сборка
ставится поверх предыдущей без удаления приложения.

Android отказывает в обновлении, если подпись изменилась, а удаление приложения
стирает журнал ответов — единственное, что в этом проекте невосполнимо.
Приватный ключ защитил бы только имя сборщика, а ценой была бы потеря истории
у всех, кто собирает сам. Подробнее — [`docs/KEYSTORE.md`](docs/KEYSTORE.md).

---

## 🔨 Сборка

```
git clone https://github.com/d1d2dopamine/ikna
cd ikna
./gradlew assembleDebug          # подписано отладочным ключом, можно ставить сразу
./gradlew test                   # юнит-тесты
./gradlew assembleRelease        # подписано ключом из репозитория
```

Нужны JDK 17 и Android SDK 35. Ничего больше устанавливать не надо.

Собрать релиз без подписи: `./gradlew assembleRelease -Pikna.unsigned=true`.

---

## 🚀 Релиз

Поставьте тег и отправьте — воркфлоу соберёт, подпишет и приложит APK к
релизу сам:

```
git tag v0.1.0-proof
git push origin v0.1.0-proof
```

Тег должен совпадать с версией в `app/build.gradle.kts`: пробел в версии
становится дефисом в теге. При расхождении сборка падает специально:
релиз, внутри которого лежит другая версия, — это релиз, который невозможно
отладить по багрепортам.

---

## 🗂️ Структура

```
app/src/main/java/dev/ikna/
  data/        база, паки, настройки, экспорт, восстановление
  domain/      FSRS, говернор, сборка сессии, граница дня
  ui/          Compose-экраны, тема, тексты интерфейса
  widget/      виджет главного экрана
  work/        план дня, напоминание, автобэкап
app/src/main/assets/
  governor.json        константы говернора и граница дня
  packs/               готовые колоды
  prompt/              системный промпт для создания колоды иишкой
tools/genpack/         генератор паков (Python, офлайн)
docs/                  архитектура, говернор, оценки, ключ
```

---

## 📚 Документация

| Файл | О чём |
| --- | --- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | из чего собрано приложение |
| [`docs/GOVERNOR.md`](docs/GOVERNOR.md) | как решается, можно ли давать новое |
| [`docs/GRADING.md`](docs/GRADING.md) | как два жеста превращаются в четыре оценки |
| [`docs/KEYSTORE.md`](docs/KEYSTORE.md) | почему ключ открыт |
| [`CHANGELOG.md`](CHANGELOG.md) | что менялось и когда |
| [`PRIVACY.md`](PRIVACY.md) | что приложение не может о вас узнать |

---

## ⚖️ Лицензия

```
ikna — приложение для изучения языка чанками
Copyright (C) 2026 the ikna authors

Эта программа — свободное ПО: вы можете распространять и/или изменять её
на условиях GNU General Public License, опубликованной Free Software
Foundation, версии 3 или (по вашему выбору) любой более поздней.

Программа распространяется в надежде, что окажется полезной, но БЕЗ КАКИХ-ЛИБО
ГАРАНТИЙ. Подробности см. в GNU General Public License.

Вместе с программой вы должны были получить копию GNU General Public
License. Если нет, см. <https://www.gnu.org/licenses/>.
```

`SPDX-License-Identifier: GPL-3.0-or-later`

---

<p align="center">▪ ▪ ▪</p>

<p align="center"><sub>made in the dark · Kotlin + Compose + FSRS</sub></p>
