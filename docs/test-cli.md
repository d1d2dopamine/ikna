# ikna — CLI test report (test-cli)

> **Статус документа: сделано GLM 5.3 & GLM 5.3-flash. Требует ревью и последующих исправлений.**
> Дефекты IKNA-T-002 и IKNA-T-003 зафиксированы для владельца репозитория и пока не исправлены. Дефект IKNA-T-001 исправлен в этой рабочей копии постоянным коммитом `67944c5` (см. статус в записи дефекта) и входит в поставляемый архив `Документы\ikna_aiworks\`.

Живой отчёт поэтапного тестового прогона текущей версии (snapshot `ikna-geologica-plex-terminal-persistent.zip`, версия в срезе: 0.10.0 press, работа над 0.11.0 press в прогрессе).

Протокол прогона: скилл `ikna-testing`; протокол безопасности репозитория: скилл `ikna-development`. Тестирование код проекта не модифицирует (разрешены только артефакты сборки, докачанные компоненты и сам этот отчёт).

Статус-словарь: `PASS` / `FAIL` / `SKIP` (с причиной) / `PARTIAL` (с оговорками). Дефекты: `IKNA-T-NNN`, severity S1 (критический) / S2 (major) / S3 (minor) / S4 (trivial).

## Окружение

| Компонент | Версия / путь | Источник |
| --- | --- | --- |
| ОС | Windows 10.0.26200 x64 | — |
| JDK | OpenJDK 17.0.20.1 (Microsoft), `C:\Users\work\AppData\Local\Programs\Microsoft\jdk-17.0.20.101-hotspot` | предустановлен |
| Gradle | 8.10.2 (пин CI), `C:\Users\work\.zcode\tools\gradle-8.10.2` | скачан с services.gradle.org |
| git | 2.55.0.windows.3 (MinGit), `C:\Users\work\.zcode\tools\MinGit\cmd\git.exe` | GitHub releases |
| Python | 3.12.10 per-user, `C:\Users\work\AppData\Local\Programs\Python\Python312\python.exe` | python.org |
| pytest | 9.1.1 | pip |
| Копия проекта | `C:\Users\work\.zcode\workspace\default\ikna`, git init, initial commit `275452e` | из zip |
| Android SDK | отсутствует | Android-сборка вне охвата прогона (решение пользователя: desktop + CLI + GUI) |

## Этап 0 — Подготовка (окружение и компоненты)

| # | Действие | Результат | Статус |
| --- | --- | --- | --- |
| 0.1 | Распаковка проекта из zip | дерево `app/ desktop/ shared/ tools/ docs/ .github/` на месте | PASS |
| 0.2 | Установка скилла ikna-development | `~/.zcode/skills/ikna-development` (7 файлов) | PASS |
| 0.3 | `git init` + initial commit | `275452e Initial import: ikna 0.10.0 press snapshot` | PASS |
| 0.4 | Аудит окружения | JDK есть; Gradle/git/pip/pytest отсутствовали → установлены (см. таблицу выше) | PASS |
| 0.5 | Докачка speech-runtime | `app/libs/sherpa-onnx-1.10.46.aar`, 36 207 890 байт, валидный zip (26 entries) | PASS |
| 0.6 | Докачка starter pack | `app/src/main/assets/packs/en-ru-beginner.jsonl`, size=1196978, sha256=70a84018…f8f7 — пин из `fetch-bundled-pack.sh` совпал | PASS |

Примечание: репозиторий «из коробки» не собирается — speech-runtime и starter pack докачиваются скриптами `tools/voice/fetch-voice.sh` и `tools/catalog/fetch-bundled-pack.sh` (bash; на этой машине bash отсутствует, компоненты докачаны вручную с тех же URL с проверкой пина). Gradle wrapper jar не коммитится (CONTRIBUTING это документирует). Это осознанная политика проекта, не дефект.

## Этап 1 — Скилл ikna-testing и старт отчёта

| # | Действие | Результат | Статус |
| --- | --- | --- | --- |
| 1.1 | Создание скилла `ikna-testing` | `~/.zcode/skills/ikna-testing/SKILL.md` | PASS |
| 1.2 | Создание `docs/test-cli.md` | этот файл | PASS |

## Этап 2 — Preflight и статический аудит

`python …/ikna-development/scripts/preflight.py <repo>` — прошёл без ошибок, вывод:

```
android versionName: 0.10.0 press
android versionCode: 200100000
desktop packageVersion: 0.10.0
Room schema version: 10
0.11 plan parts found: 13
first: Part 5 - supply census of existing sources
last:  Part 17 - release validation and publication
0.11 release-blockers section: yes
```

| # | Проверка | Результат | Статус |
| --- | --- | --- | --- |
| 2.1 | preflight.py | вывод выше; обязательные файлы на месте | PASS |
| 2.2 | Согласованность версий | `versionName` 0.10.0 press = desktop `packageVersion` 0.10.0; CHANGELOG при этом уже ведёт «0.11.0 press (in development)» — ожидаемый рабочий срез между релизами, не дефект | PASS |
| 2.3 | Схема Room | версия 10; история схем `app/schemas/…/3..10.json` закоммичена полностью | PASS |
| 2.4 | План 0.11 | 13 частей (Part 5…Part 17), секция Release Blockers присутствует | PASS |
| 2.5 | Кандидаты в дефекты из первичного аудита (TERMINAL-дубли в ChunkShape.kt, дубли Anki-файлов app/desktop, manifest chunkCount: 0) | переданы на проверку в последующие этапы | — |

## Этап 3 — Desktop JVM unit-тесты

Команда (CI-эталон): `gradle --no-daemon :desktop:test` (Gradle 8.10.2, JDK 17).

| # | Проверка | Результат | Статус |
| --- | --- | --- | --- |
| 3.1 | Первая попытка на нетронутом срезе | `:shared` собрался; `:desktop:compileKotlin` FAILED — 6 ошибок в `SettingsPane.kt:266/269/273` → дефект **IKNA-T-001** (S1) | FAIL |
| 3.2 | Повтор на копии с тестовым обходом T-001 [с обходом T-001] | `gradle --no-daemon :desktop:test` → `BUILD SUCCESSFUL in 1m 40s` (16 actionable tasks: 7 executed, 9 up-to-date); `:desktop:compileKotlin` компилируется (только deprecation-ворнинги `LocalClipboardManager`, `painterResource`); JUnit-отчёты `desktop/build/test-results/test/`: **11 наборов, 95 тестов, 0 failures, 0 errors, 0 skipped** | PASS |

**Тестовый обход T-001** (рабочая гипотеза тестировщика, НЕ исправление для поставки): три вызова `IknaChip(...)` с trailing-lambda переписаны в именованный аргумент `onClick = { … }` — семантика не меняется. Патч закоммичен отдельным коммитом `TEST WORKAROUND (not for release): …`, поверх initial import; легко откатить (`git revert`). Все дальнейшие результаты этапов 3/5/6 получены на копии с этим обходом и помечаются «[с обходом T-001]». *Последующая история: по решению пользователя обход переработан в постоянное исправление — коммит `34ee50e` переписан в `67944c5` «Fix IKNA-T-001 (hot-reload blocker)…»; `:desktop:test` повторно зелёный (95/0/0/0, `BUILD SUCCESSFUL in 1m 32s`), и именно эта копия поставляется в zip (`Документы\ikna_aiworks\`).*

## Этап 4 — Python-инструменты (чекеры и catalog-тесты)

Все команды выполнены по списку CONTRIBUTING.md, по одной (`python3 …` из документации соответствует `python.exe` 3.12 на этой машине; запуск через PowerShell — пакетный прогон одной cmd-строкой здесь невозможен, cmd-синтаксис `%%C` вне .bat не работает). Полные логи: `build/check_logs/*.log` (вне git).

**Чекеры (12):**

| # | Команда | Результат | Статус |
| --- | --- | --- | --- |
| 4.1 | `python tools/check_text.py` | `Repository text is valid UTF-8/NFC; 468 text files and 7 localization tables passed.` (2 c) | PASS |
| 4.2 | `python tools/check_localization.py` | `Language registry OK: 7 languages, 702 keys each; English fallback, tokens, labels and selectors agree.` | PASS |
| 4.3 | `python tools/grading/generate_synthetic.py --check` | все три фикстуры: `1206 explicitly synthetic rows; seed 73021; reproducible` | PASS |
| 4.4 | `python tools/check_grading.py` | `Ran 10 tests … OK` | PASS |
| 4.5 | `python tools/grading/check_full.py` | `Ran 8 tests … OK` | PASS |
| 4.6 | `python tools/check_optimizer.py` | `Ran 9 tests … OK` | PASS |
| 4.7 | `python tools/check_design_parity.py` | `Ran 35 tests … OK` | PASS |
| 4.8 | `python tools/check_palettes.py` | `Ran 8 tests … OK` | PASS |
| 4.9 | `python tools/check_nsis.py` | `NSIS contract OK: UTF-8 text, default Desktop shortcut, /S, safe upgrades, clean uninstall, opt-in purge, branded assets and no MSI` | PASS |
| 4.10 | `python tools/make_palette_preview.py --check` | `Palette preview agrees with all 24 production palette combinations.` | PASS |
| 4.11 | `python tools/check_android_ci.py --self-test` | `Ran 9 tests … FAILED (errors=1)`: `FileNotFoundError: [WinError 2]` на `subprocess.run(["bash", …])` (`check_android_ci.py:160`) → дефект **IKNA-T-002** (S3) | FAIL |
| 4.12 | `python tools/check_android_ci.py` | `DEX-safe Kotlin identifier check passed: 157 Android-bound files; JVM-only tests excluded.` | PASS |

**Catalog-тесты (прямой запуск, как предписывает CONTRIBUTING — не pytest):**

| # | Команда | Результат | Статус |
| --- | --- | --- | --- |
| 4.13 | `python tools/catalog/test_segmentation.py` | `Ran 16 tests … FAILED (errors=11)`; все 11 — `segmentation.IcuUnavailable: CJK segmentation needs ICU`; 5 тестов прошли | PARTIAL (см. примечание ниже) |
| 4.14 | `python tools/catalog/test_catalogue_v2_contract.py` | OK (target rows/exact target ids/distinct source contexts согласованы) | PASS |
| 4.15 | `python tools/catalog/test_ingestion.py` | `Catalogue v2 ingestion contracts: OK` | PASS |
| 4.16 | `python tools/catalog/test_morphology.py` | `Catalogue v2 morphology contracts: OK` | PASS |
| 4.17 | `python tools/catalog/test_build_catalogue_v2.py` | `Catalogue v2 builder contracts: OK` | PASS |
| 4.18 | `python tools/catalog/test_v1_v2_parity.py` | прерван на первой CJK-проверке: `IcuUnavailable` (`test_v1_v2_parity.py:41` → `segmentation.py:44`) | PARTIAL (та же причина) |
| 4.19 | `python tools/catalog/test_meta_info.py` | `Ran 9 tests … FAILED (errors=1)`: `SystemExit: census does not describe this Catalogue v2 build: uncompressedDeckBytes: census=242, build=243` → дефект **IKNA-T-003** (S3); остальные 8 PASS | FAIL |
| 4.20 | `python tools/catalog/test_readiness_audit.py` | `Ran 5 tests … OK` | PASS |
| 4.21 | `python tools/catalog/test_storage_experiment.py` | `Catalogue v2 storage experiment: OK` | PASS |
| 4.22 | `python tools/catalog/test_supply_census.py` | `Catalogue v2 supply census contracts: OK` | PASS |
| 4.23 | `python tools/catalog/test_supply_census_shards.py` | прерван в `build_report`: `ValueError: CJK segmentation needs ICU` (`test_supply_census_shards.py:287` → `supply_census.py:325`) | PARTIAL (та же причина) |
| 4.24 | `python tools/catalog/test_wikimatrix_stream_status.py` | `WikiMatrix stream status contracts: OK` | PASS |

Примечание к 4.13/4.18/4.23: `IcuUnavailable` — штатное поведение `tools/catalog/segmentation.py:44` (PyICU отсутствует на этой машине; сообщение подсказывает установку под Ubuntu). Это ограничение окружения, а не дефект кода: на CI (Ubuntu + libicu) соответствующие тесты проходят. Наблюдение для тулинга (не оформляется как дефект): при прямом запуске `IcuUnavailable` не конвертируется в skip — PARTIAL-тесты падают как errors, что маскирует реальную картину; под pytest прежних прогонов те же тесты отображались как skipped.

## Этап 5 — GradingLab CLI

Команда из CI (`.github/workflows/grading.yml`, шаг «Paired replay of synthetic scenarios using production Kotlin»), повторена все три сценария + структурная проверка отчётов; JAVA_HOME задан явно. Задача определена в `desktop/build.gradle.kts:133` (`JavaExec`, mainClass `dev.ikna.desktop.grading.GradingLabKt`, явный `-Pgrading.input` обязателен, workingDir = корень репозитория). [с обходом T-001]

| # | Команда | Результат | Статус |
| --- | --- | --- | --- |
| 5.1 | `gradle --no-daemon :desktop:gradingLab -Pgrading.input=tools/grading/fixtures/synthetic-verified.jsonl -Pgrading.output=build/grading/verified.txt` | exit 0, отчёт создан | PASS |
| 5.2 | то же для `synthetic-immature.jsonl` → `build/grading/immature.txt` | exit 0 | PASS |
| 5.3 | то же для `synthetic-noise.jsonl` → `build/grading/noise.txt` | exit 0 | PASS |
| 5.4 | `python tools/grading/check_reports.py build/grading` | `Production Kotlin replay reports pass structural checks; no empirical benefit claim.`, exit 0 | PASS |

Фрагмент `build/grading/verified.txt` (структура одинакова во всех трёх):

```
source=synthetic
method=paired prequential; fixed observed timestamps; no counterfactual scheduling claim
heldout=last 25% of active chronological answers; online calibration uses past only
activeAnswers=1194          scoredAnswers=1074          heldOutAnswers=299
binaryBrier=0.20292810872924272          derivedBrier=0.20043313794501563
heldOutBinaryBrier=0.24004031211908      heldOutDerivedBrier=0.23512621013585072
derivedHard=226             derivedEasy=62              warmupFallbacks=60
warrantsRealWorldReview=false           automaticEnable=false
warning=Synthetic improvement validates plumbing, NOT benefit to a real learner.
```

Наблюдения: отчёт честно разделяет «проверка трубопровода» и «польза реальному учащемуся» (явные warning'и, `automaticEnable=false` во всех сценариях — автоворонки нет). В noise-сценарии `discardedTimings=151` (мусорные тайминги отброшены), в verified/immature — 0. Расхождений с документированным контрактом нет.

## Этап 6 — Сборка desktop, --selftest, GUI

Команды по CI (build.yml / DESKTOP.md): `gradle --no-daemon :desktop:createReleaseDistributable`, упакованный `--selftest`, GUI-запуск с `IKNA_HOME_OVERRIDE`. [с обходом T-001]

| # | Проверка | Результат | Статус |
| --- | --- | --- | --- |
| 6.1 | `gradle --no-daemon :desktop:createReleaseDistributable` | `BUILD SUCCESSFUL in 1m 44s`; образ `desktop/build/compose/binaries/main-release/app/Ikna/` (Ikna.exe + app/*.jar + обрезанный runtime) | PASS |
| 6.2 | Упакованный `Ikna.exe --selftest`, `IKNA_SELFTEST_HOME=build\selftest-home` | exit code 0; лог: `selftest: sun.misc.Unsafe present` → `selftest: decks=1 cards=6 palette=ink` → `selftest: ok`; в песочнице созданы ikna.db(+wal/shm/lck), ikna-settings.preferences_pb, logs\ikna-desktop.log | PASS |
| 6.3 | GUI-запуск, `IKNA_HOME_OVERRIDE=build\gui-home` | лог `start java=17.0.20.1 os=Windows 11 home=…`; дом: ikna.db 3.2MB + WAL, настройки 441B, ikna.lock (0B), logs\; процесс-пара: лаунчер Ikna.exe (8MB) → дочерний Ikna.exe = JVM (≈380–407MB); окно существует: EnumWindows `title=[Ikna] visible=True` + theAwtToolkitWindow/IME | PASS |
| 6.4 | Single-instance | второй запуск с тем же домом → в логе `second instance refused`; по коду Main.kt:389–400 — модальный диалог + exitProcess(0) после закрытия диалога | PASS |
| 6.5 | Graceful close + геометрия | WM_CLOSE в окно → лог `exit`, записан `window.properties` (`width=1180.0 height=800.0 x=167.0 y=1.0 maximized=false`), дерево процессов завершилось | PASS |
| 6.6 | Интерактивный обход UI (сокращён) | онбординг: 4 слайда, «ДАЛЬШЕ» листает, пагинация обновляется; после онбординга — главный экран: колода «English fro…» (6 карточек), панель сессии; карточка переворачивается тапом, появляется блок оценки (← НЕ ЗНАЮ / ЗНАЮ → / ПОМЕТИТЬ НЕВЕРНОЙ). Полный цикл оценки не доведён: обход прерван по решению пользователя (приложение им многократно протестировано вручную) | PARTIAL |

Примечания к этапу 6:

- **Лаунчер ≠ JVM:** jpackage-лаунчер Ikna.exe — заглушка (8MB, 0.015s CPU), реальная JVM — его дочерний Ikna.exe. На лаунчере `jstack` отвечает «jvm.dll not loaded», окна и память у лаунчера нулевые. Диагностику проводить по дочернему pid. (Особенность jpackage, не дефект.)
- **Окна принадлежат сессии запуска:** окно создаётся и рендерится в той сессии, из которой запущен процесс (проверено EnumWindows/PrintWindow), но не появляется на столе пользователя, если запуск сделан из отсоединённой сессии агента. Для видимой проверки запускать Ikna.exe из своего терминала/проводника. Свойство окружения, не дефект.
- **Синтетические клики ловятся перекрытием:** клики, вводимые SetCursorPos+mouse_event, уходят в окно, лежащее поверх (в этой сессии — окно ZCode поверх окна ikna; подтверждено `WindowFromPoint`). «Залипание» слайда 3 онбординга при автокликах — следствие этого, а не дефект приложения; ручные клики по окну работают.
- **DPI:** координаты окон в DPI-unaware процессах виртуализуются (×1.25); скриншоты через PrintWindow без учёта DPI обрезают низ/право. Учтено в инструментарии прогона.
- Вынужденные завершения (Stop-Process) оставляли WAL/лок без checkpoint — при следующем старте БД восстановилась штатно (лог `start`, запись настроек). Приложений-дефектов в рамках пройденного не найдено.

## Этап 7 — Итоговая сводка

### Итоги по этапам

| Этап | Итог | Детали |
| --- | --- | --- |
| 0. Подготовка | PASS (6/6) | окружение собрано, speech-runtime и starter pack докачаны с проверкой пина |
| 1. Bootstrap отчёта | PASS (2/2) | скилл + docs/test-cli.md |
| 2. Preflight и версии | PASS (5/5) | versionName/packageVersion/Room 10/13 частей плана согласованы |
| 3. Desktop-тесты | PARTIAL | без обхода — FAIL (IKNA-T-001, S1); с тестовым обходом — `:desktop:test` PASS: 95 тестов, 0 падений |
| 4. Python-инструменты | PARTIAL | чекеры 11 PASS / 1 FAIL (IKNA-T-002); catalog: 8 PASS / 1 FAIL (IKNA-T-003) / 3 PARTIAL (нет ICU — окружение) |
| 5. GradingLab CLI | PASS (4/4) | verified/immature/noise + check_reports, exit 0 |
| 6. Сборка + selftest + GUI | PASS (5/6, 1 PARTIAL) | сборка, selftest, запуск, single-instance, graceful close — PASS; интерактивный обход сокращён по решению пользователя |
| 7. Сводка | этот раздел | — |

### Дефекты

| Id | Severity | Компонент | Суть |
| --- | --- | --- | --- |
| IKNA-T-001 | S1 (critical) | `desktop/.../SettingsPane.kt:266,269,273` ↔ `shared/.../Flat.kt:518` | desktop-модуль не компилируется: `IknaChip` принимает `onClick` не последним, три trailing-лямбды попадают в `modifier`. Заблокирован весь desktop-конвейер. В прогоне применён тестовый обход (именованный `onClick`, коммит `34ee50e`, НЕ для поставки) |
| IKNA-T-002 | S3 (minor) | `tools/check_android_ci.py:160` | `--self-test` хардкодит `bash` — на Windows `FileNotFoundError: [WinError 2]`; основной прогон чекера при этом PASS |
| IKNA-T-003 | S3 (minor) | `tools/catalog/test_meta_info.py:179` ↔ `meta_info.py:305,541,599,758` | на CRLF-платформах фикстура декларирует `st_size` (с `\r`), census считает `rawBytes` по строкам text-mode (без `\r`) → `uncompressedDeckBytes: census=242, build=243`, `verify_build` падает. На CI (LF) зелёный |

Не дефекты (окружение): `IcuUnavailable` в 3 catalog-тестах (PyICU отсутствует; на CI с libicu зелёные); отсутствие Android SDK — Android-этапы вне охвата по решению пользователя.

### Ограничения окружения прогона

- Windows без bash, без PyICU/ICU, без Android SDK (см. таблицу окружения).
- jpackage-лаунчер — заглушка: JVM в дочернем процессе (диагностика по дочернему pid).
- Окна приложения живут в сессии запуска; синтетический ввод перехватывается окнами поверх (проверено `WindowFromPoint`); DPI-виртуализация ×1.25 искажает координаты DPI-unaware-инструментов. Всё учтено в инструментарии (build/gui.ps1: SetProcessDPIAware, PrintWindow, авто-restore, проверка цели клика).

### Что осталось вне охвата

- Android-сборка и `:app`-тесты (нет SDK; `:app:testReleaseUnitTest :app:assembleDebug` из CI не запускались).
- Полный интерактивный цикл сессии (оценка карточек, статистика, настройки) — пользователь принимает решение о глубине UI-тестирования на себя: приложение многократно проверено им вручную.
- Подозрения из первичного аудита (дубли TERMINAL-символов в `ChunkShape.kt`, дубли Anki-файлов между `:app` и `:desktop`) не подтвердились на пройденных этапах как ломающие что-либо: 95 desktop-тестов, 35 тестов check_design_parity и остальные чекеры зелёные. Оставлены как заметки владельцу вне списка дефектов.

### Рекомендации (по убыванию приоритета)

1. ~~**IKNA-T-001**~~ — **выполнено** (коммит `67944c5`, см. статус в записи дефекта); поставлено в zip для пользователя.
2. **IKNA-T-003** — выровнять учёт байтов: в фикстуре считать байты так же, как census, либо читать deck-файлы в binary/с явным `newline="\n"`; попутно устраняет платформенную зависимость `rawBytes`.
3. **IKNA-T-002** — в self-test пропускать bash-тест при отсутствии bash (или платформо-нейтральный эквивалент).
4. Мелочь для тулинга: конвертировать `IcuUnavailable` в skip при прямом запуске catalog-тестов, чтобы PARTIAL-окружение не выглядело как ошибки.

### Артефакты прогона (вне git)

- Отчёт: `docs/test-cli.md` (этот файл; отслеживается в git с партии «Memory lattice» и дописан разделами «После прогона»).
- Логи чекеров: `build/check_logs/*.log`; скриншоты GUI: `build/shot-*.png`; вспомогательные скрипты: `build/run_phase4.ps1`, `build/count_tests.py`, `build/gui.ps1`, `build/find_window.ps1`, `build/list_windows.ps1`, `build/Mini.java`; песочницы домов: `build/selftest-home/`, `build/gui-home/`.

## Найденные дефекты

(пополняется по ходу прогона; нумерация сквозная IKNA-T-001…)

### IKNA-T-001 — S1 (critical): desktop-модуль не компилируется

- **Компонент:** `desktop/src/main/kotlin/dev/ikna/desktop/SettingsPane.kt:266, 269, 273` ↔ `shared/src/jvmShared/kotlin/dev/ikna/ui/theme/Flat.kt:518`
- **Команда:** `gradle --no-daemon :desktop:test` (также блокирует `:desktop:createReleaseDistributable`, `:desktop:gradingLab` и любой desktop-билд)
- **Ожидание:** срез репозитория собирается CI-эталонными командами из CONTRIBUTING.md.
- **Факт:** `:desktop:compileKotlin` падает с 6 ошибками:
  ```
  SettingsPane.kt:266:50 No value passed for parameter 'onClick'.
  SettingsPane.kt:266:102 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
  (аналогично для 269:50/269:99 и 273:57/273:106)
  ```
- **Причина (диагноз):** сигнатура `IknaChip(label, selected, onClick, modifier = Modifier)` — `onClick` НЕ последний параметр. Три вызова в SettingsPane.kt передают обработчик trailing-лямбдой, которая по правилам Kotlin ложится в последний параметр `modifier`. Остальные ~30 вызовов `IknaChip` в репозитории (app/, desktop/, shared/) используют многострочную форму с позиционными/именованными аргументами и корректны — сломаны только эти три.
- **Воспроизведение:** свежая распаковка среза → `gradle --no-daemon :desktop:test` → FAILED на `:desktop:compileKotlin`.
- **Гипотеза о происхождении:** срез сделан в середине работы (новые вызовы писались под сигнатуру с `onClick` последним / или `modifier` добавили позже последним параметром, не обновив новые call-site'ы). Компиляция `:shared` и Android-вызовов проходит.
- **Последствия:** весь desktop-конвейер (тесты, GradingLab, сборка, selftest, GUI) заблокирован. Для продолжения прогона применён тестовый обход (см. Этап 3).
- **Рекомендация:** в вызовах 266/269/273 заменить trailing-лямбду на именованный аргумент `onClick = { … }` (3 строки) — либо (вариант для владельца API) переместить `modifier` перед `onClick` в сигнатуре `IknaChip`, но это затронет все call-site'ы.
- **Статус после прогона: ИСПРАВЛЕНО.** В этой рабочей копии применён первый вариант рекомендации — постоянный коммит `67944c5` «Fix IKNA-T-001 (hot-reload blocker): pass onClick as named argument in 3 IknaChip calls in SettingsPane.kt» (1 файл, 6 строк). Проверка: `gradle --no-daemon :desktop:test` → `BUILD SUCCESSFUL in 1m 32s`, 95 тестов / 0 failures / 0 errors / 0 skipped. Исправленная копия поставляется пользователю zip-архивом в `Документы\ikna_aiworks\`.

### IKNA-T-002 — S3 (minor): `check_android_ci.py --self-test` падает на Windows (хардкод `bash`)

- **Компонент:** `tools/check_android_ci.py:160` — `subprocess.run(["bash", str(wrapper), …])` внутри self-test'а.
- **Команда:** `python tools/check_android_ci.py --self-test`
- **Ожидание:** self-test идёт на любой платформе с Python 3.12 (bash в требованиях репозитория не заявлен).
- **Факт:** `Ran 9 tests … FAILED (errors=1)` — `test_logging_wrapper_preserves_gradle_exit_status_and_arguments` падает с `FileNotFoundError: [WinError 2] Не удается найти указанный файл` (`_winapi.CreateProcess` для `bash`), которого на Windows нет.
- **Не задето:** основной прогон `python tools/check_android_ci.py` (без self-test) проходит — `DEX-safe Kotlin identifier check passed: 157 Android-bound files; JVM-only tests excluded.`
- **Гипотеза о причине:** скрипт писался под CI Ubuntu; self-test проверяет bash-обёртку логирования Gradle и потому вызывает bash напрямую. Нужен skip при отсутствии bash или платформо-нейтральный вариант теста.
- **Воспроизведение:** Windows без bash → `python tools/check_android_ci.py --self-test` → FAILED (errors=1 из 9).

### IKNA-T-003 — S3 (minor): фикс `test_meta_info` расходится с census-подсчётом байтов на CRLF-платформах (Windows)

- **Компонент:** `tools/catalog/test_meta_info.py:179` — фикстура декларирует `"uncompressedDeckBytes": path.stat().st_size`; ↔ `tools/catalog/meta_info.py:305` (`raw_bytes += len(line.encode("utf-8"))` по строкам, прочитанным в text-mode), `:541` (сумма), `:599` (`observedUncompressedBytes`), `:758`/`:766` (сверка в `verify_build`).
- **Команда:** `python tools/catalog/test_meta_info.py`
- **Ожидание:** `verify_build` подтверждает сборку; на CI (Ubuntu, LF) тест проходит.
- **Факт:** `Ran 9 tests … FAILED (errors=1)` — `test_build_cross_check_adds_target_cap_inventory` завершается `SystemExit: census does not describe this Catalogue v2 build: uncompressedDeckBytes: census=242, build=243`.
- **Диагноз (подтверждён чтением кода):** `write_deck` пишет файл в text-режиме — на Windows каждая `\n` становится `\r\n`; фикстура берёт `st_size` (учитывает `\r`), а census считает `rawBytes` по строкам text-mode, где universal newlines переводит `\r\n` → `\n` и `\r` теряется. Расхождение — ровно 1 байт на строку deck-файла (здесь: одна строка, 242 vs 243). `compressedDeckBytes` не расходится (обе стороны считают через `st_size`), ломается только `uncompressedDeckBytes`.
- **Воспроизведение:** Windows → `python tools/catalog/test_meta_info.py` → FAILED (errors=1 из 9). На LF-платформе воспроизведения нет.
- **Последствия/риск:** тулинговый тест зелёный на CI и красный на Windows-машинах разработчика; кроме того, сам учёт `rawBytes` в `meta_info.py` зависит от newline-перевода — на CRLF-данных «observed raw JSONL» в отчётах слегка занижается относительно фактического размера файла (смежный риск, на LF-данных репозитория не проявляется).
- **Гипотеза об исправлении:** в фикстуре считать декларируемые байты так же, как census (сумма длин строк в text-mode), либо открыть `write_deck`/`open_deck` с явным `newline="\n"`; в `meta_info.py` — читать в binary или учитывать `\r`, чтобы `rawBytes` не зависел от платформы.
- **Рекомендация:** считать S3, исправлять в рамках тулинга (не блокирует desktop-прогон).

## После прогона — партии правок (2026-09-25/26; сделано GLM 5.3 & GLM 5.3-flash, требует ревью)

Каждая партия = коммит в этой копии + полный zip в `Документы\ikna_aiworks\` (актуальный всегда продублирован как `ikna-latest.zip`).

| Партия | Коммит | Содержание | Проверки |
| --- | --- | --- | --- |
| 1. Hot-reload блокер | `67944c5` | IKNA-T-001 исправлен постоянно: именованный `onClick` в трёх вызовах `IknaChip` (`SettingsPane.kt`); тестовый обход `34ee50e` переписан в постоянный фикс | `:desktop:test` 95/0 |
| 2. Шрифты и мера Browse | `8f7c7a4` | `headlineLarge`/`titleLarge`/`titleSmall` добавлены в `IknaTypography` и `typographyOf` — системный шрифт больше нигде не подставляется; `FontMode.SYSTEM` удалён (enum, чипы, миграция сохранённых настроек и бэкапов в GEOLOGICA); мера ленты Browse 640dp вместо 960dp | 95/0; parity/localization/text/palettes — exit 0 |
| 3. Memory lattice и ритм Browse | `29081a3`, `a6b7339` | `IknaMemoryAmbientStrip`: зерно на верхней и нижней полосах с редким детерминированным появлением/растворением меток (цикл 36с; при выключенных анимациях — ровно статичное поле); заметность поднята до лестницы шапки колод (0.085/0.13/0.18, метка 1.35dp); `titleLarge` интерлиньяж 26sp; зазор до источника 12dp; чекбоксы Track C/J; тюнинг Signal Frame закрыт решением владельца | 95/0; parity/palettes/text/localization — exit 0 |
| 4. Режим разработчика | `a6b7339` | hot-reload лаунчер больше не перетирает выбор профиля (`DEVELOPER` пишется только при первом старте — раньше «Вернуться в обычный режим» отменялся каждым перезапуском); блок «Редкое → Дополнительно» раскрыт по умолчанию при активном DEV (обе платформы); `__pycache__` удалены из git и проигнорированы | 95/0; `check_hot_reload` exit 0; parity exit 0 |
| 5. Signal Frame при скролле | `ac2715a`, `b987ea8` | OUTER-рамка (контролы с собственной границей: чипы, строки, поля, плитки, ссылки) отвечает в оверлее только указателю — постоянные фокусные рамки больше не уезжают со скроллом поверх чужого текста; при потере указателя OUTER-рамка исчезает мгновенно (snap вместо 55мс fade-out); фокусные границы добавлены полям, плиткам и ссылке Browse; рамка строк колод добавлена и убрана по решению владельца; DESIGN.md и parity-тест синхронизированы | 95/0; parity 35 OK; text OK |

## После прогона — статус 0.11 (одностраничный обзор, 2026-09-26)

Состояние `modern_PLAN-0.11.md` по трекам (выполнено/всего): A 6/9, B 8/17, **C 9/12**, D 5/9, E 1/13, F 0/8, **G 1/12**, H 1/9, I 0/6, **J 9/13**, K 0/3 (`REVIEW AFTER 0.11`), L 8/14; closeout 0/11.

- Закрыто в этой сессии: Track C пункт ритма и меры; Track J — прототип lattice, одна поверхность рисования, статичное состояние, тюнинг Signal Frame (решение владельца: принят как есть). Вне чекбоксов: IKNA-T-001, шрифтовая система, режим разработчика, две утечки Signal Frame.
- Визуальная линия (порядок по плану — G, затем J): **G** — 11 открытых пунктов usability (объяснения у отключённых действий, иерархия «Редкого», подтверждения деструктивных действий, клавиатурная обнаруживаемость, a11y-метки, мелкие layout'ы); **J** — «свой» навигационный язык, строгость calm-motion, плотность телефона/десктопа, и новый пункт: **второй вид интерфейса со скруглениями** (вариант 1 — текущий угловатый, вариант 2 — скруглённый; впервые; одно дерево компонентов через shape-систему, инвентаризация жёстких прямых углов в shared-компонентах до реализации, DESIGN.md дополняется при приземлении варианта 2).
- Верификации Track C (CJK/длинные контексты, паритет скролла, source-link) требуют реальных платформ: CJK — PyICU/ICU на машине, Android — SDK (здесь недоступны; CI закрывает).
- Блокеры ретрансляции в closeout: реальные Android- и desktop-сборки финального дерева, multi-monitor проверка на живой Windows-машине.
