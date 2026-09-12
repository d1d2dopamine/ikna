<p align="center">
  <img src="docs/logo.png" alt="ikna" width="420">
</p>

<h1 align="center" id="ikna"></h1>

<p align="center">
  <strong>English</strong> · <a href="#русский">Русский</a>
</p>

<p align="center">
  A language-learning app that decides what deserves your attention today.<br>
  Android · Windows · Linux · no accounts · no telemetry
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press"><img src="https://img.shields.io/badge/release-0.10.0%20press-crimson?style=flat-square" alt="release"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?label=downloads&style=flat-square&logo=github&color=blueviolet" alt="downloads"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build&style=flat-square" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="license"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="android">
  <img src="https://img.shields.io/badge/Windows-x64-0078D4?style=flat-square&logo=windows11&logoColor=white" alt="windows x64">
  <img src="https://img.shields.io/badge/AppImage-x86__64-2E2E2E?style=flat-square&logo=appimage&logoColor=white" alt="AppImage x86_64">
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press"><strong>Download</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">Changelog</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Developer docs</a>
</p>

## What is ikna?

ikna teaches languages through **chunks**: a useful phrase, a natural sentence that contains it, and its meaning.

You do not build a giant card collection and then spend months managing it. ikna handles the daily load for you. Its **load governor** looks at upcoming reviews, backlog, recent accuracy and missed days, then decides whether new material fits today.

There is no streak to protect, no visible backlog counter, and no four-button confidence ritual. During review you make one decision: **know** or **do not know**. The scheduler handles the rest.

The app is designed around a simple idea: studying should take attention, not administration.

### The short version

- Learn phrases in context instead of isolated word pairs.
- Answer with two intentions: **know** / **do not know**.
- Let FSRS-6 schedule recognition, cloze and production separately.
- Let the governor control how much new material enters the system.
- Import ready-made decks, generate your own, or move from Anki.
- Keep review history on your device.

## Download

| Platform | File in the release |
| --- | --- |
| **Android** 10+ (`minSdk 29`), arm64 | `ikna-v0.10.0-press.apk` |
| **Android** 10+, older 32-bit ARM | `ikna-v0.10.0-press-legacy32.apk` |
| **Windows** x64, installer | `ikna-v0.10.0-press-windows-x64-setup.exe` |
| **Windows** x64, portable | `ikna-v0.10.0-press-windows-x64.zip` |
| **Linux** x86_64 | `ikna-v0.10.0-press-linux-x86_64.AppImage` |

**[Open the v0.10.0 press release](https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press)**

### Platform notes

**Android.** The main APK is for arm64 devices; older 32-bit ARM devices use the `-legacy32.apk`. Releases are signed with the repository key, so a new APK can install over the previous one without discarding your review history.

**Windows.** The installer is per-user and does not require administrator rights. The portable zip runs without a separate Java installation. The executable is unsigned, so SmartScreen may ask for confirmation on first launch.

**Linux.** Make the AppImage executable with `chmod +x` before launching it. No separate Java installation is required.

Desktop data is stored outside the application files, so replacing the executable does not delete cards or settings. See [`docs/DESKTOP.md`](docs/DESKTOP.md).

Android speech is included in the app but ships without a voice model. You can add a Kokoro or Piper model yourself; until then, ikna uses the voice already available on the phone. ikna never downloads voice models on its own. See [`docs/VOICE.md`](docs/VOICE.md).

## Why it feels different

### Two answers, not four ratings

Every review ends with one human decision: **I know this** or **I do not know this**. Swipe on mobile; use A/D on desktop after revealing the answer.

With enough clean local timing history, ikna can refine some successful answers to bounded HARD or EASY grades automatically. That happens on-device and never adds another button to the review screen. See [`docs/GRADING.md`](docs/GRADING.md).

### Three memories for every chunk

Each chunk is trained at three levels:

1. recognition,
2. cloze,
3. production.

FSRS-6 schedules them independently. Words inside the trained phrase also carry their own memory state, so knowledge can transfer between chunks.

### A governor for new material

The governor decides whether new material fits into the day before you start. It uses your own recent workload and performance instead of asking you to pick an arbitrary number of new cards.

