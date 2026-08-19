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
  Android · no accounts · no telemetry · network only for static release and catalogue files
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.8.0-press"><img src="https://img.shields.io/badge/release-0.8.0%20press-crimson?style=flat-square" alt="release"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?label=downloads&style=flat-square&logo=github&color=blueviolet" alt="downloads"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build&style=flat-square" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="license"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="android">
  <img src="https://img.shields.io/badge/network-update%20check%20%2B%20catalogue-4b4b4b?style=flat-square" alt="network">
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/download/v0.8.0-press/ikna-v0.8.0-press.apk"><strong>Download</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">Changelog</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Developer docs</a>
</p>

---

## 🧩 What it is

ikna teaches a language in **chunks**: a short phrase, one natural sentence that
contains it, and what it means. Not a word and its translation — a phrase caught in
the wild, with the sentence that makes it obvious.

One idea no other flashcard app implements: a **load governor** that decides
*whether you are allowed new material today*, from a forecast of your upcoming
review load, your backlog, your recent accuracy and the days you missed. You do not
ration yourself. The app does it for you, before you can overcommit.

There is no "add card" button. There never will be. Decks are written by a model
from a prompt the app hands you, or generated offline — never typed in one card at a
time, because that is the homework nobody finishes.

Built for ADHD — not as a slogan, but as a list of constraints that rejected
features. No streaks. No guilt. No growing counter. No queue number. No choice where
a choice can be avoided. And a day that starts at four in the morning, because that
is when the previous one actually ends.

The name is lower case, always. A capital I is a bare vertical bar in most
sans-serif faces and reads as a lower case L, so "Ikna" invites being read as
"lkna".

---

## ⬇️ Download

| Platform | File in the release |
| --- | --- |
| **Android** 10+ (`minSdk 29`) | `ikna-v0.8.0-press.apk` (~40 MB) |

