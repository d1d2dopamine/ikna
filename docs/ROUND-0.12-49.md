# Round 49 — finite selection-filter audit

2026-10-12. Working stage authorized by the owner: investigate selection and
false exclusions, without inventing or rewriting any card text. Preserve Round48
application/UI changes. No new corpus discovery, full-pool benchmark or repeat
of the completed context-retention allocation experiment.

## Finite plan

1. Bind the existing 188 directed Tatoeba pl/ko records (94 links) and saved global
   written-frequency ranks to their original SHA-256 and source version.
2. Restore the historically pinned official ICU74.2 runtime in an isolated folder
   if feasible; never silently substitute the system's ICU76.1 for parity claims.
3. Explain every pre-allocation row exclusion, and separately count token-level
   rank/function, length, occurrence and canonical-ambiguity decisions. Distinguish
   overlapping token diagnostics from first-reason row rejection.
4. Test untackled hypotheses: scalar-vs-UTF16 boundary behavior; normalization and
   analysis-cache collisions; frequency/level boundaries; asymmetric source and
   meaning gates. Compare direct per-text analysis with cached analysis.
5. Fix only a reproducible correctness bug while retaining quality/rights rules;
   otherwise preserve policy and report zero demonstrated card growth. A short
   natural utterance rejected by an intentional minimum is not automatically a
   software bug or permission to lower the minimum.
6. Reproduce the six baseline deck decisions, target/context counts and ownership
   with the same input and runtime; state omissions separately from allocation.
   Keep raw text, rank words, runtime libraries and scratch databases outside ZIPs.

Stop after the whole retained weak-pair slice and concrete invariant regressions
are explained. No threshold changes to fill counts; no claims about unseen full
input or native-speaker semantic certification. Report any demonstrated bug's
scope and actual saved-data delta separately from synthetic fixture evidence.

## Результат

Проверен весь сохранённый срез: 94 исходные связи Tatoeba, 188 направленных
записей. Исторический ICU74.2 восстановлен отдельно. После исправления все
счётчики и выбранные объекты target/context совпадают с повтором Round48.
Прирост допущенных карточек: **0**. Полный пул заново не запускался.

### До распределения контекстов

| Причина первого отказа / допуск | pl→ko | ko→pl |
|---|---:|---:|
| Слишком короткий исходный контекст | 21 | 16 |
| Нет пригодной цели после фильтра слов | 1 | 0 |
| Допущено к распределению | 72 | 78 |
| Всего направленных записей | 94 | 94 |

Ограничения максимальной длины и пустого перевода здесь ничего не исключили.
Единственная польская запись без цели содержит только слова из первых 60
частотных рангов. На уровне всех вхождений токенов: pl — 150 function-top60,
6 повторных lowercase-вхождений, 255 пригодных; ko — 73, 2 и 294 соответственно.
Это вхождения по записям, не число уникальных карточек. Перекрывающиеся причины
и хеши/идентификаторы отдельных записей сохранены в ROWS.json без исходного текста.

16 из 21 короткой польской записи и 14 из 16 корейских имеют иначе пригодные
цели. Среди них 12 польских и 9 корейских целей отсутствуют в прошедших длину
записях. Это диагностические потенциальные цели до остальных проверок и
распределения, не принятые карточки. Минимум длины является действующим правилом;
данных, позволяющих назвать его программной ошибкой, нет. Порог не менялся.

### Распределение отдельно от допуска

| Направление / уровень | Пригодные цели | Выбрано | Потеря из-за занятого контекста | Контексты | Колода включена |
|---|---:|---:|---:|---:|---|
| pl→ko beginner | 117 | 51 | 66 | 62 | да |
| pl→ko middle | 46 | 32 | 14 | 32 | нет, меньше 40 |
| pl→ko advanced | 47 | 30 | 17 | 31 | нет, меньше 40 |
| ko→pl beginner | 127 | 55 | 72 | 61 | да |
| ko→pl middle | 60 | 42 | 18 | 45 | да |
| ko→pl advanced | 74 | 45 | 29 | 45 | да |

