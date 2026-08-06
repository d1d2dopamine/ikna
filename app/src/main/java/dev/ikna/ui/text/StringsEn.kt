package dev.ikna.ui.text

internal val STRINGS_EN: Map<String, String> = mapOf(
	// Напоминание
	"app.001" to "Reminder",
	// Одно напоминание в день, если минимум ещё не сделан
	"app.002" to "One reminder a day, only if the minimum is not done yet",
	// Не удалось прочитать файл
	"font.001" to "Could not read the file",
	// Файл слишком большой для шрифта
	"font.002" to "That file is too large for a font",
	// Не удалось сохранить шрифт
	"font.003" to "Could not save the font",
	// Не удалось сохранить шрифт
	"font.004" to "Could not save the font",
	// Это не файл шрифта
	"font.005" to "That is not a font file",
	// Это коллекция шрифтов .ttc — нужен один шрифт, .ttf или .otf
	"font.006" to "That is a .ttc font collection — a single font is needed, .ttf or .otf",
	// Это веб-шрифт .woff — Android его не читает, нужен .ttf или .otf
	"font.007" to "That is a .woff web font — Android cannot read it, use .ttf or .otf",
	// Это не файл шрифта
	"font.008" to "That is not a font file",
	// Файл шрифта повреждён
	"font.009" to "The font file is damaged",
	// Файл шрифта обрезан
	"font.010" to "The font file is truncated",
	// Файл шрифта обрезан
	"font.011" to "The font file is truncated",
	// В шрифте нет таблицы символов
	"font.012" to "The font has no character table",
	// В шрифте нет самих букв
	"font.013" to "The font has no letters in it",
	// Своя колода
	"deckrepo.001" to "Your own deck",
	// Статистика
	"stats.001" to "Statistics",
	// ПОСЛЕДНИЕ 30 ДНЕЙ
	"stats.002" to "LAST 30 DAYS",
	// Каждая метка — день с занятием, справа сегодня. Пропуск ничего не обнуляет и не обрывает: 
	"stats.003" to "Each mark is a day with a session, today on the right. A skip zeroes nothing and breaks nothing: there is no streak here, nothing to break.",
	// НОРМА ДНЯ
	"stats.004" to "DAILY NORM",
	// Карточек в день. Посчитано по твоим последним дням, пересчитывается само.
	"stats.005" to "Cards a day. Counted from your own recent days, recalculated by itself.",
	// Ориентир на первые дни, а не измерение. Свою цифру посчитаю, когда наберётся хотя бы три д
	"stats.006" to "A starting guess, not a measurement. I will count your own figure once at least three days with sessions add up — before that it would be made up.",
	// СЛОВ В ПАМЯТИ
	"stats.007" to "WORDS IN MEMORY",
	// ОТВЕЧЕНО СЕГОДНЯ
	"stats.008" to "ANSWERED TODAY",
	// Слова считаются отдельно от чанков: одно слово встречается в разных фразах и держится креп
	"stats.009" to "Words are counted apart from chunks: one word appears in different phrases and holds on better than any of them.",
	// ВЕРНЁТСЯ ЗА 14 ДНЕЙ
	"stats.010" to "DUE WITHIN 14 DAYS",
	// Сколько карточек подошлёт по сроку. Если где-то вырастает гора — новые чанки в те дни доба
	"stats.011" to "How many cards fall due. If a mountain grows somewhere, new chunks will not be added on those days.",
	// УДЕРЖАНИЕ
	"stats.012" to "RETENTION",
	// Нужно хотя бы 20 повторений, чтобы это была цифра, а не догадка. Сейчас их 
	"stats.013" to "At least 20 reviews are needed for this to be a figure rather than a guess. So far there are ",
	// Из 
	"stats.014" to "Of ",
	//  повторений за месяц вспомнилось 
	"stats.015" to " reviews this month, recalled ",
	// %. Считаются только повторения — первую встречу забыть нельзя.
	"stats.016" to "%. Only reviews count — a first meeting cannot be forgotten.",
	// Расписание метит примерно в 90%. Ниже 80% — интервалы для тебя длинноваты. Нагрузка подстр
	"stats.017" to "The schedule aims at about 90%. Below 80% the intervals are too long for you. The load adjusts itself, but if it stays like this for weeks — take a smaller norm.",
	// Расписание метит примерно в 90%. Выше 95% — повторов больше, чем нужно: память выдержала б
	"stats.018" to "The schedule aims at about 90%. Above 95% there are more reviews than needed: memory would have held rarer passes, and that is spare minutes.",
	// Расписание метит примерно в 90%, и сейчас всё в этом коридоре. Трогать ничего не надо.
	"stats.019" to "The schedule aims at about 90%, and right now everything is inside that corridor. Nothing needs touching.",
	// МИНУТ СЕГОДНЯ
	"stats.020" to "MINUTES TODAY",
	// ЗА НЕДЕЛЮ
	"stats.021" to "THIS WEEK",
	// Считается только время с карточкой на экране: паузы, когда телефон отложен, сюда не попада
	"stats.022" to "Only time with a card on screen counts: pauses with the phone put down do not get in here. One card takes you about ",
	//  сек.
	"stats.023" to " sec.",
	// Считается только время с карточкой на экране: паузы, когда телефон отложен, сюда не попада
	"stats.024" to "Only time with a card on screen counts: pauses with the phone put down do not get in here.",
	// КОГДА ИДЁТ ЛУЧШЕ
	"stats.025" to "WHEN IT GOES BEST",
	// Лучше всего вспоминается около 
	"stats.026" to "Recall works best around ",
	// . Это не приказ заниматься именно тогда — просто в этот час тебе дешевле, и напоминание ра
	"stats.027" to ". This is not an order to study exactly then — it is simply cheaper for you at that hour, and a reminder is sensibly set an hour before it.",
	// Пока рано выделять час: нужно хотя бы 12 повторений внутри одного часа. Бледные столбики —
	"stats.028" to "Too early to name an hour: at least 12 reviews inside one hour are needed. Pale bars are hours with too little data yet.",
	// НЕ ДЕРЖИТСЯ
	"stats.029" to "WILL NOT STICK",
	// Пока таких нет: ничего не забывалось по четыре раза и больше.
	"stats.030" to "None so far: nothing has been forgotten four times or more.",
	// Справа — сколько раз фраза забывалась. Это про фразу, а не про тебя: обычно она слишком дл
	"stats.031" to "On the right is how many times the phrase was forgotten. This is about the phrase, not about you: usually it is too long or the translation in it is off. Nothing needs doing — these come round less often than the rest.",
	// СЕГОДНЯ
	"stats.032" to "TODAY",
	// Не удалось открыть файл
	"set.001" to "Could not open the file",
	// Не удалось открыть файл
	"set.002" to "Could not open the file",
	// Шрифт применён: 
	"set.003" to "Font applied: ",
	// Без разрешения на уведомления напоминание не придёт
	"set.004" to "Without permission for notifications the reminder will not arrive",
	// Файл настроек повреждён
	"set.005" to "The settings file is damaged",
	// Настройки восстановлены
	"set.006" to "Settings restored",
	// Настройки восстановлены. Шрифт «
	"set.007" to "Settings restored. The font «",
	// » нужно выбрать заново — сам файл шрифта твой, и в бэкап он не кладётся.
	"set.008" to "» has to be picked again — the font file is yours, and it is not put into the backup.",
	// Добавлено ответов: 
	"set.009" to "Answers added: ",
	//  · пересчитано 
	"set.010" to " · recalculated ",
	//  · пропущено 
	"set.011" to " · skipped ",
	// Настройки
	"set.012" to "Settings",
	// Нагрузка
	"set.013" to "Load",
	// Сколько повторений в день считать нормой. От этого зависит, сколько новых чанков придёт за
	"set.014" to "How many reviews a day count as the norm. It decides how many new chunks arrive tomorrow. By default the norm counts itself — from how much you actually answered over the past two weeks.",
	// АВТО
	"set.015" to "AUTO",
	// РУЧНОЙ
	"set.016" to "MANUAL",
	// СЕЙЧАС · 
	"set.017" to "NOW · ",
	// КАРТОЧЕК В ДЕНЬ
	"set.018" to "CARDS A DAY",
	// Вид
	"set.019" to "Look",
	// Анимации
	"set.020" to "Animations",
	// Карточки улетают, конец дня с анимацией
	"set.021" to "Cards fly away, the day ends with an animation",
	// Вибрация
	"set.022" to "Vibration",
	// Короткий отклик на свайп
	"set.023" to "A short response to a swipe",
	// Язык
	"set.024" to "Language",
	// Язык самого приложения. На колоды он не влияет: переводы в карточках остаются такими, каки
	"set.025" to "The language of the app itself. It does not touch the decks: translations inside the cards stay as they are in the deck.",
	// Озвучка
	"set.026" to "Speech",
	// Говорит движок синтеза речи, который уже стоит на телефоне. Ничего не скачивается и ничего
	"set.027" to "The speech engine already installed on the phone does the talking. Nothing is downloaded and nothing goes to the network: voices that need the internet do not get into the list at all.",
	// Читать вслух
	"set.028" to "Read aloud",
	// Значок звука появляется только там, где он не выдаёт ответ
	"set.029" to "The sound mark appears only where it does not give the answer away",
	// смотрю, что есть на телефоне…
	"set.030" to "looking at what the phone has…",
	// На телефоне нет движка синтеза речи. Подойдёт любой офлайновый — например RHVoice или Sher
	"set.031" to "There is no speech engine on the phone. Any offline one will do — RHVoice or SherpaTTS from F-Droid, for example. Come back here after installing it.",
	// НАСТРОЙКИ СИНТЕЗА РЕЧИ
	"set.032" to "SPEECH ENGINE SETTINGS",
	// Не нашёл этот раздел в настройках телефона
	"set.033" to "Could not find that section in the phone settings",
	// Движок есть, но офлайн-голосов для этих языков в нём нет. Их надо доставить — один раз, и 
	"set.034" to "There is an engine, but it has no offline voices for these languages. They have to be fetched — once, and after that they work without the network.",
	// ДОУСТАНОВИТЬ ГОЛОСА
	"set.035" to "INSTALL VOICES",
	// Движок не умеет докачивать голоса сам — посмотри в его настройках
	"set.036" to "The engine cannot fetch voices by itself — look in its own settings",
	// офлайн-голосов для этого языка нет
	"set.037" to "no offline voices for this language",
	// ПО УМОЛЧАНИЮ
	"set.038" to "DEFAULT",
	// Первый звук после запуска может задуматься на пару секунд — движок просыпается. Следующая 
	"set.039" to "The first sound after launch may think for a couple of seconds — the engine is waking up. The next card is voiced in advance, so after that the sound comes at once.",
	// Шрифт
	"set.040" to "Font",
	// Свой .ttf или .otf для текста карточек и заголовков. Служебные подписи остаются моноширинн
	"set.041" to "Your own .ttf or .otf for card text and headings. Service captions stay monospaced — the interface is read by them. The file is checked before it is applied: a broken font would bring down every screen at once, including this one.",
	// СЕЙЧАС · СИСТЕМНЫЙ
	"set.042" to "NOW · SYSTEM",
	// СЕЙЧАС · 
	"set.043" to "NOW · ",
	// ВЫБРАТЬ ФАЙЛ
	"set.044" to "PICK A FILE",
	// СБРОСИТЬ
	"set.045" to "RESET",
	// Вернул системный шрифт
	"set.046" to "The system font is back",
	// Напоминание
	"set.047" to "Reminder",
	// Одно в день, и только если минимум ещё не сделан. Никаких серий и укоров.
	"set.048" to "One a day, and only if the minimum is not done yet. No streaks and no reproaches.",
	// Напоминать
	"set.049" to "Remind me",
	// в 
	"set.050" to "at ",
	// выключено
	"set.051" to "off",
	// Данные
	"set.052" to "Data",
	// Журнал ответов — единственное, что нельзя восстановить. Всё остальное считается из него за
	"set.053" to "The answer log is the only thing that cannot be restored. Everything else is recomputed from it.",
	// Авто-выгрузка раз в неделю
	"set.054" to "Auto-export once a week",
	// В папку Документы/Ikna
	"set.055" to "Into the Documents/Ikna folder",
	// ВЫГРУЗИТЬ
	"set.056" to "EXPORT",
	// Не удалось сохранить файл
	"set.057" to "Could not save the file",
	// Настройки сохранены. Журнал пока пуст — выгружать нечего.
	"set.058" to "Settings saved. The log is still empty — there is nothing to export.",
	// Журнал и настройки сохранены в Документы/Ikna
	"set.059" to "Log and settings saved to Documents/Ikna",
	// ВОССТАНОВИТЬ
	"set.060" to "RESTORE",
	// Перерывы
	"set.061" to "Breaks",
	// Настраивать нечего и включать нечего. Приложение смотрит, сколько ты реально занимался, и 
	"set.062" to "Nothing to set up and nothing to switch on. The app looks at how much you actually studied and shifts the dates by itself: a day with no sessions moves things by a day, a half-hearted day by half a day. New chunks are not added until the pace returns. No debt piles up.",
	// Редкое
	"set.063" to "Rare",
	// То, что нужно раз в год или ни разу. Спрятано не потому, что сложно, а чтобы не нажать слу
	"set.064" to "The things needed once a year or never. Hidden not because they are hard, but so they are not hit by accident.",
	// СКРЫТЬ
	"set.065" to "HIDE",
	// ПОКАЗАТЬ
	"set.066" to "SHOW",
	// ПЕРЕСЧИТАТЬ СЛОЙ СЛОВ
	"set.067" to "REBUILD THE WORD LAYER",
	// Слой слов пересчитан по журналу
	"set.068" to "Word layer rebuilt from the log",
	// ТЕХНИЧЕСКИЙ ЭКРАН
	"set.069" to "TECHNICAL SCREEN",
	// НАЧАТЬ ЗАНОВО
	"set.070" to "START OVER",
	// Стереть всё
	"set.071" to "Erase everything",
	// Полный сброс: карточки, сроки, статистика, журнал ответов и сами настройки. Приложение ста
	"set.072" to "A full reset: cards, dates, statistics, the answer log and the settings themselves. The app becomes what it was right after installation and restarts. Before erasing, the log is exported to Documents/Ikna — it can be restored afterwards.",
	// ТОЧНО СТЕРЕТЬ ВСЁ
	"set.073" to "REALLY ERASE EVERYTHING",
	// СТЕРЕТЬ ДАННЫЕ
	"set.074" to "ERASE THE DATA",
	// Нажми ещё раз, если правда стереть. Отмены не будет.
	"set.075" to "Press again if you really mean it. There will be no undo.",
	// второе нажатие стирает сразу
	"set.076" to "the second press erases at once",
	// нажать надо дважды
	"set.077" to "it takes two presses",
	// Начать заново?
	"set.078" to "Start over?",
	// Сроки карточек, статистика и слой слов обнулятся. Журнал ответов останется — из него можно
	"set.079" to "Card dates, statistics and the word layer will be zeroed. The answer log stays — everything can be brought back from it with the «Restore» button.",
	// НАЧАТЬ ЗАНОВО
	"set.080" to "START OVER",
	// Статистика обнулена
	"set.081" to "Statistics zeroed",
	// ОТМЕНА
	"set.082" to "CANCEL",
	// ФОН
	"set.083" to "BACKGROUND",
	// ТЕКСТ
	"set.084" to "TEXT",
	// ПРИГЛУШЁННЫЙ
	"set.085" to "MUTED",
	// АКЦЕНТ
	"set.086" to "ACCENT",
	// КОНТРАСТ · ТЕКСТ 
	"set.087" to "CONTRAST · TEXT ",
	//   ·  ПРИГЛУШЁННЫЙ 
	"set.088" to "  ·  MUTED ",
	//   ·  АКЦЕНТ 
	"set.089" to "  ·  ACCENT ",
	// что-то из этого плохо читается на своём фоне — цвет всё равно применён
	"set.090" to "something here reads badly on its own background — the colour is applied anyway",
	// НАГРУЗКА
	"set.091" to "LOAD",
	// ВИД
	"set.092" to "LOOK",
	// ЯЗЫК
	"set.093" to "LANGUAGE",
	// ОЗВУЧКА
	"set.094" to "SPEECH",
	// ШРИФТ
	"set.095" to "FONT",
	// НАПОМИНАНИЕ
	"set.096" to "REMINDER",
	// ДАННЫЕ
	"set.097" to "DATA",
	// РЕДКОЕ
	"set.098" to "RARE",
	// КАК В СИСТЕМЕ
	"set.099" to "SYSTEM",
	// РУССКИЙ
	"set.100" to "РУССКИЙ",
	// ТЁМНАЯ
	"set.101" to "DARK",
	// СВЕТЛАЯ
	"set.102" to "LIGHT",
	// СВОЯ
	"set.103" to "OWN",
	// ПОЛЬСКИЙ
	"set.104" to "POLISH",
	// РУССКИЙ
	"set.105" to "RUSSIAN",
	// АНГЛИЙСКИЙ
	"set.106" to "ENGLISH",
	// НЕМЕЦКИЙ
	"set.107" to "GERMAN",
	// ИСПАНСКИЙ
	"set.108" to "SPANISH",
	// ФРАНЦУЗСКИЙ
	"set.109" to "FRENCH",
	// шрифт
	"set.110" to "font",
	// ← НАЗАД
	"dbg.001" to "← BACK",
	// ВЫГРУЗИТЬ ЖУРНАЛ ОТВЕТОВ
	"dbg.002" to "EXPORT THE ANSWER LOG",
	// Файл: Документы/Ikna/
	"dbg.003" to "File: Documents/Ikna/",
	// Не удалось сохранить файл
	"dbg.004" to "Could not save the file",
	// ПЕРЕСЧИТАТЬ СЛОЙ СЛОВ
	"dbg.005" to "REBUILD THE WORD LAYER",
	// Слой слов пересчитан
	"dbg.006" to "Word layer rebuilt",
	// ПЕРЕСОБРАТЬ ПЛАН ДНЯ
	"dbg.007" to "REBUILD TODAY'S PLAN",
	// План на сегодня пересобран
	"dbg.008" to "Today's plan rebuilt",
	// Решения регулятора по дням
	"dbg.009" to "Governor decisions by day",
	//   новых=
	"dbg.010" to "  new=",
	//   к повтору=
	"dbg.011" to "  due=",
	//   прогноз3д=
	"dbg.012" to "  forecast3d=",
	//   долг=
	"dbg.013" to "  backlog=",
	//   точность=
	"dbg.014" to "  accuracy=",
	//   запас=
	"dbg.015" to "  headroom=",
	// Не получилось прочитать ни одной строки
	"deck.001" to "Could not read a single line",
	// Добавлено чанков: 
	"deck.002" to "Chunks added: ",
	// , пропущено 
	"deck.003" to ", skipped ",
	// Колоды
	"deck.004" to "Decks",
	// Пока ни одной колоды. Плюс сверху добавит файл .jsonl.
	"deck.005" to "No decks yet. The plus above adds a .jsonl file.",
	// читаю файл…
	"deck.006" to "reading the file…",
	// СЕГОДНЯ
	"deck.007" to "TODAY",
	// ничего не ждёт
	"deck.008" to "nothing waiting",
	// сегодня 
	"deck.009" to "today ",
	// на сегодня нет
	"deck.010" to "nothing for today",
	// введено 
	"deck.011" to "introduced ",
	//  из 
	"deck.012" to " of ",
	//  · знаешь 
	"deck.013" to " · you know ",
	// карточек
	"deck.014" to "cards",
	// карточка
	"deck.015" to "card",
	// карточки
	"deck.016" to "cards",
	// карточек
	"deck.017" to "cards",
	//  · ВЕРНУЛАСЬ
	"card.001" to " · CAME BACK",
	// тап в любом месте
	"card.002" to "tap anywhere",
	// ↑ ЛЕГКО
	"card.003" to "↑ EASY",
	// ↓ ТРУДНО
	"card.004" to "↓ HARD",
	// ← НЕ ПОМНЮ
	"card.005" to "← FORGOT",
	// ПОМНЮ →
	"card.006" to "REMEMBER →",
	// секунду…
	"sess.001" to "one moment…",
	// знакомство
	"sess.002" to "first look",
	// МИНИМУМ СДЕЛАН
	"sess.003" to "MINIMUM DONE",
	// Колода пройдена
	"sess.004" to "Deck finished",
	// На сегодня всё
	"sess.005" to "That is all for today",
	// Сегодня карточек нет
	"sess.006" to "No cards today",
	// Повторять больше нечего — остальное ещё не подошло по сроку
	"sess.007" to "Nothing left to review — the rest is not due yet",
	// ПРОВЕРИТЬ ЕЩЁ РАЗ
	"sess.008" to "CHECK AGAIN",
	// ЕЩЁ НЕМНОГО  +5
	"sess.009" to "A BIT MORE  +5",
	// только повторения, новые чанки от этого не добавятся
	"sess.010" to "reviews only, no new chunks come from this",
	// этот ответ отменить уже нельзя
	"sess.011" to "this answer can no longer be undone",
	// ответ записан
	"sess.012" to "answer recorded",
	// ОТМЕНИТЬ
	"sess.013" to "UNDO",
	// ОК
	"sess.014" to "OK",
	// узнать
	"sess.015" to "recognise",
	// вставить
	"sess.016" to "fill in",
	// сказать
	"sess.017" to "say it",
	// План дня закрыт. Новые чанки придут сами завтра.
	"sess.018" to "The day's plan is closed. New chunks will come by themselves tomorrow.",
	// Первый день — берём совсем немного.
	"sess.019" to "First day — we take very little.",
	// Всё повторено, срок следующих ещё не наступил.
	"sess.020" to "Everything is reviewed, the next ones are not due yet.",
	// Повторений впереди и так много — новые слова подождут.
	"sess.021" to "There are plenty of reviews ahead already — new words will wait.",
	// Сначала разбираем накопившееся, потом новое.
	"sess.022" to "First we clear what piled up, then the new.",
	// После перерыва сначала разогрев на старом.
	"sess.023" to "After a break, a warm-up on the old first.",
	// Неделя вышла тихая — новое подождёт, сроки уже сдвинуты, долгов нет.
	"sess.024" to "The week came out quiet — the new will wait, the dates are already shifted, there are no debts.",
	// Для новых чанков поздно — познакомимся утром, повторения на месте.
	"sess.025" to "Too late for new chunks — we will meet them in the morning, reviews stay.",
	// Пока старое не закрепится — без новых.
	"sess.026" to "Until the old settles — no new ones.",
	// Режим возвращения: несколько коротких дней, без долгов.
	"sess.027" to "Return mode: a few short days, no debts.",
	// Добавлю хотя бы один новый чанк, чтобы не стоять на месте.
	"sess.028" to "I will add at least one new chunk so we do not stand still.",
	// Новых чанков здесь больше нет — все уже знакомы. Повторения продолжат приходить по срокам,
	"sess.029" to "There are no new chunks here any more — all of them are familiar. Reviews will keep coming on their dates, and new material needs one more deck.",
	// следующие — сегодня в 
	"sess.030" to "next — today at ",
	// следующие — завтра в 
	"sess.031" to "next — tomorrow at ",
	// следующие — через 
	"sess.032" to "next — in ",
	// дней
	"sess.033" to "days",
	// день
	"sess.034" to "day",
	// дня
	"sess.035" to "days",
	// дней
	"sess.036" to "days",
	//  · <1 МИН
	"sess.037" to " · <1 MIN",
	//  МИН
	"sess.038" to " MIN",
	// карточек
	"sess.039" to "cards",
	// карточка
	"sess.040" to "card",
	// карточки
	"sess.041" to "cards",
	// карточек
	"sess.042" to "cards",
	// Фразами, а не словами
	"onb.001" to "In phrases, not in words",
	// Внутри — готовые чанки: короткие живые куски речи. Новые добавляются сами — ничего не надо
	"onb.002" to "Inside are ready-made chunks: short living pieces of speech. New ones are added by themselves — nothing has to be typed in by hand.",
	// Пропуск — не провал
	"onb.003" to "A skip is not a failure",
	// Если день или неделя пропали, завала на входе не будет. Старое уйдёт в тихий пул и будет в
	"onb.004" to "If a day or a week went missing, there will be no pile-up at the door. The old goes into a quiet pool and comes back little by little, and new chunks arrive only when there is room.",
	// Минимум — одна карточка
	"onb.005" to "The minimum is one card",
	// Одна карточка закрывает день целиком. Захочется больше — есть кнопка «ещё немного», и она 
	"onb.006" to "One card closes the whole day. If you want more, there is an «a bit more» button, and it will not make tomorrow heavier.",
	// ГОТОВЛЮ КАРТОЧКИ…
	"onb.007" to "PREPARING CARDS…",
	// ДАЛЬШЕ
	"onb.008" to "NEXT",
	// НАЧАТЬ
	"onb.009" to "START",
	// ПРОПУСТИТЬ
	"onb.010" to "SKIP",
	// ОТЛИЧНОЕ
	"speaker.001" to "EXCELLENT",
	// ХОРОШЕЕ
	"speaker.002" to "GOOD",
	// ОБЫЧНОЕ
	"speaker.003" to "ORDINARY",
	// НИЗКОЕ
	"speaker.004" to "LOW",
	// Одна карточка
	"remind.001" to "One card",
	// Одной достаточно, чтобы день был закрыт
	"remind.002" to "One is enough to close the day",
)