The daily plan can shrink while you work. It only grows when you explicitly ask for more. See [`docs/GOVERNOR.md`](docs/GOVERNOR.md).

### Browse without grading

Once the required plan is complete, familiar cards can appear in a limited Browse mode. Browse is for reading. It records exposure, not a recall result, and does not replace required review.

### Local FSRS fitting

With enough scored history, ikna can fit FSRS parameters locally and validate them against recent held-out answers. Only an accepted fit becomes active. Existing review history and due dates are not bulk-rewritten.

Details: [`docs/FSRS-OPTIMIZER.md`](docs/FSRS-OPTIMIZER.md) · [`docs/FSRS-OPTIMIZER-INTEGRATION.md`](docs/FSRS-OPTIMIZER-INTEGRATION.md)

## Decks

You have three ways to get material into ikna:

### 1. Ready-made catalogue

The built-in catalogue contains decks generated from open corpora. Sentences come from [Tatoeba](https://tatoeba.org), and catalogue cards keep their public source reference and licence information.

The initial catalogue build contains **466,061 cards**, **216 decks** and **72 language pairs**. The available learning and meaning languages are:

English, Russian, Polish, Spanish, French, German, Italian, Portuguese, Chinese, Japanese and Korean.

The catalogue reports whether a language pair is well populated, thin, or unavailable before download. Source and pipeline details live in [`docs/SOURCES.md`](docs/SOURCES.md).

You can inspect the catalogue outside the app:

- [`releases/tag/catalog`](https://github.com/d1d2dopamine/ikna/releases/tag/catalog)
- [`index.json`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/index.json)
- [`en-ru-beginner.jsonl`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/en-ru-beginner.jsonl)

A beginner English-from-Russian catalogue deck is bundled with the Android app, so a clean installation starts with real study material before the first catalogue request.

### 2. Make your own deck

A deck is plain text with three columns:

```
get used to | It takes a while to get used to the noise. | to grow accustomed
```

ikna can give you a prompt for this format. Send it to any AI, paste the result back, and the importer handles common list, code-block and Markdown-table formatting. Invalid rows are skipped and reported.

Format and import rules: [`docs/DECKS.md`](docs/DECKS.md).

### 3. Move from Anki

Import an `.apkg` and ikna brings over text cards together with answer history, so scheduling can continue instead of starting from zero.

Pictures and audio are not imported. Suspended and buried cards stay in Anki. Collections above 300 MB are rejected rather than partially imported, and the file is only read, never modified.

Repeated imports update the same imported decks instead of creating duplicates. There is no export back to Anki.

More: [`docs/ANKI.md`](docs/ANKI.md).

## Pronunciation

Catalogue cards can include IPA pronunciation. Per deck, ikna can show IPA, a readable English respelling, or no pronunciation line.

Production prompts do not reveal the missing answer through pronunciation. See [`docs/PHONETICS.md`](docs/PHONETICS.md).

## Privacy and data

ikna has no accounts and no telemetry.

The network is used for static files you request: release checks, catalogue index/preview/deck files and public source links. Cards, answers, statistics and identifiers are not uploaded. Voice models are never fetched by ikna.

Review history is append-only. Android can export the log and settings to `Documents/ikna/`; desktop can create a portable backup. Restore replays answers through the scheduler instead of trusting copied schedule state.

See [`PRIVACY.md`](PRIVACY.md) and [`docs/UPDATES.md`](docs/UPDATES.md).

## Interface

ikna includes twelve palettes in two lightings, optional imported fonts and motion that can be disabled.

The interface is available in Russian, English, Polish, Spanish, French, German and Brazilian Portuguese.

Android also provides a home-screen widget and one daily reminder. Desktop currently ships without speech, widget or reminders.

## Build

Push to `main`, or run the `build` workflow manually, then download the `ikna-apk` artifact. CI provisions Gradle; the wrapper jar is not committed.

```
bash tools/voice/fetch-voice.sh                # once per clone: speech runtime
bash tools/catalog/fetch-bundled-pack.sh         # pinned starter catalogue deck
./gradlew assembleDebug                        # the app
./gradlew assembleRelease -Pikna.unsigned=true # unsigned, without the committed key
./gradlew testReleaseUnitTest                  # Android JVM tests
./gradlew :desktop:test                          # desktop/shared JVM tests
./gradlew :desktop:createReleaseDistributable    # desktop application image
```

The starter catalogue deck is pinned by size and SHA-256. If the catalogue was intentionally rebuilt, run:

```
bash tools/catalog/fetch-bundled-pack.sh --update
```

The Android speech runtime is fetched once per clone with `tools/voice/fetch-voice.sh`; CI does this automatically. No speech model is fetched or bundled.

Both debug and release builds use the repository keystore so updates remain installable over previous Android builds. The trade-off is documented in [`docs/KEYSTORE.md`](docs/KEYSTORE.md).

### Release

Bump the version in `app/build.gradle.kts`, then tag the same version with the space replaced by a dash:

```
git tag v0.10.0-press
git push origin v0.10.0-press
```

The release workflow verifies that the tag matches the build file, runs release gates, and attaches the Android APKs, Windows installer and portable zip, and Linux AppImage built from that tag.

Versioning rules: [`docs/VERSIONS.md`](docs/VERSIONS.md).

## Documentation

[`ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`LEARNING-ENGINE.md`](docs/LEARNING-ENGINE.md) ·
[`SCIENCE.md`](docs/SCIENCE.md) ·
[`IKNA-DATABASE.md`](docs/IKNA-DATABASE.md) ·
[`ROADMAP-0.11.md`](docs/ROADMAP-0.11.md) ·
[`DESIGN.md`](docs/DESIGN.md) ·
[`GOVERNOR.md`](docs/GOVERNOR.md) ·
[`GRADING.md`](docs/GRADING.md) ·
[`GRADING-IMPLEMENTATION.md`](docs/GRADING-IMPLEMENTATION.md) ·
[`FSRS-OPTIMIZER.md`](docs/FSRS-OPTIMIZER.md) ·
[`FSRS-OPTIMIZER-INTEGRATION.md`](docs/FSRS-OPTIMIZER-INTEGRATION.md) ·
[`PHONETICS.md`](docs/PHONETICS.md) ·
[`DESKTOP.md`](docs/DESKTOP.md) ·
[`DECKS.md`](docs/DECKS.md) ·
[`ANKI.md`](docs/ANKI.md) ·
[`SOURCES.md`](docs/SOURCES.md) ·
[`UPDATES.md`](docs/UPDATES.md) ·
[`VERSIONS.md`](docs/VERSIONS.md) ·
[`VOICE.md`](docs/VOICE.md) ·
[`KEYSTORE.md`](docs/KEYSTORE.md) ·
[`CHANGELOG.md`](CHANGELOG.md) ·
[`PRIVACY.md`](PRIVACY.md)

## License

ikna is free software under the **GNU General Public License, version 3 or (at your option) any later version**. See [LICENSE](LICENSE).

---

<p align="center">
  <img src="docs/logo.png" alt="ikna" width="420">
</p>

<h1 align="center" id="русский"></h1>

<p align="center">
  <a href="#ikna">English</a> · <strong>Русский</strong>
</p>

<p align="center">
  Приложение для изучения языков, которое само решает, сколько материала тебе сегодня действительно нужно.<br>
  Android · Windows · Linux · без аккаунтов · без телеметрии
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press"><img src="https://img.shields.io/badge/release-0.10.0%20press-crimson?style=flat-square" alt="release"></a>
  <a href="https://github.com/d1d2dopamine/ikna/releases"><img src="https://img.shields.io/github/downloads/d1d2dopamine/ikna/total?label=downloads&style=flat-square&logo=github&color=blueviolet" alt="downloads"></a>
  <a href="https://github.com/d1d2dopamine/ikna/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/d1d2dopamine/ikna/build.yml?branch=main&label=build&style=flat-square" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="license"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="android">
  <img src="https://img.shields.io/badge/Windows-x64-0078D4?style=flat-square&logo=windows11&logoColor=white" alt="windows x64">
  <img src="https://img.shields.io/badge/AppImage-x86__64-2E2E2E?style=flat-square&logo=appimage&logoColor=white" alt="AppImage x86_64">
</p>

<p align="center">
  <a href="https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press"><strong>Скачать</strong></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">Изменения</a>
  &nbsp;·&nbsp;
  <a href="docs/ARCHITECTURE.md">Документация</a>
</p>

## Что такое ikna?

ikna учит язык **кусками**: полезная фраза, естественное предложение с ней и её смысл.

Тебе не нужно сначала собирать огромную коллекцию карточек, а потом месяцами управлять ею. ikna сама регулирует дневную нагрузку. **Регулятор нагрузки** смотрит на будущие повторы, накопившийся долг, недавнюю точность и пропущенные дни, а затем решает, помещается ли сегодня новый материал.

Нет стрика, который страшно потерять. Нет растущего счётчика долгов. Нет четырёх кнопок уверенности. На повторении ты принимаешь одно решение: **знаю** или **не знаю**. Остальное делает планировщик.

Идея простая: внимание должно уходить на язык, а не на обслуживание системы карточек.

### Если совсем коротко

- Учишь фразы в контексте, а не пары «слово-перевод».
- Отвечаешь двумя намерениями: **знаю** / **не знаю**.
- FSRS-6 отдельно планирует узнавание, пропуск и производство.
- Регулятор сам ограничивает новый материал.
- Можно брать готовые колоды, делать свои или переехать из Anki.
- История ответов остаётся на устройстве.

## Скачать

| Платформа | Файл в релизе |
| --- | --- |
| **Android** 10+ (`minSdk 29`), arm64 | `ikna-v0.10.0-press.apk` |
| **Android** 10+, старый 32-битный ARM | `ikna-v0.10.0-press-legacy32.apk` |
| **Windows** x64, установщик | `ikna-v0.10.0-press-windows-x64-setup.exe` |
| **Windows** x64, portable | `ikna-v0.10.0-press-windows-x64.zip` |
| **Linux** x86_64 | `ikna-v0.10.0-press-linux-x86_64.AppImage` |

**[Открыть релиз v0.10.0 press](https://github.com/d1d2dopamine/ikna/releases/tag/v0.10.0-press)**

### По платформам

**Android.** Основной APK предназначен для arm64; старые 32-битные ARM-устройства используют `-legacy32.apk`. Релизы подписаны ключом из репозитория, поэтому новая версия ставится поверх старой без потери журнала ответов.

**Windows.** Установщик работает для текущего пользователя и не требует прав администратора. Portable zip запускается без отдельной Java. Файл не подписан сертификатом Windows, поэтому SmartScreen при первом запуске может запросить подтверждение.

**Linux.** Перед запуском AppImage нужно сделать исполняемым через `chmod +x`. Отдельная Java не требуется.

Данные desktop-версии хранятся отдельно от файлов приложения, поэтому замена исполняемого файла не удаляет карточки и настройки. Подробнее: [`docs/DESKTOP.md`](docs/DESKTOP.md).

На Android движок озвучки уже входит в приложение, но голосовой модели внутри нет. Можно добавить свою модель Kokoro или Piper; до этого ikna использует системный голос телефона. Само приложение голосовые модели не скачивает. Подробнее: [`docs/VOICE.md`](docs/VOICE.md).

## Чем ikna отличается

### Два ответа вместо четырёх оценок

Каждый повтор заканчивается одним человеческим решением: **знаю** или **не знаю**. На телефоне это свайп; на desktop после открытия ответа работают A/D.

Когда накопится достаточно чистой локальной истории времени ответа, ikna может автоматически уточнять часть успешных ответов до ограниченных HARD или EASY. Всё происходит на устройстве и не добавляет новых кнопок в сессию. Подробнее: [`docs/GRADING.md`](docs/GRADING.md).

### Три вида памяти у каждого куска

Каждый кусок проходит три уровня:

1. узнавание,
2. пропуск,
3. производство.

FSRS-6 планирует их независимо. Отдельные слова внутри изучаемой фразы тоже имеют своё состояние памяти, поэтому знание переносится между кусками.

### Регулятор нового материала

До начала сессии регулятор решает, помещается ли новый материал в сегодняшний день. Он смотрит на твою реальную нагрузку и результаты, а не просит заранее придумать произвольное число новых карточек.

Дневной план может уменьшаться по ходу работы. Увеличивается он только когда ты сам просишь ещё. Подробнее: [`docs/GOVERNOR.md`](docs/GOVERNOR.md).

### Browse без оценки

После обязательного плана знакомые карточки могут появляться в ограниченном режиме Browse. Это чтение, а не повторение: ikna записывает факт показа, но не превращает его в результат recall и не подменяет им обязательную сессию.

### Локальная подгонка FSRS

Когда истории достаточно, ikna может локально подобрать параметры FSRS и проверить их на недавних отложенных ответах. Активируется только принятый результат. Уже существующая история и назначенные даты массово не переписываются.

Подробности: [`docs/FSRS-OPTIMIZER.md`](docs/FSRS-OPTIMIZER.md) · [`docs/FSRS-OPTIMIZER-INTEGRATION.md`](docs/FSRS-OPTIMIZER-INTEGRATION.md)

## Колоды

Есть три способа добавить материал.

### 1. Готовый каталог

Встроенный каталог содержит колоды, собранные из открытых корпусов. Предложения берутся из [Tatoeba](https://tatoeba.org), а карточки сохраняют публичную ссылку на источник и сведения о лицензии.

Первая сборка каталога содержит **466 061 карточку**, **216 колод** и **72 пары языков**. Доступные языки обучения и значений:

английский, русский, польский, испанский, французский, немецкий, итальянский, португальский, китайский, японский и корейский.

До скачивания каталог показывает, насколько хорошо наполнена выбранная пара. Подробности об источниках и конвейере: [`docs/SOURCES.md`](docs/SOURCES.md).

Каталог можно посмотреть и без приложения:

- [`releases/tag/catalog`](https://github.com/d1d2dopamine/ikna/releases/tag/catalog)
- [`index.json`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/index.json)
- [`en-ru-beginner.jsonl`](https://github.com/d1d2dopamine/ikna/releases/download/catalog/en-ru-beginner.jsonl)

Beginner-колода английского с русскими значениями уже лежит внутри Android-приложения, поэтому после чистой установки можно начать с настоящего материала ещё до первого запроса к каталогу.

### 2. Своя колода

Формат простой: три столбца обычного текста.

```
get used to | It takes a while to get used to the noise. | привыкать
```

ikna может выдать готовый промпт для этого формата. Отправь его любому ИИ, вставь ответ обратно, и импортёр разберёт обычные списки, code blocks и Markdown-таблицы. Неподходящие строки будут пропущены и показаны в отчёте.

Формат и правила импорта: [`docs/DECKS.md`](docs/DECKS.md).

### 3. Переезд из Anki

Импортируй `.apkg`, и ikna перенесёт текстовые карточки вместе с историей ответов, чтобы расписание не начиналось с нуля.

Картинки и звук не переносятся. Приостановленные и отложенные карточки остаются в Anki. Коллекции тяжелее 300 МБ отклоняются целиком, а исходный файл только читается и никогда не меняется.

Повторный импорт обновляет уже созданные колоды вместо дубликатов. Экспорта обратно в Anki нет.

Подробнее: [`docs/ANKI.md`](docs/ANKI.md).

## Произношение

Каталожные карточки могут содержать IPA. Для каждой колоды можно выбрать IPA, читаемую английскую respelling-строку или полностью скрыть произношение.

На production-заданиях произношение не раскрывает пропущенный ответ. Подробнее: [`docs/PHONETICS.md`](docs/PHONETICS.md).

## Приватность и данные

В ikna нет аккаунтов и телеметрии.

Сеть нужна только для статических файлов, которые ты сам запрашиваешь: проверки релиза, каталога, предпросмотра, скачивания колоды и публичных ссылок на источники. Карточки, ответы, статистика и идентификаторы не загружаются. Голосовые модели приложение тоже не скачивает.

Журнал ответов append-only. На Android его вместе с настройками можно экспортировать в `Documents/ikna/`; desktop умеет создавать переносимый backup. При восстановлении ответы заново проигрываются через планировщик вместо слепого копирования состояния расписания.

Подробнее: [`PRIVACY.md`](PRIVACY.md) и [`docs/UPDATES.md`](docs/UPDATES.md).

## Интерфейс

В ikna есть двенадцать палитр в двух вариантах освещения, импорт собственных шрифтов и возможность полностью отключить анимации.

Интерфейс доступен на русском, английском, польском, испанском, французском, немецком и бразильском португальском.

На Android также есть виджет и одно ежедневное напоминание. Desktop сейчас выходит без озвучки, виджета и напоминаний.

## Сборка

Пуш в `main` или ручной запуск workflow `build`, затем скачать артефакт `ikna-apk`. Gradle ставится в CI; jar wrapper в репозитории не хранится.

```
bash tools/voice/fetch-voice.sh                # один раз на клон: движок озвучки
bash tools/catalog/fetch-bundled-pack.sh         # закреплённая стартовая колода
./gradlew assembleDebug                        # приложение
./gradlew assembleRelease -Pikna.unsigned=true # без коммитнутого ключа
./gradlew testReleaseUnitTest                  # Android JVM-тесты
./gradlew :desktop:test                          # desktop/shared JVM-тесты
./gradlew :desktop:createReleaseDistributable    # desktop application image
```

Стартовая колода закреплена по размеру и SHA-256. Если каталог был пересобран намеренно, выполни:

```
bash tools/catalog/fetch-bundled-pack.sh --update
```

Android-движок озвучки загружается один раз на клон через `tools/voice/fetch-voice.sh`; CI делает это автоматически. Голосовая модель при этом не скачивается и не кладётся в сборку.

Debug и release используют ключ из репозитория, чтобы новые Android-сборки устанавливались поверх предыдущих. Компромисс описан в [`docs/KEYSTORE.md`](docs/KEYSTORE.md).

### Релиз

Подними версию в `app/build.gradle.kts`, затем поставь тег с той же версией, заменив пробел дефисом:

```
git tag v0.10.0-press
git push origin v0.10.0-press
```

Release workflow проверяет совпадение тега и build-файла, запускает релизные проверки и прикладывает Android APK, Windows installer и portable zip, а также Linux AppImage, собранные из этого тега.

Правила версионирования: [`docs/VERSIONS.md`](docs/VERSIONS.md).

## Документация

[`ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`LEARNING-ENGINE.md`](docs/LEARNING-ENGINE.md) ·
[`SCIENCE.md`](docs/SCIENCE.md) ·
[`IKNA-DATABASE.md`](docs/IKNA-DATABASE.md) ·
[`ROADMAP-0.11.md`](docs/ROADMAP-0.11.md) ·
[`DESIGN.md`](docs/DESIGN.md) ·
[`GOVERNOR.md`](docs/GOVERNOR.md) ·
[`GRADING.md`](docs/GRADING.md) ·
[`GRADING-IMPLEMENTATION.md`](docs/GRADING-IMPLEMENTATION.md) ·
[`FSRS-OPTIMIZER.md`](docs/FSRS-OPTIMIZER.md) ·
[`FSRS-OPTIMIZER-INTEGRATION.md`](docs/FSRS-OPTIMIZER-INTEGRATION.md) ·
[`PHONETICS.md`](docs/PHONETICS.md) ·
[`DESKTOP.md`](docs/DESKTOP.md) ·
[`DECKS.md`](docs/DECKS.md) ·
[`ANKI.md`](docs/ANKI.md) ·
[`SOURCES.md`](docs/SOURCES.md) ·
[`UPDATES.md`](docs/UPDATES.md) ·
[`VERSIONS.md`](docs/VERSIONS.md) ·
[`VOICE.md`](docs/VOICE.md) ·
[`KEYSTORE.md`](docs/KEYSTORE.md) ·
[`CHANGELOG.md`](CHANGELOG.md) ·
[`PRIVACY.md`](PRIVACY.md)

Документация в `docs/` ведётся на английском.

## Лицензия

ikna распространяется под **GNU General Public License версии 3 или, по твоему выбору, любой более поздней версии**. Полный текст: [LICENSE](LICENSE).