Итого 471 пригодная цель, 255 выбранных членств до исключения маленьких колод,
193 после исключения, 276 сохранённых вхождений контекста до исключения.
Исторический лимит 8 000 и текущий 12 000 здесь ничего не отсекают.

## Исправленная ошибка идентичности анализа

Частотная дедупликация намеренно объединяет регистр и пробельные варианты.
Но прежний анализ границ слов тоже использовал первого представителя этой
группы. Для Unicode-варианта с иной токенизацией это могло приписать реальному
исходному тексту отсутствующую цель или неверное число токенов. Технический
регрессионный тест воспроизводит ошибку через production stage → analysis → pair
в обоих порядках исходных вариантов. Это синтетический тест программной
корректности, не новый текст карточки и не найденная плохая запись корпуса.

Теперь частотная идентичность и ранги сохранены, а анализ привязан к точной
исходной UTF-8 строке. Реальные границы, canonical-уникальность и японский guard
проверяются для каждого варианта. Старое представление цели сохраняется только
при совпадении числа токенов и последовательности rank/targetId/level.
Постоянный экспериментальный кеш имеет версию 2, учитывает точные варианты и
частотных представителей; версия 1 не переиспользуется и не удаляется.
Диагностические пути используют ту же идентичность.

В сохранённом срезе нет разных исходных написаний с общей частотной
идентичностью, нет коллизий canonical-ключей рангов, ошибочных кешированных
строк найдено 0. Полный пул может изменить выбор для затронутых вариантов;
его влияние, дополнительная стоимость хранения и быстродействие не измерены.

## Важное уточнение контракта

Три контекста — **максимум**, допустимы 1–3. Это не общее требование минимум
трёх контекстов для каждой карточки. Уточнены EVERYDAY-POOL.md и историческое
описание Round08. В Round36 строгий append-only эксперимент сознательно требовал
три контекста для нового добавления; его нулевой результат остаётся верным
для того эксперимента и не доказывает невозможность всех допустимых добавлений.
Источник нельзя отвергать только потому, что он даёт менее трёх контекстов.
Прежние выводы, опиравшиеся исключительно на такой минимум, требуют пересмотра;
это не снимает права, домен, полноту фразы, ранги, точность перевода и дедупликацию.
Новых источников в этом раунде не искали и не допускали.

## Проверки и пределы

- 151 selector/cache/allocation/metrics/guard tests: 150 успешно, один явный skip
  реального Sudachi, отсутствующего в среде; это не Japanese platform acceptance.
- 10 новых filter-audit tests: успешно.
- Независимо: 67 identity/cache/reuse/allocation tests успешно; дополнительная
  перестановка источников с общим кешем дала miss/miss/hit без неверных границ.
- До/после: все шесть уровней, выбранные target/context ID, полные выбранные
  объекты и владение контекстами совпадают. SHA256 метаданных назначений:
  `b6249a463934844919c313f8c0268a2f18cabac28ae239dcf6e3415330ea47d1`.
- Исторический отчёт содержит счётчики, но не полные первоначальные назначения.
  Точное сравнение назначений относится к новому повтору Round48 и Round49.
- Семантическая оценка не является сертификацией носителем языка.
- Приложение/UI и его активы не менялись; новый Android/JVM build не требовался.

Evidence: `evidence/selection-round49/`. Для воспроизведения CLI
`tools/catalog/selection_filter_audit.py` принимает --baseline, --ranks,
--expected, --scratch и --out; он проверяет исходные SHA256 и требует ICU74.2.
Исходные корпуса/словарь рангов получают отдельно существующим workflow и
сопоставляют с паспортом; они, scratch SQLite, кеш и ICU-бинарники в ZIP не входят.
Исходные хеши и версия источника сохранены в SUMMARY.json. Технические
синтетические fixtures остаются в тестах.
