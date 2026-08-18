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
	// Вибра��ия
	"set.022" to "Vibration",
	// Короткий отклик на свайп
	// Язык
	"set.024" to "Language",
	// Язык самого приложения. На колоды он не влияет: переводы в карточках остаются такими, каки
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
	// В папку Документы/ikna
	"set.055" to "Into the Documents/ikna folder",
	// ВЫГРУЗИТЬ
	"set.056" to "EXPORT",
	// Не удалось сохранить файл
	"set.057" to "Could not save the file",
	// Настройки сохранены. Журнал пока пуст — выгружать нечего.
	"set.058" to "Settings saved. The log is still empty — there is nothing to export.",
	// Журнал и настройки сохранены в Документы/ikna
	"set.059" to "Log and settings saved to Documents/ikna",
	// ВОССТАНОВИТЬ
	"set.060" to "RESTORE",
	// Перерывы
	// Настраивать нечего и включать нечего. Приложение смотрит, сколько ты реально занимался, и 
	// Редкое
	"set.063" to "Rare",
	// То, что нужно раз в год или ни разу. Спрятано не потому, что сложно, а чтобы не нажать слу
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
	"set.072" to "A full reset: cards, dates, statistics, the answer log and the settings themselves. The app becomes what it was right after installation and restarts. Before erasing, the log is exported to Documents/ikna — it can be restored afterwards.",
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
	// ПАЛИТРА
	"set.114" to "PALETTE",
	// Цвет самого приложения. Светлая и тёмная версии — это одна палитра при разном освещении, а не две разные темы.
	// УГОЛЬ
	"set.116" to "EMBER",
	// БИБЛИОТЕКА
	"set.117" to "LIBRARY",
	// ЧЕРНИЛА
	"set.118" to "INK",
	// СЛИВА
	"set.119" to "PLUM",
	// НОЛЬ
	"set.120" to "ZERO",
	// НЕЙТРАЛЬНАЯ
	"set.121" to "NEUTRAL",
	// РОЗА
	"set.125" to "ROSE",
	// ИНЕЙ
	"set.126" to "FROST",
	// ФОСФОР
	"set.127" to "PHOSPHOR",
	// ОСВЕЩЕНИЕ
	"set.122" to "LIGHTING",
	"set.128" to "ALL LANGUAGES",
	"set.130" to "Phone voice",
	"set.131" to "Reads the languages no model of yours reads",
	"set.132" to "Speak every time",
	"set.133" to "A card reads itself every time it comes up",
	"set.134" to "A card reads itself only at first contact",
	"set.135" to "Cards taken out of rotation: ",
	"set.136" to "Put them back",
	"set.137" to "The cards are back",
	"set.138" to "Updates",
	"set.139" to "The app is installed from a file, so nothing but the app itself can tell you a newer one exists.",
	"set.140" to "UPDATE",
	// БЕТА
	"set.123" to "BETA",
	// Пока в бете и по умолчанию выключено: как звучит голос, решает движок на телефоне, а плохой голос хуже тишины.
	"set.124" to "In beta and off by default: how the voice sounds is decided by the engine on the phone, and a bad voice is worse than silence.",
	// ← НАЗАД
	"dbg.001" to "← BACK",
	// ВЫГРУЗИТЬ ЖУРНАЛ ОТВЕТОВ
	"dbg.002" to "EXPORT THE ANSWER LOG",
	// Файл: Документы/ikna/
	"dbg.003" to "File: Documents/ikna/",
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
	"deck.005" to "No decks yet. The plus at the bottom has the AI prompt and a place to paste.",
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
	//  из 
	//  · знаешь 
	// карточек
	"deck.014" to "cards",
	// карточка
	"deck.015" to "card",
	// карточки
	"deck.016" to "cards",
	// карточек
	"deck.017" to "cards",
	"deck.018" to " · <1 min",
	"deck.019" to " min",
	"add.001" to "New deck",
	"add.002" to "A deck is plain text. One line is one card: a phrase, a sentence containing it, and the translation, separated by |.",
	"add.003" to "Copy the prompt and send it to any AI.",
	"add.004" to "Tell it the language and the topic: “Polish, cooking, 100 cards”.",
	"add.005" to "Copy the whole answer, or save it as a file.",
	"add.006" to "Come back here and paste the text, or pick the file.",
	"add.007" to "Copy the prompt",
	"add.008" to "Save the prompt as a file",
	"add.009" to "Prompt copied",
	"add.010" to "Prompt saved",
	"add.011" to "Could not save the prompt",
	"add.012" to "Paste the deck lines here",
	"add.013" to "Paste an example",
	"add.014" to "Add from text",
	"add.015" to "Pick a file",
	"add.016" to "PHRASE | SENTENCE CONTAINING IT | TRANSLATION",
	"add.017" to "reading…",
	"add.018" to "That file is too large for a deck",
	"add.019" to "Could not read the file",
	"add.020" to "Paste the text first",
	"add.021" to "Cards added: ",
	"add.022" to ", lines skipped: ",
	"add.023" to "Line ",
	"add.024" to " — ",
	"add.025" to "No line could be used",
	"add.026" to "Done",
	"add.027" to "get used to|It takes a while to get used to the noise.|to get accustomed\nkeep an eye on|Can you keep an eye on my bag?|to watch over",
	"add.028" to "Example pasted",
	"add.029" to "Clear",
	"add.030" to "My deck",
	"add.031" to "the line is not three parts",
	"add.032" to "an empty field",
	"add.033" to "the phrase is not in the sentence",
	"add.034" to "the line is too long",
	"add.035" to "that phrase was already used",
	"share.001" to "share",
	"share.002" to "Send deck",
	"share.003" to "could not share",
	"share.004" to "this deck has nothing to send yet",
	// Экран одной колоды
	"dp.001" to "In use",
	"dp.002" to "Switched off",
	"dp.003" to "Deck language",
	"dp.004" to "Used for the voice only. A deck imported from a file starts with no language, which is why it cannot be read aloud until one is chosen.",
	"dp.005" to "no voice",
	"dp.006" to "Send deck",
	"dp.007" to "Delete deck",
	"dp.008" to "Tap again if you really mean it",
	"dp.009" to "The cards of this deck and their schedule go. The answers stay in the statistics — that log is never rewritten.",
	"dp.010" to "Add cards",
	"dp.011" to "Adding…",
	"dp.013" to "Deck name",
	"add.036" to "how this works",
	"add.037" to "hide",
	"add.038" to "Which language the cards are in",
	"add.039" to "Used for the voice only. It can be changed later on the deck's own page.",
	"add.040" to "What to ask the model for",
	"add.041" to "How many cards",
	"add.042" to "Language of the meanings",
	"add.043" to "starting out",
	"add.044" to "can hold a conversation",
	"add.045" to "fluent",
	"add.046" to "Level",
	"add.047" to "Topic or situation",
	"add.048" to "for example, cafés and ordering food",
	"add.049" to "The answers go into the prompt itself, so there is nothing left to type into the chat.",
	"add.050" to "Worth checking, lines: ",
	"add.051" to "the meaning repeats the term itself",
	"add.052" to "a hedged wording",
	"add.053" to "this meaning already appeared above",
	"add.054" to "has numbers — check them against the source",
	"add.055" to "What is being learned",
	"add.056" to "a language",
	"add.057" to "a subject",
	"add.058" to "A subject deck is not read aloud and never asks you to speak — only to recognise and to recall.",
	"add.059" to "some background",
	"add.060" to "Subject and section",
	"add.061" to "for example, neuroscience, synaptic plasticity",
	"add.062" to "Language of the cards",
	"add.063" to "Paste from clipboard",
	"add.064" to "The clipboard has no text",
	"add.065" to "lines",
	"add.066" to "characters",
	"add.067" to "SHOW THE TEXT",
	"add.068" to "HIDE THE TEXT",
	"add.069" to "and more lines: ",
	"add.070" to "the whole text arrived as one line. Press “paste from clipboard”: a keyboard cuts large pastes short",
	"add.071" to "The text was longer than the limit and was cut. Split the deck in two.",
	// тап в любом месте
	"card.002" to "tap anywhere",
	// ← НЕ ЗНАЮ
	"card.003" to "← DON'T KNOW",
	// ЗНАЮ →
	"card.004" to "KNOW →",
	// секунду…
	"sess.001" to "one moment…",
	// знакомство
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
	"sess.016" to "complete it",
	// сказать
	"sess.017" to "say it out loud",
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
	"sess.043" to "Yesterday ran long — today is a lighter day, with nothing new in it.",
	"sess.044" to "MARK AS WRONG",
	"sess.045" to "Taken out of rotation. The mistake was not written into your statistics.",
	"sess.046" to "the term",
	"sess.047" to "from memory",
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
	// Влево и вправо
	"onb.011" to "Left and right",
	// Карточку смахивают: влево — не знаю, вправо — знаю. Можно вместо этого нажать на слово внизу. Ответ отменяется сразу после него, так что ошибиться не страшно.
	"onb.012" to "You swipe the card: left if you don't know it, right if you do. Tapping the word at the bottom does the same thing. An answer can be undone right after it, so getting one wrong costs nothing.",
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
	// Назад
	"a11y.001" to "Back",
	// Настройки
	"a11y.002" to "Settings",
	// Статистика
	"a11y.003" to "Statistics",
	// Добавить колоду
	"a11y.004" to "Add a deck",
	// Прочитать вслух
	"a11y.005" to "Read aloud",
	// Колода в работе
	"a11y.006" to "Deck in use",
	"a11y.007" to "Explain this number",
	// Не знаю
	"a11y.008" to "Don't know",
	// Знаю
	"a11y.009" to "Know",
	"a11y.010" to "Deck settings",
	// Озвучка
	"voice.001" to "Voice",
	// Сейчас читает
	"voice.002" to "Reading now",
	// Системный голос телефона
	"voice.003" to "The phone's own voice",
	// В этой сборке нет движка моделей — читает только системный голос
	"voice.004" to "This build carries no model engine — only the phone's voice reads",
	// Модель добавлена, но не загрузилась. Читает системный голос
	"voice.005" to "A model is installed but did not load. The phone's voice reads",
	// Модели нет. Читает системный голос телефона
	"voice.006" to "No model. The phone's own voice reads",
	// Проверяю модель…
	"voice.007" to "Checking the model…",
	// Модель загружена и готова
	"voice.008" to "The model is loaded and ready",
	// Проверить голос
	"voice.010" to "Test the voice",
	// Своя модель
	"voice.011" to "Your models",
	// Модель приносите вы: папка Kokoro или Piper с телефона. Ничего не скачивается — у приложения нет доступа в интернет.
	"voice.012" to "You bring the model: a Kokoro or Piper folder from the phone. Nothing is downloaded — the app has no internet access.",
	// Убрать модель
	"voice.013" to "Remove the model",
	// Язык модели
	"voice.014" to "Model language",
	// Любой
	"voice.015" to "Any",
	// Это сборка без движка. Своя модель здесь не заработает — нужна сборка с пометкой voice.
	"voice.016" to "This build has no engine. Your own model will not work here — take the build marked voice.",
	// Заменить модель
	"voice.017" to "Add another model",
	// Копирую файлы: 
	"voice.018" to "Files copied: ",
	// Это не похоже на модель: в папке нет файла .onnx
	"voice.019" to "This does not look like a model: no .onnx file in the folder",
	// Выбрана папка уровнем выше. Откройте её и выберите папку с моделью
	"voice.020" to "The folder picked is one level too high. Open it and pick the folder with the model",
	// В папке несколько моделей. Оставьте одну
	"voice.021" to "Several models in one folder. Leave one",
	// Нет файла tokens.txt. Нужна сборка модели для sherpa-onnx, а не файлы Piper как есть
	"voice.022" to "No tokens.txt. Take the sherpa-onnx build of the model, not the raw Piper files",
	// Не удалось скопировать: 
	"voice.023" to "Could not copy: ",
	// Модель добавлена
	"voice.024" to "Model added",
	// Озвучка выключена в настройках
	"voice.025" to "Reading aloud is off - the switch above turns it on",
	// Модели лежат на странице релизов sherpa-onnx, раздел tts-models. Kokoro — английский, Piper — почти любой язык, включая русский и польский.
	"voice.026" to "Models live on the sherpa-onnx releases page, under tts-models. Kokoro speaks English; Piper covers almost any language, Russian and Polish included.",
	// Нет файла voices.bin. Скачивание Kokoro оборвалось
	"voice.027" to "No voices.bin. The Kokoro download stopped early",
	// Нет данных для произношения: ни espeak-ng-data, ни lexicon
	"voice.028" to "Nothing to pronounce with: no espeak-ng-data and no lexicon",
	// Добавить модель
	"voice.029" to "Add a model",
	"voice.030" to "Read the cards aloud",
	"voice.031" to "While this is off the cards stay silent, model or no model",
	"voice.032" to "Who reads which deck",
	"voice.033" to "the model reads it",
	"voice.034" to "the phone's voice reads it",
	"voice.035" to "nobody: the model does not know this language and the phone has no voice for it",
	"voice.036" to "A model speaks one language. A deck's language is asked at import and can be changed in the deck.",
	"voice.037" to "NO LANGUAGE",
	"voice.038" to "switched off",
	"voice.039" to "Voice number",
	"voice.040" to "One model speaks each language",
	"voice.041" to "Add a .tar.bz2 archive",
	"voice.042" to "This is not a model archive",
	"voice.043" to "Not enough space: an unpacked model is about three times the archive",
	"voice.044" to "The app unpacks the archive itself",
	"voice.045" to "Language read off the model's name:",
	"voice.046" to "OTHER LANGUAGE",
	"voice.047" to "How many voices this model has is something only the model can say: press the test button and the number appears here.",
	"voice.048" to "Speed",
	"voice.049" to "Unpacking:",
	"voice.050" to "A large model takes minutes to unpack: bzip2 is undone by the phone's own processor.",
	"voice.051" to "You can leave this screen — the install carries on. Do not put the app away for long.",
	// Оформление
	"look.001" to "Appearance",
	// Буквы и цвет квадрата в списке колод. Видно только вам: тому, кому отправите колоду, придут только карточки.
	"look.002" to "The letters and the colour of the square on the deck list. Yours only: whoever you send the deck to gets the cards and nothing else.",
	// Цвет квадрата
	"look.003" to "Colour of the square",
	// 1-2 символа. Оставьте пустым - буквы подберутся сами.
	"look.004" to "One or two characters. Left empty, the square works out its own.",
	// Логотип ikna
	"bar.001" to "The ikna mark",
	// Слева в нижнем баре. Выключите — место уйдёт кнопкам
	// Под левую руку
	"bar.003" to "For the left hand",
	// Нижний бар зеркалится: плюс уходит в левый угол, остальные кнопки — вправо

	// The update window and the update section in settings.
	"upd.001" to "UPDATE AVAILABLE",
	"upd.002" to "Size: ",
	"upd.003" to "MB",
	"upd.004" to "What is new:",
	"upd.005" to "UPDATE",
	"upd.006" to "SKIP",
	"upd.007" to "The app downloads the file itself; the bar and the percentage are here. Android's installer puts the new version over the old one, and cards and progress stay.",
	"upd.008" to "Check for updates",
	"upd.009" to "One request to the releases page, at most once a day. Nothing is sent. Switched off, no socket is opened at all.",
	"upd.010" to "CHECK NOW",
	"upd.011" to "Installed: ",
	"upd.012" to "Nothing newer. If there is no network the check simply failed — the app cannot tell those apart.",
	"upd.013" to "RELEASES PAGE",
	"upd.014" to "CHECKING…",
	"upd.015" to "Skipped: ",
	"upd.016" to "Update to ",
	"upd.017" to "DOWNLOADING",
	"upd.018" to "The file is here. Android's installer puts the new version over the old one — cards, progress and settings stay.",
	"upd.019" to "CANCEL",
	"upd.020" to "INSTALL",
	"upd.021" to "Android asks permission to install apps from this source; without it the installer will not open. The file is already downloaded and stays.",
	"upd.022" to "ALLOW",
	"upd.023" to "The download failed — the connection dropped, or the file arrived short. Retry, or take the file in the browser.",
	"upd.024" to "RETRY",
	"upd.025" to "IN THE BROWSER",
)
