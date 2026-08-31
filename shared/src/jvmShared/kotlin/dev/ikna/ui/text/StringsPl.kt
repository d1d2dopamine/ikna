package dev.ikna.ui.text

val STRINGS_PL: Map<String, String> = mapOf(
	// Напоминание
	"app.001" to "Przypomnienie",
	// Одно напоминание в день, если минимум ещё не сделан
	"app.002" to "Jedno przypomnienie dziennie, tylko jeśli minimum nie jest jeszcze zrobione",
	// Не удалось прочитать файл
	"font.001" to "Nie udało się odczytać pliku",
	// Файл слишком большой для шрифта
	"font.002" to "Plik jest za duży jak na czcionkę",
	// Не удалось сохранить шрифт
	"font.003" to "Nie udało się zapisać czcionki",
	// Не удалось сохранить шрифт
	"font.004" to "Nie udało się zapisać czcionki",
	// Это не файл шрифта
	"font.005" to "To nie jest plik czcionki",
	// Это коллекция шрифтов .ttc — нужен один шрифт, .ttf или .otf
	"font.006" to "To kolekcja czcionek .ttc — potrzebna jest jedna czcionka, .ttf albo .otf",
	// Это веб-шрифт .woff — Android его не читает, нужен .ttf или .otf
	"font.007" to "To czcionka webowa .woff — Android jej nie czyta, potrzebny .ttf albo .otf",
	// Это не файл шрифта
	"font.008" to "To nie jest plik czcionki",
	// Файл шрифта повреждён
	"font.009" to "Plik czcionki jest uszkodzony",
	// Файл шрифта обрезан
	"font.010" to "Plik czcionki jest ucięty",
	// Файл шрифта обрезан
	"font.011" to "Plik czcionki jest ucięty",
	// В шрифте нет таблицы символов
	"font.012" to "W czcionce nie ma tablicy znaków",
	// В шрифте нет самих букв
	"font.013" to "W czcionce nie ma samych liter",
	// Своя колода
	"deckrepo.001" to "Własna talia",
	// Статистика
	"stats.001" to "Statystyka",
	// ПОСЛЕДНИЕ 30 ДНЕЙ
	"stats.002" to "OSTATNIE 30 DNI",
	// Каждая метка — день с занятием, справа сегодня. Пропуск ничего не обнуляет и не обрывает: 
	"stats.003" to "Każdy znacznik to dzień z nauką, dziś po prawej. Przerwa niczego nie zeruje i nie przerywa: nie ma tu serii, nie ma czego łamać.",
	// НОРМА ДНЯ
	"stats.004" to "NORMA DNIA",
	// Карточек в день. Посчитано по твоим последним дням, пересчитывается само.
	"stats.005" to "Kart dziennie. Policzone z twoich ostatnich dni, przelicza się samo.",
	// Ориентир на первые дни, а не измерение. Свою цифру посчитаю, когда наберётся хотя бы три д
	"stats.006" to "Na razie punkt wyjścia, a nie pomiar. Własną liczbę policzę, gdy uzbiera się choćby trzy dni z nauką — wcześniej byłaby zmyślona.",
	// СЛОВ В ПАМЯТИ
	"stats.007" to "SŁÓW W PAMIĘCI",
	// ОТВЕЧЕНО СЕГОДНЯ
	"stats.008" to "ODPOWIEDZI DZIŚ",
	// Слова считаются отдельно от чанков: одно слово встречается в разных фразах и держится креп
	"stats.009" to "Słowa liczone są osobno od chunków: jedno słowo pojawia się w różnych zwrotach i trzyma się mocniej niż każdy z nich.",
	// ВЕРНЁТСЯ ЗА 14 ДНЕЙ
	"stats.010" to "WRÓCI W 14 DNI",
	// Сколько карточек подошлёт по сроку. Если где-то вырастает гора — новые чанки в те дни доба
	"stats.011" to "Ile kart przypadnie na termin. Jeśli gdzieś rośnie góra — w te dni nowe chunki nie dojdą.",
	// УДЕРЖАНИЕ
	"stats.012" to "UTRZYMANIE",
	// Нужно хотя бы 20 повторений, чтобы это была цифра, а не догадка. Сейчас их 
	"stats.013" to "Potrzeba co najmniej 20 powtórek, żeby to była liczba, a nie zgadywanie. Na razie jest ich ",
	// Из 
	"stats.014" to "Z ",
	//  повторений за месяц вспомнилось 
	"stats.015" to " powtórek w miesiącu przypomniało się ",
	// %. Считаются только повторения — первую встречу забыть нельзя.
	"stats.016" to "%. Liczą się tylko powtórki — pierwszego spotkania nie da się zapomnieć.",
	// Расписание метит примерно в 90%. Ниже 80% — интервалы для тебя длинноваты. Нагрузка подстр
	"stats.017" to "Rozkład celuje w około 90%. Poniżej 80% odstępy są dla ciebie za długie. Obciążenie dostroi się samo, ale jeśli tak trzyma tygodniami — weź mniejszą normę.",
	// Расписание метит примерно в 90%. Выше 95% — повторов больше, чем нужно: память выдержала б
	"stats.018" to "Rozkład celuje w około 90%. Powyżej 95% powtórek jest więcej niż trzeba: pamięć wytrzymałaby rzadsze podejścia, a to zbędne minuty.",
	// Расписание метит примерно в 90%, и сейчас всё в этом коридоре. Трогать ничего не надо.
	"stats.019" to "Rozkład celuje w około 90% i teraz wszystko mieści się w tym korytarzu. Nic nie trzeba ruszać.",
	// МИНУТ СЕГОДНЯ
	"stats.020" to "MINUT DZIŚ",
	// ЗА НЕДЕЛЮ
	"stats.021" to "W TYGODNIU",
	// Считается только время с карточкой на экране: паузы, когда телефон отложен, сюда не попада
	"stats.022" to "Liczy się tylko czas z kartą na ekranie: przerwy, gdy telefon leży, tu nie wchodzą. Jedna karta zajmuje ci około ",
	//  сек.
	"stats.023" to " sek.",
	// Считается только время с карточкой на экране: паузы, когда телефон отложен, сюда не попада
	"stats.024" to "Liczy się tylko czas z kartą na ekranie: przerwy, gdy telefon leży, tu nie wchodzą.",
	// КОГДА ИДЁТ ЛУЧШЕ
	"stats.025" to "KIEDY IDZIE LEPIEJ",
	// Лучше всего вспоминается около 
	"stats.026" to "Najlepiej przypomina się około ",
	// . Это не приказ заниматься именно тогда — просто в этот час тебе дешевле, и напоминание ра
	"stats.027" to ". To nie nakaz nauki właśnie wtedy — po prostu o tej godzinie jest ci taniej, a przypomnienie rozsądnie ustawić godzinę wcześniej.",
	// Пока рано выделять час: нужно хотя бы 12 повторений внутри одного часа. Бледные столбики —
	"stats.028" to "Za wcześnie, by wskazać godzinę: potrzeba co najmniej 12 powtórek w jednej godzinie. Blade słupki to godziny, o których danych jest jeszcze mało.",
	// НЕ ДЕРЖИТСЯ
	"stats.029" to "NIE TRZYMA SIĘ",
	// Пока таких нет: ничего не забывалось по четыре раза и больше.
	"stats.030" to "Na razie takich nie ma: nic nie zostało zapomniane cztery razy ani więcej.",
	// Справа — сколько раз фраза забывалась. Это про фразу, а не про тебя: обычно она слишком дл
	"stats.031" to "Po prawej: ile razy zapomniano tę frazę. To o frazie, nie o tobie.",
	// СЕГОДНЯ
	"stats.032" to "DZIŚ",
	// Не удалось открыть файл
	"set.001" to "Nie udało się otworzyć pliku",
	// Не удалось открыть файл
	"set.002" to "Nie udało się otworzyć pliku",
	// Шрифт применён: 
	"set.003" to "Czcionka zastosowana: ",
	// Без разрешения на уведомления напоминание не придёт
	"set.004" to "Bez zgody na powiadomienia przypomnienie nie przyjdzie",
	// Файл настроек повреждён
	"set.005" to "Plik ustawień jest uszkodzony",
	// Настройки восстановлены
	"set.006" to "Ustawienia przywrócone",
	// Настройки восстановлены. Шрифт «
	"set.007" to "Ustawienia przywrócone. Czcionkę «",
	// » нужно выбрать заново — сам файл шрифта твой, и в бэкап он не кладётся.
	"set.008" to "» trzeba wybrać na nowo — plik czcionki jest twój i nie trafia do kopii.",
	// Добавлено ответов: 
	"set.009" to "Dodano odpowiedzi: ",
	//  · пересчитано 
	"set.010" to " · przeliczono ",
	//  · пропущено 
	"set.011" to " · pominięto ",
	// Настройки
	"set.012" to "Ustawienia",
	// Нагрузка
	"set.013" to "Obciążenie",
	// Сколько повторений в день считать нормой. От этого зависит, сколько новых чанков придёт за
	// АВТО
	"set.015" to "AUTO",
	// РУЧНОЙ
	"set.016" to "RĘCZNY",
	// СЕЙЧАС · 
	"set.017" to "TERAZ · ",
	// КАРТОЧЕК В ДЕНЬ
	"set.018" to "KART DZIENNIE",
	// Вид
	"set.019" to "Wygląd",
	// Анимации
	"set.020" to "Animacje",
	// Карточки улетают, конец дня с анимацией
	// Вибрация
	"set.022" to "Wibracje",
	// Короткий отклик на свайп
	// Язык
	"set.024" to "Język",
	// Язык самого приложения. На колоды он не влияет: переводы в карточках остаются такими, каки
	// Озвучка
	"set.026" to "Mowa",
	// Говорит движок синтеза речи, который уже стоит на телефоне. Ничего не скачивается и ничего
	"set.027" to "Mówi silnik już zainstalowany w telefonie. Nic się nie pobiera i nic nie idzie do sieci.",
	// Читать вслух
	"set.028" to "Czytaj na głos",
	// Значок звука появляется только там, где он не выдаёт ответ
	"set.029" to "Znak dźwięku pojawia się tylko tam, gdzie nie zdradza odpowiedzi",
	// смотрю, что есть на телефоне…
	"set.030" to "sprawdzam, co jest w telefonie…",
	// На телефоне нет движка синтеза речи. Подойдёт любой офлайновый — например RHVoice или Sher
	"set.031" to "W telefonie nie ma silnika syntezy mowy. Wystarczy dowolny offline'owy — na przykład RHVoice albo SherpaTTS z F-Droid. Po instalacji wróć tutaj.",
	// НАСТРОЙКИ СИНТЕЗА РЕЧИ
	"set.032" to "USTAWIENIA SYNTEZY MOWY",
	// Не нашёл этот раздел в настройках телефона
	"set.033" to "Nie znalazłem tej sekcji w ustawieniach telefonu",
	// Движок есть, но офлайн-голосов для этих языков в нём нет. Их надо доставить — один раз, и 
	"set.034" to "Silnik jest, ale nie ma w nim głosów offline dla tych języków. Trzeba je dociągnąć — raz, a potem działają bez sieci.",
	// ДОУСТАНОВИТЬ ГОЛОСА
	"set.035" to "DOINSTALUJ GŁOSY",
	// Движок не умеет докачивать голоса сам — посмотри в его настройках
	"set.036" to "Silnik nie umie sam dociągać głosów — zajrzyj do jego ustawień",
	// офлайн-голосов для этого языка нет
	"set.037" to "brak głosów offline dla tego języka",
	// ПО УМОЛЧАНИЮ
	"set.038" to "DOMYŚLNY",
	// Первый звук после запуска может задуматься на пару секунд — движок просыпается. Следующая 
	"set.039" to "Pierwszy dźwięk po starcie może chwilę pomyśleć — silnik się budzi.",
	// Шрифт
	"set.040" to "Czcionka",
	// Свой .ttf или .otf для текста карточек и заголовков. Служебные подписи остаются моноширинн
	"set.041" to "Własny plik .ttf lub .otf do tekstu fiszek. Plik jest sprawdzany przed użyciem.",
	// СЕЙЧАС · СИСТЕМНЫЙ
	"set.042" to "TERAZ · SYSTEMOWA",
	// СЕЙЧАС · 
	"set.043" to "TERAZ · ",
	// ВЫБРАТЬ ФАЙЛ
	"set.044" to "WYBIERZ PLIK",
	// СБРОСИТЬ
	"set.045" to "ZRESETUJ",
	// Вернул системный шрифт
	"set.046" to "Wróciła czcionka systemowa",
	// Напоминание
	"set.047" to "Przypomnienie",
	// Одно в день, и только если минимум ещё не сделан. Никаких серий и укоров.
	// Напоминать
	"set.049" to "Przypominaj",
	// в 
	"set.050" to "o ",
	// выключено
	"set.051" to "wyłączone",
	// Данные
	"set.052" to "Dane",
	// Журнал ответов — единственное, что нельзя восстановить. Всё остальное считается из него за
	"set.053" to "Dziennik odpowiedzi to jedyne, czego nie da się odtworzyć. Cała reszta liczy się z niego na nowo.",
	// Авто-выгрузка раз в неделю
	"set.054" to "Auto-eksport raz w tygodniu",
	// В папку Документы/ikna
	"set.055" to "Do folderu Dokumenty/ikna",
	// ВЫГРУЗИТЬ
	"set.056" to "EKSPORTUJ",
	// Не удалось сохранить файл
	"set.057" to "Nie udało się zapisać pliku",
	// Настройки сохранены. Журнал пока пуст — выгружать нечего.
	"set.058" to "Ustawienia zapisane. Dziennik jest jeszcze pusty — nie ma czego eksportować.",
	// Журнал и настройки сохранены в Документы/ikna
	"set.059" to "Dziennik i ustawienia zapisane w Dokumenty/ikna",
	// ВОССТАНОВИТЬ
	"set.060" to "PRZYWRÓĆ",
	// Перерывы
	// Настраивать нечего и включать нечего. Приложение смотрит, сколько ты реально занимался, и 
	// Редкое
	"set.063" to "Rzadkie",
	// То, что нужно раз в год или ни разу. Спрятано не потому, что сложно, а чтобы не нажать слу
	// СКРЫТЬ
	"set.065" to "UKRYJ",
	// ПОКАЗАТЬ
	"set.066" to "POKAŻ",
	// ПЕРЕСЧИТАТЬ СЛОЙ СЛОВ
	"set.067" to "PRZELICZ WARSTWĘ SŁÓW",
	// Слой слов пересчитан по журналу
	"set.068" to "Warstwa słów przeliczona z dziennika",
	// ТЕХНИЧЕСКИЙ ЭКРАН
	"set.069" to "EKRAN TECHNICZNY",
	// НАЧАТЬ ЗАНОВО
	"set.070" to "ZACZNIJ OD NOWA",
	// Стереть всё
	"set.071" to "Wymaż wszystko",
	// Полный сброс: карточки, сроки, статистика, журнал ответов и сами настройки. Приложение ста
	"set.072" to "Fiszki, daty, statystyki, ustawienia i dziennik odpowiedzi znikają. Dziennik jest najpierw zapisany w Documents/ikna.",
	// ТОЧНО СТЕРЕТЬ ВСЁ
	"set.073" to "NA PEWNO WYMAŻ WSZYSTKO",
	// СТЕРЕТЬ ДАННЫЕ
	"set.074" to "WYMAŻ DANE",
	// Нажми ещё раз, если правда стереть. Отмены не будет.
	"set.075" to "Naciśnij jeszcze raz, jeśli naprawdę. Cofnięcia nie będzie.",
	// второе нажатие стирает сразу
	"set.076" to "drugie naciśnięcie wymazuje od razu",
	// нажать надо дважды
	"set.077" to "trzeba nacisnąć dwa razy",
	// Начать заново?
	"set.078" to "Zacząć od nowa?",
	// Сроки карточек, статистика и слой слов обнулятся. Журнал ответов останется — из него можно
	"set.079" to "Terminy kart, statystyka i warstwa słów wyzerują się. Dziennik odpowiedzi zostaje — z niego można wszystko wrócić przyciskiem «Przywróć».",
	// НАЧАТЬ ЗАНОВО
	"set.080" to "ZACZNIJ OD NOWA",
	// Статистика обнулена
	"set.081" to "Statystyka wyzerowana",
	// ОТМЕНА
	"set.082" to "ANULUJ",
	// ФОН
	"set.083" to "TŁO",
	// ТЕКСТ
	"set.084" to "TEKST",
	// ПРИГЛУШЁННЫЙ
	"set.085" to "STONOWANY",
	// АКЦЕНТ
	"set.086" to "AKCENT",
	// КОНТРАСТ · ТЕКСТ 
	"set.087" to "KONTRAST · TEKST ",
	//   ·  ПРИГЛУШЁННЫЙ 
	"set.088" to "  ·  STONOWANY ",
	//   ·  АКЦЕНТ 
	"set.089" to "  ·  AKCENT ",
	// что-то из этого плохо читается на своём фоне — цвет всё равно применён
	"set.090" to "coś z tego źle się czyta na swoim tle — kolor i tak zastosowany",
	// НАГРУЗКА
	"set.091" to "OBCIĄŻENIE",
	// ВИД
	"set.092" to "WYGLĄD",
	// ЯЗЫК
	"set.093" to "JĘZYK",
	// ОЗВУЧКА
	"set.094" to "MOWA",
	// ШРИФТ
	"set.095" to "CZCIONKA",
	// НАПОМИНАНИЕ
	"set.096" to "PRZYPOMNIENIE",
	// ДАННЫЕ
	"set.097" to "DANE",
	// РЕДКОЕ
	"set.098" to "RZADKIE",
	// КАК В СИСТЕМЕ
	"set.099" to "JAK W SYSTEMIE",
	// РУССКИЙ
	"set.100" to "РУССКИЙ",
	// ТЁМНАЯ
	"set.101" to "CIEMNY",
	// СВЕТЛАЯ
	"set.102" to "JASNY",
	// СВОЯ
	"set.103" to "WŁASNY",
	// ПОЛЬСКИЙ
	"set.104" to "POLSKI",
	// РУССКИЙ
	"set.105" to "ROSYJSKI",
	// АНГЛИЙСКИЙ
	"set.106" to "ANGIELSKI",
	// НЕМЕЦКИЙ
	"set.107" to "NIEMIECKI",
	// ИСПАНСКИЙ
	"set.108" to "HISZPAŃSKI",
	// ФРАНЦУЗСКИЙ
	"set.109" to "FRANCUSKI",
	// шрифт
	"set.110" to "czcionka",
	// ПАЛИТРА
	"set.114" to "PALETA",
	// Цвет самого приложения. Светлая и тёмная версии — это одна палитра при разном освещении, а не две разные темы.
	// УГОЛЬ
	"set.116" to "WĘGIEL",
	// БИБЛИОТЕКА
	"set.117" to "BIBLIOTEKA",
	// ЧЕРНИЛА
	"set.118" to "ATRAMENT",
	// СЛИВА
	"set.119" to "ŚLIWKA",
	// НОЛЬ
	"set.120" to "ZERO",
	// НЕЙТРАЛЬНАЯ
	"set.121" to "NEUTRALNA",
	// РОЗА
	"set.125" to "RÓŻA",
	// ИНЕЙ
	"set.126" to "SZRON",
	// ФОСФОР
	"set.127" to "FOSFOR",
	"set.141" to "ULTRAFIOLET",
	"set.142" to "LAGUNA",
	"set.143" to "KOBALT",
	// ОСВЕЩЕНИЕ
	"set.122" to "OŚWIETLENIE",
	"set.128" to "WSZYSTKIE JĘZYKI",
	"set.130" to "Głos telefonu",
	"set.131" to "Czyta języki, których nie czyta żaden twój model",
	"set.132" to "Czytaj za każdym razem",
	"set.133" to "Karta czyta się sama przy każdym pokazaniu",
	"set.134" to "Karta czyta się sama tylko przy pierwszym spotkaniu",
	"set.135" to "Kart wyjętych z obiegu: ",
	"set.136" to "Przywróć je",
	"set.137" to "Karty wróciły",
	"set.138" to "Aktualizacje",
	"set.139" to "Aplikacja instaluje się z pliku, więc o nowej wersji nie powie nic poza nią samą.",
	"set.140" to "AKTUALIZACJA",
	// БЕТА
	"set.123" to "BETA",
	// Пока в бете и по умолчанию выключено: как звучит голос, решает движок на телефоне, а плохой голос хуже тишины.
	"set.124" to "Na razie w wersji beta i domyślnie wyłączone: jak brzmi głos, decyduje silnik w telefonie, a zły głos jest gorszy od ciszy.",
	// ← НАЗАД
	"dbg.001" to "← WSTECZ",
	// ВЫГРУЗИТЬ ЖУРНАЛ ОТВЕТОВ
	"dbg.002" to "EKSPORTUJ DZIENNIK ODPOWIEDZI",
	// Файл: Документы/ikna/
	"dbg.003" to "Plik: Dokumenty/ikna/",
	// Не удалось сохранить файл
	"dbg.004" to "Nie udało się zapisać pliku",
	// ПЕРЕСЧИТАТЬ СЛОЙ СЛОВ
	"dbg.005" to "PRZELICZ WARSTWĘ SŁÓW",
	// Слой слов пересчитан
	"dbg.006" to "Warstwa słów przeliczona",
	// ПЕРЕСОБРАТЬ ПЛАН ДНЯ
	"dbg.007" to "PRZEBUDUJ PLAN DNIA",
	// План на сегодня пересобран
	"dbg.008" to "Plan na dziś przebudowany",
	// Решения регулятора по дням
	"dbg.009" to "Decyzje regulatora dzień po dniu",
	//   новых=
	"dbg.010" to "  nowe=",
	//   к повтору=
	"dbg.011" to "  do powtórki=",
	//   прогноз3д=
	"dbg.012" to "  prognoza3d=",
	//   долг=
	"dbg.013" to "  zaległość=",
	//   точность=
	"dbg.014" to "  celność=",
	//   запас=
	"dbg.015" to "  zapas=",
	// Не получилось прочитать ни одной строки
	"deck.001" to "Nie udało się odczytać ani jednej linii",
	// Добавлено чанков: 
	"deck.002" to "Dodano chunków: ",
	// , пропущено 
	"deck.003" to ", pominięto ",
	// Колоды
	"deck.004" to "Talie",
	// Пока ни одной колоды. Плюс сверху добавит файл .jsonl.
	"deck.005" to "Nie ma jeszcze talii. Plus na dole ma prompt dla AI i miejsce na wklejenie.",
	// читаю файл…
	"deck.006" to "czytam plik…",
	// СЕГОДНЯ
	"deck.007" to "DZIŚ",
	// ничего не ждёт
	"deck.008" to "nic nie czeka",
	// сегодня 
	"deck.009" to "dziś ",
	// на сегодня нет
	"deck.010" to "na dziś nic",
	// введено 
	//  из 
	//  · знаешь 
	// карточек
	"deck.014" to "kart",
	// карточка
	"deck.015" to "karta",
	// карточки
	"deck.016" to "karty",
	// карточек
	"deck.017" to "kart",
	"deck.018" to " · <1 min",
	"deck.019" to " min",
	"add.001" to "Nowa talia",
	"add.002" to "Talia to zwykły tekst. Jedna linia to jedna karta: fraza, zdanie z tą frazą i tłumaczenie, oddzielone |.",
	"add.003" to "Skopiuj prompt i wyślij go dowolnej AI.",
	"add.004" to "Napisz jej język i temat: „polski, kuchnia, 100 kart”.",
	"add.005" to "Skopiuj całą odpowiedź albo zapisz ją do pliku.",
	"add.006" to "Wróć tutaj i wklej tekst albo wybierz plik.",
	"add.007" to "Skopiuj prompt",
	"add.008" to "Zapisz prompt jako plik",
	"add.009" to "Prompt skopiowany",
	"add.010" to "Prompt zapisany",
	"add.011" to "Nie udało się zapisać promptu",
	"add.012" to "Wklej tutaj linie talii",
	"add.013" to "Wklej przykład",
	"add.014" to "Dodaj z tekstu",
	"add.015" to "Wybierz plik",
	"add.016" to "FRAZA | ZDANIE Z TĄ FRAZĄ | TŁUMACZENIE",
	"add.017" to "czytam…",
	"add.018" to "Ten plik jest za duży na talię",
	"add.019" to "Nie udało się odczytać pliku",
	"add.020" to "Najpierw wklej tekst",
	"add.021" to "Dodano kart: ",
	"add.022" to ", pominięto linii: ",
	"add.023" to "Linia ",
	"add.024" to " — ",
	"add.025" to "Żadna linia się nie nadała",
	"add.026" to "Gotowe",
	"add.027" to "get used to|It takes a while to get used to the noise.|przyzwyczaić się\nkeep an eye on|Can you keep an eye on my bag?|przypilnować",
	"add.028" to "Przykład wklejony",
	"add.029" to "Wyczyść",
	"add.030" to "Moja talia",
	"add.031" to "linia nie ma trzech części",
	"add.032" to "puste pole",
	"add.033" to "frazy nie ma w zdaniu",
	"add.034" to "linia jest za długa",
	"add.035" to "ta fraza już była",
	"share.001" to "udostępnij",
	"share.002" to "Wyślij talię",
	"share.003" to "nie udało się udostępnić",
	"share.004" to "w tej talii nie ma jeszcze czego wysłać",
	// Экран одной колоды
	"dp.001" to "W użyciu",
	"dp.002" to "Wyłączona",
	"dp.003" to "Język talii",
	"dp.004" to "Potrzebny do lektora. Zaimportowana talia bierze język z fiszek — tutaj można go poprawić.",
	"dp.005" to "bez lektora",
	"dp.006" to "Wyślij talię",
	"dp.007" to "Usuń talię",
	"dp.008" to "Naciśnij jeszcze raz, jeśli na pewno",
	"dp.009" to "Znikną karty tej talii i ich terminy. Odpowiedzi zostaną w statystykach — ten dziennik nigdy nie jest nadpisywany.",
	"dp.010" to "Dodaj karty",
	"dp.011" to "Dodaję…",
	"dp.013" to "Nazwa talii",
	"dp.014" to "Wymowa",
	"dp.015" to "Jak brzmi fraza. Zapis angielski zrozumie prawie każdy, IPA jest dokładniejsza.",
	"dp.016" to "Angielski",
	"dp.017" to "IPA",
	"dp.018" to "Wyłączona",
	"dp.019" to "Nic nie jest pokazywane",
	"add.036" to "jak to działa",
	"add.037" to "ukryj",
	"add.038" to "W jakim języku są karty",
	"add.039" to "Potrzebny tylko do czytania na głos. Można to zmienić później na stronie talii.",
	"add.040" to "O co poprosić model",
	"add.041" to "Ile kart",
	"add.042" to "Język znaczeń",
	"add.043" to "zaczynam",
	"add.044" to "potrafię rozmawiać",
	"add.045" to "swobodnie",
	"add.046" to "Poziom",
	"add.047" to "Temat lub sytuacja",
	"add.048" to "na przykład kawiarnie i zamawianie jedzenia",
	"add.049" to "Odpowiedzi trafią do samego promptu, w czacie nie trzeba już nic dopisywać.",
	"add.050" to "Warto sprawdzić, linii: ",
	"add.051" to "znaczenie powtarza sam termin",
	"add.052" to "niepewne sformułowanie",
	"add.053" to "to znaczenie już było wyżej",
	"add.054" to "są liczby — sprawdź je w źródle",
	"add.055" to "Czego się uczymy",
	"add.056" to "języka",
	"add.057" to "przedmiotu",
	"add.058" to "Talia przedmiotowa nie jest czytana na głos i nie każe mówić — tylko rozpoznać i przypomnieć.",
	"add.059" to "mam podstawy",
	"add.060" to "Przedmiot i dział",
	"add.061" to "na przykład neurobiologia, plastyczność synaptyczna",
	"add.062" to "Język kart",
	"add.063" to "Wklej ze schowka",
	"add.064" to "Schowek nie ma tekstu",
	"add.065" to "wierszy",
	"add.066" to "znaków",
	"add.067" to "POKAŻ TEKST",
	"add.068" to "UKRYJ TEKST",
	"add.069" to "i jeszcze wierszy: ",
	"add.070" to "podziały wierszy zginęły po drodze i tego wiersza nie udało się rozłożyć. Naciśnij „wklej ze schowka”: klawiatura ucina duże wklejenia",
	"add.071" to "Tekst był dłuższy niż limit i został ucięty. Podziel talię na dwie części.",
	"add.072" to "Jeśli nie ma gotowej talii",
	"add.073" to "Droga opcjonalna: poproś model o talię. To, co wróci, trzeba przejrzeć okiem.",
	"add.074" to "SPOSÓB Z MODELEM",
	// тап в любом месте
	"card.002" to "dotknij gdziekolwiek",
	// ← НЕ ЗНАЮ
	"card.003" to "← NIE ZNAM",
	// ЗНАЮ →
	"card.004" to "ZNAM →",
	// секунду…
	"sess.001" to "chwilka…",
	// знакомство
	// МИНИМУМ СДЕЛАН
	"sess.003" to "MINIMUM ZROBIONE",
	// Колода пройдена
	"sess.004" to "Talia przerobiona",
	// На сегодня всё
	"sess.005" to "Na dziś koniec",
	// Сегодня карточек нет
	"sess.006" to "Dziś nie ma kart",
	// Повторять больше нечего — остальное ещё не подошло по сроку
	"sess.007" to "Nie ma już co powtarzać — reszcie nie minął termin",
	// ПРОВЕРИТЬ ЕЩЁ РАЗ
	"sess.008" to "SPRAWDŹ JESZCZE RAZ",
	// ЕЩЁ НЕМНОГО  +5
	"sess.009" to "JESZCZE TROCHĘ  +5",
	// только повторения, новые чанки от этого не добавятся
	"sess.010" to "tylko powtórki, nowe chunki od tego nie dojdą",
	// этот ответ отменить уже нельзя
	"sess.011" to "tej odpowiedzi już nie cofniesz",
	// ответ записан
	"sess.012" to "odpowiedź zapisana",
	// ОТМЕНИТЬ
	"sess.013" to "COFNIJ",
	// ОК
	"sess.014" to "OK",
	// узнать
	"sess.015" to "rozpoznać",
	// вставить
	"sess.016" to "dopisać",
	// сказать
	"sess.017" to "powiedzieć na głos",
	// План дня закрыт. Новые чанки придут сами завтра.
	"sess.018" to "Plan dnia zamknięty. Nowe chunki przyjdą same jutro.",
	// Первый день — берём совсем немного.
	"sess.019" to "Pierwszy dzień — bierzemy całkiem mało.",
	// Всё повторено, срок следующих ещё не наступил.
	"sess.020" to "Wszystko powtórzone, termin następnych jeszcze nie nadszedł.",
	// Повторений впереди и так много — новые слова подождут.
	"sess.021" to "Powtórek przed tobą i tak dużo — nowe słowa poczekają.",
	// Сначала разбираем накопившееся, потом новое.
	"sess.022" to "Dziś tylko najważniejsze powtórki; nowy materiał wróci, gdy będzie na niego miejsce.",
	// После перерыва сначала разогрев на старом.
	"sess.023" to "Witaj z powrotem. Zaczniemy od krótkiego, znajomego kroku.",
	// Неделя вышла тихая — новое подождёт, сроки уже сдвинуты, долгов нет.
	"sess.024" to "Spokojny tydzień — nowe może poczekać, terminy już się dostosowały.",
	// Для новых чанков поздно — познакомимся утром, повторения на месте.
	"sess.025" to "Na nowe chunki za późno — poznamy je rano, powtórki zostają.",
	// Пока старое не закрепится — без новых.
	"sess.026" to "Dopóki stare się nie utrwali — bez nowych.",
	// Режим возвращения: несколько коротких дней, без долгов.
	"sess.027" to "Witaj z powrotem. Plan już dopasował się do twojego rytmu. Dziś jeden krótki, skończony krok. Kontynuuj od miejsca, w którym jesteś.",
	// Добавлю хотя бы один новый чанк, чтобы не стоять на месте.
	"sess.028" to "Dodam choć jeden nowy chunk, żeby nie stać w miejscu.",
	// Новых чанков здесь больше нет — все уже знакомы. Повторения продолжат приходить по срокам,
	"sess.029" to "Nowych chunków tu już nie ma — wszystkie są znajome. Powtórki będą przychodzić w swoich terminach, a po nowy materiał potrzebna jest kolejna talia.",
	// следующие — сегодня в 
	"sess.030" to "następne — dziś o ",
	// следующие — завтра в 
	"sess.031" to "następne — jutro o ",
	// следующие — через 
	"sess.032" to "następne — za ",
	// дней
	"sess.033" to "dni",
	// день
	"sess.034" to "dzień",
	// дня
	"sess.035" to "dni",
	// дней
	"sess.036" to "dni",
	//  · <1 МИН
	"sess.037" to " · <1 MIN",
	//  МИН
	"sess.038" to " MIN",
	// карточек
	"sess.039" to "kart",
	// карточка
	"sess.040" to "karta",
	// карточки
	"sess.041" to "karty",
	// карточек
	"sess.042" to "kart",
	"sess.043" to "Wczoraj wyszło dużo — dziś dzień lżejszy, bez nowego.",
	"sess.044" to "OZNACZ JAKO BŁĘDNĄ",
	"sess.045" to "Karta wyjęta z obiegu. Błąd nie trafił do statystyk.",
	"sess.046" to "termin",
	"sess.047" to "z pamięci",
	// Фразами, а не словами
	"onb.001" to "Zwrotami, nie słowami",
	// Внутри — готовые чанки: короткие живые куски речи. Новые добавляются сами — ничего не надо
	"onb.002" to "W środku są gotowe chunki: krótkie, żywe kawałki mowy. Nowe dochodzą same — niczego nie trzeba wpisywać ręcznie.",
	// Пропуск — не провал
	"onb.003" to "Przerwa to nie porażka",
	// Если день или неделя пропали, завала на входе не будет. Старое уйдёт в тихий пул и будет в
	"onb.004" to "Opuszczony tydzień nie zostawi zatoru: stare wraca po trochu.",
	// Минимум — одна карточка
	"onb.005" to "Minimum to jedna karta",
	// Одна карточка закрывает день целиком. Захочется больше — есть кнопка «ещё немного», и она 
	"onb.006" to "Jedna karta zamyka cały dzień. Zechcesz więcej — jest przycisk «jeszcze trochę», i nie obciąży jutra.",
	// ГОТОВЛЮ КАРТОЧКИ…
	"onb.007" to "PRZYGOTOWUJĘ KARTY…",
	// ДАЛЬШЕ
	"onb.008" to "DALEJ",
	// НАЧАТЬ
	"onb.009" to "ZACZNIJ",
	// ПРОПУСТИТЬ
	"onb.010" to "POMIŃ",
	// Влево и вправо
	"onb.011" to "W lewo i w prawo",
	// Карточку смахивают: влево — не знаю, вправо — знаю. Можно вместо этого нажать на слово внизу. Ответ отменяется сразу после него, так что ошибиться не страшно.
	"onb.012" to "W lewo — nie znam, w prawo — znam. Odpowiedź można cofnąć od razu po niej.",
	// ОТЛИЧНОЕ
	"speaker.001" to "ŚWIETNA",
	// ХОРОШЕЕ
	"speaker.002" to "DOBRA",
	// ОБЫЧНОЕ
	"speaker.003" to "ZWYKŁA",
	// НИЗКОЕ
	"speaker.004" to "NISKA",
	// Одна карточка
	"remind.001" to "Jedna karta",
	// Одной достаточно, чтобы день был закрыт
	"remind.002" to "Jedna wystarczy, żeby dzień był zamknięty",
	// Назад
	"a11y.001" to "Wstecz",
	// Настройки
	"a11y.002" to "Ustawienia",
	// Статистика
	"a11y.003" to "Statystyki",
	// Добавить колоду
	"a11y.004" to "Dodaj talię",
	// Прочитать вслух
	"a11y.005" to "Przeczytaj na głos",
	// Колода в работе
	"a11y.006" to "Talia w użyciu",
	"a11y.007" to "Wyjaśnij liczbę",
	// Не знаю
	"a11y.008" to "Nie znam",
	// Знаю
	"a11y.009" to "Znam",
	"a11y.010" to "Ustawienia talii",
	// Озвучка
	"voice.001" to "Czytanie na głos",
	// Сейчас читает
	"voice.002" to "Teraz czyta",
	// Системный голос телефона
	"voice.003" to "Systemowy głos telefonu",
	// В этой сборке нет движка моделей — читает только системный голос
	"voice.004" to "Ta wersja nie ma silnika modeli — czyta tylko głos systemowy",
	// Модель добавлена, но не загрузилась. Читает системный голос
	"voice.005" to "Model jest, ale się nie wczytał. Czyta głos systemowy",
	// Модели нет. Читает системный голос телефона
	"voice.006" to "Nie ma modelu. Czyta systemowy głos telefonu",
	// Проверяю модель…
	"voice.007" to "Sprawdzam model…",
	// Модель загружена и готова
	"voice.008" to "Model wczytany i gotowy",
	// Проверить голос
	"voice.010" to "Sprawdź głos",
	// Своя модель
	"voice.011" to "Twoje modele",
	// Модель приносите вы: папка Kokoro или Piper с телефона. Ничего не скачивается — у приложения нет доступа в интернет.
	"voice.012" to "Model wybierasz sam: folder Kokoro albo Piper z telefonu. Nic się nie pobiera — aplikacja nie ma dostępu do internetu.",
	// Убрать модель
	"voice.013" to "Usuń model",
	// Язык модели
	"voice.014" to "Język modelu",
	// Любой
	"voice.015" to "Dowolny",
	// Это сборка без движка. Своя модель здесь не заработает — нужна сборка с пометкой voice.
	"voice.016" to "To wersja bez silnika. Własny model tu nie zadziała — potrzebna wersja z oznaczeniem voice.",
	// Заменить модель
	"voice.017" to "Dodaj kolejny model",
	// Копирую файлы: 
	"voice.018" to "Skopiowane pliki: ",
	// Это не похоже на модель: в папке нет файла .onnx
	"voice.019" to "To nie wygląda na model: w folderze nie ma pliku .onnx",
	// Выбрана папка уровнем выше. Откройте её и выберите папку с моделью
	"voice.020" to "Wybrany folder jest o poziom wyżej. Otwórz go i wskaż folder z modelem",
	// В папке несколько моделей. Оставьте одну
	"voice.021" to "W folderze jest kilka modeli. Zostaw jeden",
	// Нет файла tokens.txt. Нужна сборка модели для sherpa-onnx, а не файлы Piper как есть
	"voice.022" to "Brak pliku tokens.txt. Potrzebna wersja modelu dla sherpa-onnx, a nie surowe pliki Piper",
	// Не удалось скопировать: 
	"voice.023" to "Nie udało się skopiować: ",
	// Модель добавлена
	"voice.024" to "Model dodany",
	// Озвучка выключена в настройках
	"voice.025" to "Czytanie na głos jest wyłączone - włącz przełącznik powyżej",
	// Модели лежат на странице релизов sherpa-onnx, раздел tts-models. Kokoro — английский, Piper — почти любой язык, включая русский и польский.
	"voice.026" to "Modele są na stronie wydań sherpa-onnx, w sekcji tts-models. Kokoro — angielski, Piper — prawie każdy język, w tym polski i rosyjski.",
	// Нет файла voices.bin. Скачивание Kokoro оборвалось
	"voice.027" to "Brak pliku voices.bin. Pobieranie Kokoro się nie dokończyło",
	// Нет данных для произношения: ни espeak-ng-data, ни lexicon
	"voice.028" to "Brak danych do wymowy: ani espeak-ng-data, ani lexicon",
	// Добавить модель
	"voice.029" to "Dodaj model",
	"voice.030" to "Czytaj karty na głos",
	"voice.031" to "Gdy to jest wyłączone, karty milczą — nawet z modelem",
	"voice.032" to "Kto przeczyta które talie",
	"voice.033" to "czyta model",
	"voice.034" to "czyta głos telefonu",
	"voice.035" to "nikt: model nie zna tego języka, a telefon nie ma dla niego głosu",
	"voice.036" to "Model mówi jednym językiem. Język talii bierze się z fiszek i można go zmienić na jej stronie.",
	"voice.037" to "BEZ JĘZYKA",
	"voice.038" to "wyłączony",
	"voice.039" to "Numer głosu",
	"voice.040" to "Każdy język czyta jeden model",
	"voice.041" to "Dodaj archiwum .tar.bz2",
	"voice.042" to "To nie jest archiwum z modelem",
	"voice.043" to "Za mało miejsca: rozpakowany model jest około trzy razy większy",
	"voice.044" to "Aplikacja sama rozpakuje archiwum",
	"voice.045" to "Język rozpoznany z nazwy modelu:",
	"voice.046" to "INNY JĘZYK",
	"voice.047" to "Ile głosów ma model, wie tylko sam model: naciśnij próbę głosu, a liczba pojawi się tutaj.",
	"voice.048" to "Prędkość",
	"voice.049" to "Rozpakowuję:",
	"voice.050" to "Duży model rozpakowuje się kilka minut: bzip2 rozkłada procesor telefonu.",
	"voice.051" to "Możesz opuścić ten ekran — instalacja trwa dalej. Nie zwijaj aplikacji na długo.",
	// Оформление
	"look.001" to "Wygląd",
	// Буквы и цвет квадрата в списке колод. Видно только вам: тому, кому отправите колоду, придут только карточки.
	"look.002" to "Litery i kolor kwadratu na liście talii. Widzisz tylko ty: ten, komu wyślesz talię, dostanie same karty.",
	// Цвет квадрата
	"look.003" to "Kolor kwadratu",
	// 1-2 символа. Оставьте пустым - буквы подберутся сами.
	"look.004" to "Jeden lub dwa znaki. Puste - kwadrat wybierze sam.",
	// Логотип ikna
	"bar.001" to "Znak ikna",
	// Слева в нижнем баре. Выключите — место уйдёт кнопкам
	// Под левую руку
	"bar.003" to "Pod lewą rękę",
	// Нижний бар зеркалится: плюс уходит в левый угол, остальные кнопки — вправо

	// The update window and the update section in settings.
	"upd.001" to "DOSTĘPNA AKTUALIZACJA",
	"upd.002" to "Rozmiar: ",
	"upd.003" to "MB",
	"upd.004" to "Co nowego:",
	"upd.005" to "AKTUALIZUJ",
	"upd.006" to "POMIŃ",
	"upd.007" to "Plik pobiera sama aplikacja — pasek i procenty widzisz tutaj. Instalator Androida nałoży nową wersję na starą, karty i postępy zostają.",
	"upd.008" to "Sprawdzaj aktualizacje",
	"upd.009" to "Jedno zapytanie do strony wydań, nie częściej niż raz na dobę. Nic nie jest wysyłane. Wyłączone — żadnego połączenia.",
	"upd.010" to "SPRAWDŹ TERAZ",
	"upd.011" to "Zainstalowana: ",
	"upd.012" to "Nic nowszego. Jeśli nie ma sieci, sprawdzenie się nie udało — aplikacja tego nie rozróżni.",
	"upd.013" to "STRONA WYDAŃ",
	"upd.014" to "SPRAWDZAM…",
	"upd.015" to "Odłożone: ",
	"upd.016" to "Aktualizacja do ",
	"upd.017" to "POBIERANIE",
	"upd.018" to "Plik jest pobrany. Instalator Androida nałoży nową wersję na starą — karty, postępy i ustawienia zostają.",
	"upd.019" to "ANULUJ",
	"upd.020" to "ZAINSTALUJ",
	"upd.021" to "Android poprosi o zgodę na instalowanie aplikacji z tego źródła; bez niej instalator się nie otworzy. Plik jest już pobrany i nie znika.",
	"upd.022" to "POZWÓL",
	"upd.023" to "Pobieranie się nie udało — połączenie zerwane albo plik przyszedł niecały. Można powtórzyć lub wziąć plik w przeglądarce.",
	"upd.024" to "POWTÓRZ",
	"upd.025" to "W PRZEGLĄDARCE",

	"cat.001" to "Katalog talii",
	"cat.002" to "Zdania pochodzą z Tatoeby, formy słów z Wikisłownika. Licencja i autorstwo są pokazane przed pobraniem.",
	"cat.003" to "WCZYTUJĘ LISTĘ…",
	"cat.004" to "Lista nie dotarła — nie ma sieci albo strona katalogu jest nieosiągalna. Nic się nie zepsuło: wbudowana talia i tworzenie talii z modelem są na miejscu.",
	"cat.005" to "STRONA KATALOGU",
	"cat.006" to "POWTÓRZ",
	"cat.007" to "Czego się uczyć",
	"cat.008" to "Znaczenia w",
	"cat.009" to "Temat",
	"cat.010" to "Poziom",
	"cat.011" to "WSZYSTKIE",
	"cat.012" to "Ta para jest pełna: w korpusie jest dużo zdań, talie są duże.",
	"cat.013" to "Ta para jest cienka: w korpusie jest mało zdań, więc talie wyjdą krótsze. To ograniczenie źródła, nie aplikacji.",
	"cat.014" to "Dla tej pary jeszcze nic nie ma. Pełność wszystkich par jest wypisana w README projektu.",
	"cat.015" to "kart",
	"cat.016" to "Licencja: ",
	"cat.017" to "Źródło: ",
	"cat.018" to "POBIERZ",
	"cat.019" to "POBIERANIE",
	"cat.020" to "Pobieranie się nie udało — połączenie zerwane albo plik przyszedł niecały. Nic nie zostało zainstalowane, można powtórzyć.",
	"cat.021" to "Plik dotarł, ale nie udało się złożyć ani jednej karty. Talia nie została dodana.",
	"cat.022" to "Gotowe. Kart w talii: ",
	"cat.023" to "SKŁADAM TALIĘ…",
	"cat.024" to "MB",
	"cat.025" to "CC BY-SA: talia zbudowana z tej i przekazana dalej idzie na tej samej licencji. Na naukę to nie ma żadnego wpływu.",
	"cat.026" to "Lista złożona: ",
	"cat.027" to "początek",
	"cat.028" to "środek",
	"cat.029" to "zaawansowany",
	"cat.030" to "Dla tych filtrów nic nie ma.",
	"cat.031" to "Gotowa talia",
	"cat.032" to "Jeśli nie chce się bawić w model: talia z katalogu, zdania napisane przez ludzi, licencja otwarta, autor wskazany.",
	"cat.033" to "OTWÓRZ KATALOG",
	"cat.034" to "JUŻ POBRANE",
	"cat.035" to "pobierz ponownie",
	"cat.036" to "POKAŻ PRZYKŁADY",
	"cat.037" to "WCZYTUJĘ PRZYKŁADY…",
	"cat.038" to "PONÓW PRZYKŁADY",
	"cat.039" to "PRZYKŁADY Z TALII",
	"cat.040" to "KARTA ",
	"cat.041" to "Z wymową",
	"a11y.011" to "Szukaj w taliach",
	"src.001" to "Źródło · ",
	"src.002" to "Ta karta jest błędna?",
	"src.003" to "Karta zostanie ukryta, a gotowe zgłoszenie z tekstem i linkiem do źródła skopiowane. Można je wkleić do GitHub Issues.",
	"src.004" to "UKRYJ I SKOPIUJ",
	"src.005" to "ANULUJ",
	"src.006" to "Karta ukryta · zgłoszenie skopiowane",
	"src.007" to "ŹRÓDŁO",
	"search.001" to "Szukaj w taliach",
	"search.002" to "Przeszukuje tylko talie już zainstalowane na tym telefonie. Sieć nie jest używana.",
	"search.003" to "Zwrot, zdanie lub tłumaczenie",
	"search.004" to "SZUKAJ",
	"search.005" to "SZUKAM…",
	"search.006" to "Wpisz co najmniej dwa znaki.",
	"search.007" to "W zainstalowanych taliach niczego nie znaleziono.",
	"search.008" to "Pokazano pierwszych 80 wyników — doprecyzuj zapytanie, aby skrócić listę.",
	"search.009" to "Wyszukiwanie się nie udało. Dane nie zostały zmienione; można ponowić.",
	"search.010" to "TALIA · ",
	"mig.001" to "Aktualizuję harmonogram",
	"mig.002" to "Talie i historia zostają na miejscu. Odpowiedzi są raz przeliczane przez FSRS-6.",
	"mig.003" to "Harmonogram nie został zaktualizowany",
	"mig.004" to "Nic nie usunięto: talie i historia są na miejscu. Przeliczenie można bezpiecznie powtórzyć.",
	"mig.005" to "POWTÓRZ",
	"diag.001" to "Diagnostyka",
	"diag.002" to "Tylko podsumowanie techniczne: bez nazw talii, tekstu fiszek i historii. Nic nie jest wysyłane.",
	"diag.003" to "POKAŻ DIAGNOSTYKĘ",
	"diag.004" to "UKRYJ DIAGNOSTYKĘ",
	"diag.005" to "ZBIERANIE…",
	"diag.006" to "KOPIUJ PODSUMOWANIE",
	"diag.007" to "Skopiowano diagnostykę",
	"diag.008" to "Nie udało się zebrać diagnostyki",

    "anki.001" to "Z Anki",
    "anki.002" to "Wybierz plik .apkg. Wszystko zostaje na tym telefonie.",
    "anki.003" to "Każdy plik .apkg z Anki się nada — stary i nowy.",
    "anki.005" to "WYBIERZ .APKG",
    "anki.006" to "ODCZYT PLIKU…",
    "anki.007" to "Możesz opuścić ten ekran — import trwa dalej.",
    "anki.008" to "Talie: ",
    "anki.009" to "Karty: ",
    "anki.010" to "Dodane wpisy historii: ",
    "anki.011" to "Pominiętych zapisów historii: ",
    "anki.012" to "Wstrzymane lub zakopane w Anki, nie dodane: ",
    "anki.013" to "Pominiętych fiszek: ",
    "anki.014" to "Z obrazkami lub dźwiękiem: ",
    "anki.015" to "Historia jest bardzo duża: wzięto 100 000 najnowszych ocen.",
    "anki.016" to "GOTOWE",
    "anki.017" to "IMPORTUJ KOLEJNY",
    "anki.018" to "Nie udało się odczytać tego .apkg.",
    "anki.019" to "Pakiet nie zawiera kolekcji Anki.",
    "anki.020" to "Nie udało się odczytać tej kolekcji. Dane bez zmian.",
    "anki.021" to "Plik jest uszkodzony albo jego format nie jest jeszcze czytany.",
    "anki.022" to "Pakiet przekracza bezpieczny limit 300 MB. Dane nie zmieniły się.",
    "anki.023" to "Pakiet nie ma czytelnych kart tekstowych. Dane nie zmieniły się.",
    "anki.024" to "Nic się nie zmieniło — popraw eksport i spróbuj ponownie.",
    "anki.025" to "Co się przeniosło",
    "anki.026" to "Przenieś .apkg w telefonie: talie i historię ocen bez ręcznego odtwarzania.",
    "anki.027" to "IMPORTUJ Z ANKI",
    "anki.028" to "Harmonogram powstaje tutaj od nowa — interwały Anki nie są przenoszone.",
    "anki.029" to "Źródłowy plik Anki pozostaje bez zmian.",
    "anki.030" to "Odtworzono z pól: ",
    "anki.031" to "W pliku jest tylko systemowa fiszka Anki z prośbą o aktualizację. Wyeksportuj go ponownie.",
    "anki.032" to "Języki: ",
    "anki.033" to "przedmiot",
    "anki.034" to "nieokreślony",
    "pc.001" to "Na komputerze jeszcze niedostępne",
    "pc.002" to "Spacja pokazuje, 1–4 ocenia, Z cofa",
    "pc.003" to "Sesja",
    "pc.004" to "Wybierz talię po lewej",
    "pc.005" to "Plik",
    "pc.006" to "Widok",
    "pc.007" to "Pomoc",
    "pc.008" to "Zakończ",
    "pc.009" to "Pełny ekran",
    "pc.010" to "Skróty klawiszowe",
    "pc.011" to "Folder danych",
    "pc.012" to "Ikna jest już uruchomiona",
    "pc.013" to "Ikna napotkała błąd. Szczegóły są w pliku dziennika:",
    "pc.014" to "Przeciągnij kartę: w lewo — nie wiem, w prawo — wiem",
    "pc.015" to "Talie",
    "pc.016" to "Dzisiaj",
    "bk.001" to "JEDEN PLIK",
    "bk.002" to "ZAPISZ DO PLIKU",
    "bk.003" to "PRZYWRÓĆ Z PLIKU",
    "bk.004" to "PLIK DLA TELEFONU",
    "bk.005" to "PRACUJĘ…",
    "bk.006" to "Zapisano: ",
    "bk.007" to "Odpowiedzi: ",
    "bk.008" to "Talie: ",
    "bk.009" to "Przyjęte odpowiedzi: ",
    "bk.010" to "Już były: ",
    "bk.011" to "Przeliczone karty: ",
    "bk.012" to "Ustawienia zastosowane",
    "bk.013" to "Nie udało się odczytać pliku",
    "bk.014" to "Nie ma czego zapisać",
    "bk.015" to "Odczytano pełny plik",
    "bk.016" to "Odpowiedzi, ustawienia i teksty talii — wszystko, czego nie da się odtworzyć.",
    "bk.017" to "Dlaczego nie ma konfliktów",
    "bk.018" to "Dziennik odpowiedzi jest tylko dopisywany, a każda odpowiedź jest rozpoznawana po karcie i czasie. Scalenie dwóch dzienników to suma zbiorów, więc jeden plik może krążyć między telefonem a komputerem dowolnie długo.",
    "bk.019" to "Te dwa pliki czyta przywracanie w aplikacji na telefonie.",
    "bk.020" to "Odczytano dziennik odpowiedzi",
    "bk.021" to "Odczytano ustawienia",
    "bk.022" to "Odczytano talię",
    "bk.023" to "Plik: ",
    "bk.024" to "Rozmiar, MB: ",
)