**[Download ikna-v0.8.0-press.apk](https://github.com/d1d2dopamine/ikna/releases/download/v0.8.0-press/ikna-v0.8.0-press.apk)** ·
[all files](https://github.com/d1d2dopamine/ikna/releases/tag/v0.8.0-press)

The file above is for every phone sold since roughly 2017. Older 32-bit ones
take `ikna-v0.8.0-press-legacy32.apk` from the same page: the same app, built
for the one architecture they can run.

Download it and open it; Android asks once whether to allow installing from this
source. There is no store listing. Every release is signed with the key committed to
this repository, so a new APK installs over the old one and **your answers survive
the update**.

One file, not two. There used to be a second `-voice` APK; the choice was never a
real one, so the speech engine is simply inside now and switched off until you turn
it on. It ships with **no model** — that part is yours to add, a Kokoro or Piper
folder from your own phone, in the language you are actually learning. The app never
downloads a model itself, and there is nowhere inside it to ask for one. Until you
add a model it
speaks with the voice your phone already has, and the voice screen always names who
is speaking. See [`docs/VOICE.md`](docs/VOICE.md).

---

## ✨ Features

- **Swipe-only answering.** Two directions carry four grades: how far and how fast
  you throw the card is the difference between *again* and *easy*. No buttons to
  choose between.
- **A governor that rations new material for you**, from your own recent numbers.
- **Three levels per chunk** — recognition, cloze, production — scheduled
  independently by FSRS-6.
- **A second memory layer** under the cards: individual words carry their own state,
  so a phrase you have never seen can already be partly known.
- **20% amnesty.** A card you missed comes back inside the same day, but not
  immediately and not all of them. There is no visible backlog number, ever.
- **Make a deck with any AI.** The app gives you a written prompt; you paste the
  answer back. Three columns, one line per card.
- **Share a deck** as plain text that imports on any other phone.
- **Twelve palettes**, each in two lightings, plus any font file on the phone.
- **A memory field of cells and fine pixel grain:** clear language marks,
  segmented progress and a soft 280 ms Shared Axis X transition. The route
  viewport stays fixed and the fade has one hand-off, so two screens never fight
  for the same frame. Forward and Back mirror; motion can be switched off instantly.
- **A widget and one reminder a day**, both opening the cards directly.
- **Russian, English, Polish, Spanish, French and German** interface, switchable from the system too. The picker stays folded until all languages are requested.
- **Your answers are append-only** and exported to `Documents/ikna/`, outside the
  app sandbox, so they survive an uninstall.
- **A catalogue of ready-made decks**, cut out of open corpora on the build server
  and not written by a model: every card names the sentence it came from, by
  number, on a public site. The licence and the credit are shown before the
  download, not after it.
- **Network only for static files you ask for.** The optional update check reads
  the releases page at most once a day. The catalogue reads its index, a bounded
  preview only after a tap, and the selected deck only after download is pressed.
  Voice models are explicit downloads too. Nothing is uploaded — no account,
  identifier, statistic, card or answer. See [`docs/UPDATES.md`](docs/UPDATES.md),
  [`docs/SOURCES.md`](docs/SOURCES.md) and [`docs/VOICE.md`](docs/VOICE.md).

---

## 🃏 How it works

1. **A chunk** is a phrase, a sentence containing it, and its meaning. The phrase is
   highlighted inside the sentence; that highlight is what is being trained.
2. **You answer by throwing the card** — away from you when you knew it, towards you
   when you did not. Distance and speed turn two directions into four grades, judged
   against a rolling window of your own answers rather than a fixed threshold:
   [`docs/GRADING.md`](docs/GRADING.md).
3. **FSRS-6 schedules each level separately**, including a separate model for
   repeats inside the same day, and answers also credit the
   individual words inside the trained phrase.
4. **The governor decides the size of the day** before you see it, and can refuse
   new material without refusing the session: [`docs/GOVERNOR.md`](docs/GOVERNOR.md).
5. **The plan is fixed once a day** and may only shrink while you work. It grows only
   when you ask for more.
6. **A day starts at 04:00**, so a session at one in the morning belongs to the
   evening it actually was.
7. **Nothing is ever overwritten.** The review log only gains rows, undo included —
   restoring it replays every answer through the scheduler instead of copying a
   database back.

The reasoning behind each of these, and the interface built on top of them, is in
[`docs/DESIGN.md`](docs/DESIGN.md).

---

## 🃋 Decks

| Deck | Chunks | Shipped |
| --- | --- | --- |
| `catalog-en-ru-beginner` — English from Russian, beginner | catalogue deck | on |

A deck can be turned off without consequences: only **new** chunks stop coming from
it, and everything already started keeps its schedule.

There is a **catalogue** in the app now, and it is the first thing the plus screen
offers: finished decks cut out
of open corpora on the build server. Sentences come from
[Tatoeba](https://tatoeba.org), the phrase on a card is cut out of the sentence it
appears in rather than written next to it, and the licence and the credit are on
screen before the download and inside the cards after it. The filters — what you
are learning, what the meanings should be in, which level — run on the phone,
over one small index.

**Getting one on the phone:** **+** → **A ready-made deck** → **OPEN THE CATALOGUE**,
then say what you are learning and which language the meanings should be in. A deck
already installed says **ALREADY DOWNLOADED** on its row instead of offering the same
file twice. An unfolded row can fetch three examples first; that request is capped
at 96 KiB even if a server ignores HTTP Range.

The English-from-Russian beginner deck is also bundled into the APK, so a clean
installation starts with real catalogue material before the first network request.
CI downloads that exact release asset and verifies its size and SHA-256 before
Gradle builds the app.

The square search mark on the home screen searches phrases, sentences and meanings
across installed decks only. It reads the local Room database and never opens a
socket. On a catalogue card, **Tatoeba #…** opens the public source sentence. Marking
one wrong can hide it and copy a paste-ready report containing only the deck, card
text and public source link — never review history or device data.

**Reading the catalogue without the app:**

- the release itself: [`releases/tag/catalog`](https://github.com/d1d2dopamine/ikna/releases/tag/catalog)
- the index the phone reads: [`index.json`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/index.json)
- one deck, to look at the format: [`en-ru-beginner.jsonl`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/en-ru-beginner.jsonl)

The first build came out at **466,061 cards** in **216 decks** over **72 language
pairs** — 66 of them full, 6 thin — every card under `CC BY 2.0 FR`, credited to
Tatoeba contributors, and every card naming the sentence it was cut out of by number.

| Can be learned | Can carry the meanings |
| --- | --- |
| English, Russian, Polish, Spanish, French, German, Italian, Portuguese | those eight, plus Chinese and Japanese |

Eight against ten, because a phrase is cut on word boundaries and a translation is
never cut at all. So Chinese and Japanese are already available as the language you
already know, and not yet as the language you are learning — the same goes for Thai
and Arabic, for the same reason, and it changes when the pipeline learns to
segment.

How well a pair is served is measured by the pipeline, not promised here: **full**
when the sieve drops little, **thin** when decks come out smaller than asked for,
and absent when the corpus has too few direct translations between those two
languages. The catalogue says which of the three your pair is before you download
anything. A deck's size is a result, not a promise.

Where the decks come from, what the sieve throws away, and why not AnkiWeb:
[`docs/SOURCES.md`](docs/SOURCES.md).

Your own deck is three columns of plain text, one line per card:

```
get used to | It takes a while to get used to the noise. | to grow accustomed
```

The plus in the bottom bar hands you a prompt written for that format. Send it to any
AI with your language and your topic, paste the reply back, and the deck is
imported — bullets, numbering, code fences and Markdown tables and all. Lines that
cannot work are skipped, counted, and quoted back with the reason.

Format, refusals, the deck screen and the offline generator:
[`docs/DECKS.md`](docs/DECKS.md).

---

## 🏷️ Versions

A version here is a number **and a word**: `0.8.0 press`. The word names the epoch
the build belongs to, the number counts releases inside it, and git tags replace the
space with a dash: `v0.8.0-press`.

What the words mean, what the numbers promise and how `appVersionCode` is built:
[`docs/VERSIONS.md`](docs/VERSIONS.md).

---

## 🔨 Build

Push to `main`, or run the `build` workflow by hand, and download the `ikna-apk`
artifact. No Gradle wrapper jar is committed; CI provisions Gradle itself.

```
bash tools/voice/fetch-voice.sh                # once per clone: speech runtime
bash tools/catalog/fetch-bundled-pack.sh         # pinned starter catalogue deck
./gradlew assembleDebug                        # the app
./gradlew assembleRelease -Pikna.unsigned=true # unsigned, without the committed key
./gradlew testReleaseUnitTest                  # the tests CI runs
```

There is one build. The speech runtime is a ten-megabyte `.aar` that this repository
does not store, so `tools/voice/fetch-voice.sh` has to run once before the first
build; both workflows run it themselves. No model is fetched and none is shipped —
that part comes from whoever uses the app.

Both debug and release are signed with one keystore committed to this repository, on
purpose: in CI every machine would otherwise generate its own signature, a new APK
could not install over the old one, and the review log inside it would be lost. The
trade-off is written down in [`docs/KEYSTORE.md`](docs/KEYSTORE.md).

### 🚀 Release

Bump the two version lines in `app/build.gradle.kts`, then tag the commit with the
same string, space replaced by a dash:

```
git tag v0.8.0-press
git push origin v0.8.0-press
```

The `release` workflow refuses to continue if the tag and the build file disagree,
then runs the tests, builds a signed APK, names it after the tag and attaches it to
the GitHub release.

---

## 📚 Docs

[`ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`DESIGN.md`](docs/DESIGN.md) ·
[`GOVERNOR.md`](docs/GOVERNOR.md) ·
[`GRADING.md`](docs/GRADING.md) ·
[`DECKS.md`](docs/DECKS.md) ·
[`SOURCES.md`](docs/SOURCES.md) ·
[`UPDATES.md`](docs/UPDATES.md) ·
[`VERSIONS.md`](docs/VERSIONS.md) ·
[`VOICE.md`](docs/VOICE.md) ·
[`KEYSTORE.md`](docs/KEYSTORE.md) ·
[`CHANGELOG.md`](CHANGELOG.md) ·
[`PRIVACY.md`](PRIVACY.md)

---

## ⚖️ License

ikna is free software under the **GNU General Public License, version 3 or (at your
option) any later version**. The full text is in [LICENSE](LICENSE).

The licence covers the whole repository — every file, every commit and every
release, the ones published before this notice as well as every future one.

---

<p align="center">
  <img src="docs/logo.png" alt="ikna" width="420">
</p>

<h1 align="center" id="русский"></h1>

<p align="center">▪</p>

<p align="center">
  <a href="#ikna">English</a> · <strong>Русский</strong>
</p>

<p align="center">
  Анки наизнанку. Система кормит тебя, а не ты её.<br>
  Android · без аккаунтов · без телеметрии · сеть только для статических файлов релиза и каталога
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.8.0-press"><img src="https://img.shields.io/badge/release-0.8.0%20press-crimson?style=flat-square" alt="release"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?label=downloads&style=flat-square&logo=github&color=blueviolet" alt="downloads"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build&style=flat-square" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="license"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="android">
  <img src="https://img.shields.io/badge/network-update%20check%20%2B%20catalogue-4b4b4b?style=flat-square" alt="network">
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/download/v0.8.0-press/ikna-v0.8.0-press.apk"><strong>Скачать</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">Изменения</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Документация</a>
</p>

---

## 🧩 Что это

ikna учит язык **кусками**: короткая фраза, живое предложение с ней и то, что она
значит. Не слово и перевод, а фраза в своём естественном окружении — вместе с
предложением, из-за которого она понятна.

Главная идея, которой нет ни в одном другом приложении с карточками — **регулятор
нагрузки**. Он решает, *можно ли тебе сегодня новый материал*, исходя из прогноза
будущих повторений, накопившегося долга, точности ответов и пропущенных дней.
Не надо себя дозировать самому — приложение сделает это раньше, чем ты успеешь
набрать лишнего.

Кнопки «добавить карточку» нет и не будет. Колоду пишет ии по промпту, который
выдаёт само приложение, либо генератор без сети — но никогда человек вручную по
карточке, потому что именно эта домашка никем не доделывается.

Сделано под СДВГ — не как лозунг, а как список ограничений, который выбрасывал
функции. Никаких стриков. Никакого чувства вины. Никакого растущего счётчика и
номера в очереди. Никакого выбора там, где его можно не делать. И день, который
начинается в четыре утра, потому что именно тогда заканчивается предыдущий.

Название всегда со строчной. Заглавная I в большинстве шрифтов — просто
вертикальная палка и читается как строчная L, так что «Ikna» просится быть прочитанным
как «lkna».

---

## ⬇️ Скачать

| Платформа | Файл в релизе |
| --- | --- |
| **Android** 10+ (`minSdk 29`) | `ikna-v0.8.0-press.apk` (~40 МБ) |

**[Скачать ikna-v0.8.0-press.apk](https://github.com/d1d2dopamine/ikna/releases/download/v0.8.0-press/ikna-v0.8.0-press.apk)** ·
[все файлы](https://github.com/d1d2dopamine/ikna/releases/tag/v0.8.0-press)

Файл выше — для любого телефона примерно с 2017 года. Для более старых
32-битных рядом лежит `ikna-v0.8.0-press-legacy32.apk`: то же самое
приложение, одна архитектура, которую они умеют.

Скачать и открыть; андроид один раз спросит, разрешить ли установку из этого
источника. Никакого магазина нет. Все релизы подписаны ключом из этого же
репозитория, так что новый apk ставится поверх старого и **твои ответы переживают
обновление**.

Файл один, не два. Раньше был второй, `-voice`, но выбор был ненастоящий, так что
движок озвучки теперь просто внутри и выключен, пока его не включишь. **Модели
внутри нет** — её добавляешь ты сам: папка Kokoro или Piper с твоего же телефона, на
том языке, который реально учишь. Модель приложение не скачивает само и попросить
её внутри негде. Пока модели нет, читает голосом, который уже есть в
телефоне, а экран озвучки всегда говорит, кто именно сейчас говорит. Подробности в
[`docs/VOICE.md`](docs/VOICE.md).

---

## ✨ Возможности

- **Ответ только свайпом.** Две стороны дают четыре оценки: как далеко и как
  быстро ты отбросил карточку — это разница между «заново» и «легко». Никаких
  кнопок, между которыми надо выбирать.
- **Регулятор дозирует новый материал за тебя**, от твоих же чисел.
- **Три уровня у каждого куска** — узнавание, пропуск, производство — и у каждого
  своё расписание по FSRS-6.
- **Второй слой памяти** под карточками: у отдельных слов есть своё состояние,
  так что ни разу не виденная фраза может быть уже частично знакомой.
- **Амнистия 20%.** Проваленная карточка вернётся в тот же день, но не сразу и
  не все. Никакого видимого числа долгов, никогда.
- **Колода любым ии.** Приложение даёт готовый промпт, ты вставляешь ответ
  обратно. Три столбца, одна строка — одна карточка.
- **Колодой можно поделиться** — обычным текстом, который встанет на любой другой
  телефон.
- **Двенадцать палитр**, каждая в двух освещениях, плюс любой шрифт с телефона.
- **Поле памяти из ячеек и мелкого пиксельного зерна:** чёткие языковые
  знаки, сегментированный прогресс и мягкий Shared Axis X на 280 мс.
  Область экрана зафиксирована, а затухания не пересекаются: две вкладки больше
  не спорят за один кадр. Вперёд и назад зеркальны; анимации можно отключить.
- **Виджет и одно напоминание в день**, оба открывают сразу карточки.
- **Русский, английский, польский, испанский, французский и немецкий** интерфейс, переключается и из системы. Список свёрнут, пока не понадобятся все языки.
- **Ответы только дописываются** и выгружаются в `Documents/ikna/` — вне песочницы
  приложения, чтобы пережить удаление.
- **Каталог готовых колод**, собранных не моделью, а из открытых корпусов на
  сервере сборки: у каждой карточки есть номер предложения, которое можно
  открыть на публичном сайте. Лицензия и автор — до скачивания, а не после.
- **Сеть только для статических файлов, которые запросили.** Необязательная
  проверка обновлений читает страницу релизов не чаще раза в сутки. Каталог
  загружает индекс, ограниченный предпросмотр только после нажатия и выбранную
  колоду только после команды скачать. Голосовые модели тоже скачиваются явно.
  Ничего не отправляется: ни аккаунта, ни идентификатора, ни статистики, ни
  карточек, ни ответов. Подробности в [`docs/UPDATES.md`](docs/UPDATES.md),
  [`docs/SOURCES.md`](docs/SOURCES.md) и [`docs/VOICE.md`](docs/VOICE.md).

---

## 🃏 Как это работает

1. **Кусок** — это фраза, предложение с ней и её смысл. Фраза выделена внутри
   предложения, и именно это выделенное и тренируется.
2. **Ответ — бросок карточки**: от себя, если знал, к себе, если нет. Дальность и
   скорость превращают две стороны в четыре оценки, и сравниваются они с твоими
   же последними ответами, а не с фиксированным порогом:
   [`docs/GRADING.md`](docs/GRADING.md).
3. **FSRS-6 ведёт каждый уровень отдельно**, отдельно учитывает повторы в тот же
   день, а ответ засчитывается ещё и словам
   внутри тренируемой фразы.
4. **Регулятор решает размер дня** до того, как ты его увидишь, и может отказать в
   новом материале, не отказывая в сессии: [`docs/GOVERNOR.md`](docs/GOVERNOR.md).
5. **План фиксируется раз в день** и пока ты работаешь может только уменьшаться.
   Растёт он только если ты сам попросишь ещё.
6. **День начинается в 04:00**, чтобы сессия в час ночи относилась к тому вечеру,
   которым она и была.
7. **Ничего никогда не перезаписывается.** В журнале ответов строки только
   добавляются, включая отмену — а восстановление прогоняет все ответы через
   планировщик заново, а не копирует базу обратно.

Почему всё именно так и как из этого собран интерфейс — в [`docs/DESIGN.md`](docs/DESIGN.md).

---

## 🃋 Колоды

| Колода | Кусков | В комплекте |
| --- | --- | --- |
| `catalog-en-ru-beginner` — английский с русскими значениями, начало | колода каталога | вкл |

Колоду можно выключить без последствий: перестанут приходить только **новые**
куски из неё, а всё уже начатое сохранит своё расписание.

В приложении теперь есть **каталог**, и это первое, что предлагает экран
добавления: готовые колоды,
собранные из открытых корпусов на сервере сборки. Предложения — из
[Tatoeba](https://tatoeba.org), фраза на карточке не написана рядом, а вырезана из
того же предложения, а лицензия и автор видны до скачивания и остаются
внутри карточек после. Фильтры — что учишь, на каком языке значения, какой
уровень — работают на телефоне, по одному маленькому списку.

**Как скачать колоду на телефоне:** **+** → **Готовая колода** → **ОТКРЫТЬ КАТАЛОГ**,
дальше выбрать, что учишь и на каком языке должны быть значения. Уже скачанная
колода пишет на своей строке **УЖЕ СКАЧАНА** вместо того, чтобы предлагать
тот же файл второй раз. В раскрытой строке можно сначала получить три примера;
даже если сервер проигнорирует HTTP Range, приложение прочитает не больше 96 КиБ.

Beginner-колода английского с русскими значениями теперь входит прямо в APK, так
что после чистой установки настоящие карточки каталога есть ещё до первого запроса
в сеть. CI скачивает конкретный файл релиза и до сборки проверяет его размер и
SHA-256.

Квадратная кнопка поиска на главном экране ищет фразу, предложение и перевод во
всех установленных колодах. Поиск читает только локальную Room-базу и не открывает
сеть. На карточке каталога строка **Tatoeba #…** открывает исходное предложение.
Действие «карточка неверна» может скрыть её и скопировать готовый отчёт: только
колода, текст и публичная ссылка — без истории ответов и данных телефона.

**Где посмотреть каталог без приложения:**

- сам релиз: [`releases/tag/catalog`](https://github.com/d1d2dopamine/ikna/releases/tag/catalog)
- список, который читает телефон: [`index.json`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/index.json)
- одна колода, чтобы посмотреть формат: [`en-ru-beginner.jsonl`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/en-ru-beginner.jsonl)

Первая сборка вышла на **466 061 карточку** в **216 колодах** по **72 парам языков** —
66 из них full, 6 thin — всё под `CC BY 2.0 FR`, с указанием авторов Tatoeba, и
каждая карточка называет номер предложения, из которого она вырезана.

| Можно учить | Может быть языком значений |
| --- | --- |
| английский, русский, польский, испанский, французский, немецкий, итальянский, португальский | эти восемь плюс китайский и японский |

Восемь против десяти — потому что фраза вырезается по границам слов, а перевод
не режется вообще. Поэтому китайский и японский уже годятся как язык, который
ты уже знаешь, и пока не годятся как язык, который учишь — так же с таиским и
арабским, и по той же причине; это изменится, когда конвейер научится резать
текст без пробелов.

Насколько хорошо обеспечена пара языков — не обещание, а измерение конвейера:
**full**, когда сито отбрасывает мало, **thin**, когда колоды выходят меньше
заказанного, и пары нет вовсе, когда прямых переводов между этими двумя
языками в корпусе слишком мало. Каталог говорит это до того, как ты что-то
скачаешь. Размер колоды — результат, а не обещание.

Откуда берутся колоды, что выбрасывает сито и почему не AnkiWeb — в
[`docs/SOURCES.md`](docs/SOURCES.md).

Своя колода — это три столбца обычного текста, одна строка — одна карточка:

```
get used to | It takes a while to get used to the noise. | привыкать
```

Плюс в нижней панели выдаёт промпт, написанный под этот формат. Отправь его
любому ии, укажи язык и тему, вставь ответ обратно — и колода импортируется
вместе с маркерами списка, нумерацией, блоками кода и таблицами маркдауна.
Строки, которые не могут работать, пропускаются, считаются и показываются с
причиной.

Формат, правила отказа, экран колоды и генератор без сети — в
[`docs/DECKS.md`](docs/DECKS.md).

---

## 🏷️ Версии

Версия здесь — это номер **и слово**: `0.8.0 press`. Слово называет эпоху, к которой
относится сборка, номер считает релизы внутри неё, а в тегах git пробел заменяется
дефисом: `v0.8.0-press`.

Что значат слова, что обещают номера и как собирается `appVersionCode` — в
[`docs/VERSIONS.md`](docs/VERSIONS.md).

---

## 🔨 Сборка

Пуш в `main` или ручной запуск воркфлоу `build`, потом скачать артефакт
`ikna-apk`. Jar грейдл-раппера в репозитории нет — CI ставит грейдл сам.

```
bash tools/voice/fetch-voice.sh                # один раз на клон: движок озвучки
bash tools/catalog/fetch-bundled-pack.sh         # закреплённая стартовая колода
./gradlew assembleDebug                        # приложение
./gradlew assembleRelease -Pikna.unsigned=true # без коммитнутого ключа
./gradlew testReleaseUnitTest                  # тесты, которые гоняет CI
```

Сборка одна. Движок озвучки — это `.aar` на десять мегабайт, репозиторий его не
хранит, так что `tools/voice/fetch-voice.sh` надо один раз запустить перед первой
сборкой; оба воркфлоу делают это сами. Модель не скачивается и не кладётся внутрь:
её добавляет тот, кто пользуется приложением.

И debug, и release подписаны одним ключом, лежащим в репозитории, и это сделано
намеренно: иначе каждая машина в CI сгенерировала бы свою подпись, новый apk не
встал бы поверх старого, и журнал ответов внутри пропал бы. Компромисс разобран в
[`docs/KEYSTORE.md`](docs/KEYSTORE.md).

### 🚀 Релиз

Поднять две строки версии в `app/build.gradle.kts` и поставить на коммит тег с той
же строкой, где пробел заменён дефисом:

```
git tag v0.8.0-press
git push origin v0.8.0-press
```

Воркфлоу `release` откажется работать, если тег и файл сборки расходятся, а затем
прогонит тесты, соберёт подписанный apk, назовёт его по тегу и приложит к релизу
на гитхабе.

---

## 📚 Документация

[`ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`DESIGN.md`](docs/DESIGN.md) ·
[`GOVERNOR.md`](docs/GOVERNOR.md) ·
[`GRADING.md`](docs/GRADING.md) ·
[`DECKS.md`](docs/DECKS.md) ·
[`SOURCES.md`](docs/SOURCES.md) ·
[`UPDATES.md`](docs/UPDATES.md) ·
[`VERSIONS.md`](docs/VERSIONS.md) ·
[`VOICE.md`](docs/VOICE.md) ·
[`KEYSTORE.md`](docs/KEYSTORE.md) ·
[`CHANGELOG.md`](CHANGELOG.md) ·
[`PRIVACY.md`](PRIVACY.md)

Документация в `docs/` ведётся на английском.

---

## ⚖️ Лицензия

ikna — свободное программное обеспечение под **GNU General Public License версии 3
или (по твоему выбору) любой позднеей**. Полный текст — в [LICENSE](LICENSE).

Лицензия покрывает весь репозиторий — каждый файл, каждый коммит и каждый релиз,
выложенные и до этой оговорки, и после неё.
