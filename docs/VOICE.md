# Voice

How ikna reads a card out loud, why the model is not in the APK, and what to do
when nothing is speaking.

---

## One build

One app per release, with the speech engine inside and **no model**. Two files,
differing in one thing only -- which processor they are built for:

| What | Size | Who it is for |
| --- | --- | --- |
| `ikna-<tag>.apk` | ~40 MB | every phone sold since roughly 2017 (arm64) |
| `ikna-<tag>-legacy32.apk` | ~35 MB | older 32-bit phones (armeabi-v7a) |

0.3.0 shipped one file with four architectures inside it: **114 MB**, of which
any given phone could run a quarter and carried the other three as dead weight.
The engine is native code, so there is no single small file that fits everything;
there is a file for the phones people have and a file for the ones they had. The
emulator architectures are not built at all.

There were two APKs until 0.3.0: a plain one and a `-voice` one. The split was
worth its cost while the second carried a 300 MB model. Once the model moved out
to the file picker, all it carried was ten megabytes of runtime -- and in exchange
the release page asked everybody to choose between two files whose names explain
nothing, while CI had to build both and one of them broke without anybody
noticing. Ten megabytes is the cheaper mistake.

The engine is inert until reading aloud is switched on, and there is no model in
it at all: about ten megabytes of native code and no weights.

---

## Why the model is not inside

The first version of this feature shipped Kokoro in the APK. It went badly, in a
way worth writing down.

Full precision made the download **466 MB** against 21. Quantised to int8 it came
down to about 150 MB, which is still seven times the app. And what all those
megabytes bought was **English**: Kokoro in sherpa-onnx speaks English and
Chinese, while every deck imported into this app is created by the person using
it, in whatever language they are learning.

Worse, imported decks are stored with no language of their own, so the bundled
model was never even offered for them. The engine fell back to the phone's voice
without saying so, and if the phone had no voice for that language the result was
silence. Nothing on screen ever named the engine that was speaking, so a 450 MB
install and a 21 MB install looked and behaved identically. That is the whole
failure: **an invisible feature that quietly did nothing.**

So the model now comes from the person using the app, in the language they are
actually learning, at the size their phone can carry -- and the app says at all
times which voice is speaking.

---

## Adding a model

**Settings -> Voice -> Add a model.**

The file picker opens on a folder, not a file. Point it at an unpacked model
folder; everything in it is copied into the app's own storage, which takes from a
few seconds to about a minute and shows a running count of files.

The app has **no internet permission**, so it cannot download a model and never
will. The download happens in a browser, once.

### Where to get one

The sherpa-onnx release page, section `tts-models`:
<https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models>

The full list with samples: <https://k2-fsa.github.io/sherpa/onnx/tts/all/>

| You are learning | Take |
| --- | --- |
| English | `kokoro-int8-en-v0_19` (~92 MB, 11 voices) |
| Russian | `vits-piper-ru_RU-dmitri-medium` or `-irina-medium` (~65 MB) |
| Polish | `vits-piper-pl_PL-gosia-medium` (~65 MB) |
| anything else | any `vits-piper-*` folder for that language |

Prefer a folder with `int8` in its name where there is one: about a quarter of
the size, and on a phone that is the difference between a model that loads and
one the system kills for memory.

### The download is a `.tar.bz2`

There is no zip. Every model on that page is a `.tar.bz2`, and Android opens
neither half of that -- not the bzip2 wrapper, not the archive inside it. Which is
where most attempts used to stop.

Since `0.5.0 proof` the app opens it itself. Download the file, then on the voice
screen press **Add a .tar.bz2 archive** and pick it. Unpacking a sixty-megabyte
model takes a minute or so, and the screen counts the files as they land.

The picker is opened for any file rather than for bzip2 specifically, on purpose:
phones disagree about what a `.tar.bz2` is called and several answer "nothing at
all", which is how a picker ends up greying out the very file it was opened to
choose. The app checks the contents, not the label.

An already-unpacked folder still works -- **Add a model** takes one, from a
computer or from ZArchiver -- and it is the faster route when the archive is
already open in front of you.

Do not unpack anything into the app's own folders and do not rename the files
inside: the app looks for `tokens.txt` and `espeak-ng-data` by name.

### What a usable folder looks like

Kokoro:

```
kokoro-int8-en-v0_19/
  model.int8.onnx
  voices.bin
  tokens.txt
  espeak-ng-data/
```

Piper / VITS:

```
vits-piper-ru_RU-dmitri-medium/
  ru_RU-dmitri-medium.onnx
  tokens.txt
  espeak-ng-data/
```

Unpack the archive and pick **the folder that holds these files**, not the folder
it was unpacked into. Picking one level too high is the commonest mistake there
is, and the app says so in those words when it happens.

---

## Several models

A model speaks one language. Somebody learning two needs two, and until `0.5.0
proof` there was a single slot: adding the second destroyed the first, and the
only way back was to copy sixty megabytes again.

Now every model is a row on the voice screen, with:

- a switch -- off keeps the files and stops the model being used, which is the
  honest answer to "this voice is worse than my phone's" without paying to find
  out again later;
- the language it reads, changeable, because multi-language releases name none;
- **Kokoro only:** which of its voices speaks, by number. That file holds around a
  hundred of them and they have no names, so a number and the test button are the
  whole interface there is to have;
- delete, for that model alone.

One model per language is on at a time. Switching one on switches off whichever
model held that language before, because two voices with an equal claim to a deck
is a coin toss the app would otherwise have to hide from you.

Only the model being used is held in memory -- one at a time, loaded when a card
in its language comes up. Ten installed models cost storage and nothing else.

---

## What the app refuses, and why

Every refusal is a sentence on screen rather than silence:

| On screen | What happened |
| --- | --- |
| no `.onnx` file in the folder | not a model folder at all |
| the folder is one level too high | pick the folder inside it |
| several models in one folder | two unrelated nets, and guessing between them would be wrong |
| no `tokens.txt` | a raw Piper download; take the sherpa-onnx build of the same voice |
| no `voices.bin` | a Kokoro folder whose download stopped early |
| no `espeak-ng-data` and no `lexicon` | nothing to turn letters into sounds |
| not an archive this app can open | the picked file is not a `.tar.bz2`, `.tbz2` or `.tar` |
| not enough free space | an unpacked model is about three times the archive, and this is checked before unpacking rather than during |

When several nets sit in one folder at different precisions -- `model.onnx`,
`model.int8.onnx`, `model.fp16.onnx` -- the quantised one is chosen without
asking. This is the one place the app guesses, and it guesses towards the phone
surviving it.

---

## Language

Most folder names say which language they are: `ru_RU`, `pl_PL`, `-en-`. The app
reads it from the name and shows it on the voice screen, where it can be changed.

A multi-language release names no language at all. Those are marked **Any** and
offered for every deck, because refusing every deck would be the one certainly
wrong answer.

---

## The voice number, and the speed

Kokoro holds many voices in one file and addresses them by index. There are no
names to list, so the screen offers a number -- and that number is the one thing
in the app that can end the process rather than fail. sherpa-onnx validates the
index down in C++: one past the last voice is not an error to catch, it is
`exit`, with no stack trace and nothing in a log afterwards.

So the count comes from the loaded net itself, is written into the manifest, and
is the only thing that bounds the buttons. Nothing unconfirmed reaches the
runtime: until a net has answered, voice 0 is offered, which is the one voice
every model has. A number left over from an older version, where the button had
no end, is pulled back into range the first time that model loads.

Speed belongs to a model, not to all of them: it lives in the manifest beside the
language, and each model is set on its own. A Piper voice recorded slowly and a
Kokoro voice that hurries do not agree on what 100% means, and one control for
both was a compromise that suited neither.

Pitch is not offered at all any more -- not for a model, not for the phone. A
neural voice has exactly one pitch, its own, and the runtime has no parameter for
it; the phone's engine accepted the number and, on most engines, did nothing with
it. A control that silently does nothing is worse than no control.

The phone's speed went the same way, for a plainer reason: it belongs to whoever
set that engine up, in the phone's own speech settings, for every app that uses
it. What is left in Settings is one switch -- whether the phone may read at all.
It exists for the languages no model of yours covers. Off, such a card is silent
and says so by not drawing the mark.

---

## When it still does not speak

The voice screen answers this without guessing. The line at the top always names
who is speaking right now, and **Test the voice** proves it out loud.

- **"Reading aloud is off - the switch above turns it on"** -- the switch is on
  the voice screen itself now. It used to live one screen up, in settings, and
  **Test the voice** does not go through it: the test could speak while every
  card stayed silent, which is exactly what happened before 0.4.0.
- **A deck in a language your model does not speak** -- a model speaks one
  language. **Who reads which deck** lists every deck language with who would
  read it: the model, the phone's voice, or nobody. A deck the model does not
  cover falls through to the phone, and if the phone has no voice for it either,
  the speaker mark is not drawn at all rather than drawn and dead.
- **"No model. The phone's own voice reads"** -- nothing was added yet. If the
  phone has no voice for that language, install one from the system's own speech
  settings, or add a model here.
- **The phone's voice is switched off** -- Settings has one switch for it. Off,
  a language no model of yours reads has no voice at all, on purpose: the mark is
  not drawn rather than drawn and dead.
- **"A model is installed but did not load"** -- the folder is a model but this
  phone would not run it: usually memory. Take an `int8` folder, or a `low`
  quality Piper voice instead of `medium`.
- **"This build carries no model engine"** -- from a build older than 0.3.0,
  when the engine was in a separate APK. Any current build has it.

A model that renders too slowly three times over is dropped on purpose, and the
phone's own voice takes over: twenty seconds a card is not a working feature, it
is a frozen app.

---

## Where it lives

| Path | What it is |
| --- | --- |
| `app/src/main/java/dev/ikna/audio/Speaker.kt` | everything speech; picks the engine per card |
| `app/src/main/java/dev/ikna/audio/NeuralSpeech.kt` | the interface a model engine implements |
| `app/src/main/java/dev/ikna/audio/VoiceModel.kt` | judging a picked folder, no Android involved |
| `app/src/main/java/dev/ikna/audio/VoiceModelStore.kt` | copying it in, and the one model on disk |
| `app/src/main/java/dev/ikna/audio/SherpaSpeech.kt` | the engine itself |
| `app/src/main/java/dev/ikna/audio/NeuralSpeechFactory.kt` | hands out the engine, or null until a model is added |
| `app/src/main/java/dev/ikna/ui/settings/VoiceScreen.kt` | the screen that says who is speaking |
| `tools/voice/fetch-voice.sh` | downloads the runtime `.aar`; run once per clone |

Each model is copied to its own folder under `files/voice-models/`, with a small
`ikna-model.txt` beside it recording what it is: its kind, its name, its
language, its voice number, how many voices it turned out to have, and its own
speed. Removing a model, or uninstalling the app, removes all of it.

A manifest written by an older version has no `speakers=` and no `rate=` line,
and is read anyway: an unknown number of voices means voice 0 only until a load
says otherwise, and an unknown speed means the model's own pace.

Rendered audio is cached in `cache/speech/` and capped at 240 files. The cache
key carries whoever would actually say the words: a model, its voice number and
its speed, or a constant standing in for the phone. Changing any of them means a
new rendering rather than yesterday's voice played back.

Nothing in that key may change while a card is on screen, and that is not a
detail. Until `0.2.0 press` the key also held the speed and the pitch this app
applied to the phone's engine, and those two numbers arrived from storage a
moment after a session opened: the card was rendered under one key and looked for
under another. The one card meant to read itself -- a phrase met for the first
time -- was the one that stayed silent until its mark was pressed by hand.

---

## Adding a large one

A Piper voice is a dozen files and sixty megabytes: it lands in a second or two,
and nothing about the copying is worth a word. A Kokoro release is a single
`model.onnx` of a few hundred megabytes with a handful of crumbs beside it, and
until `0.2.1 press` adding one was, in practice, impossible. Four separate
reasons, none of them the bytes:

- **Progress was counted in files.** One file is the whole archive, so the line
  read `1` from the first second to the last. Everybody who tried it concluded
  the app had hung, and everybody was being reasonable.
- **The copy belonged to the screen.** It ran in the voice screen's composition
  scope. A back press during the unpacking cancelled it, deleted the staging
  folder, and reported neither.
- **Eight kilobytes at a time.** The standard library's default buffer, against
  three hundred megabytes through a content provider, is forty thousand round
  trips in and as many out.
- **The screen was allowed to sleep.** A dark screen over a long silent job is an
  invitation for the system to take the process, and it took it.

What replaced them, in the same order: bytes of the picked file, reported from
inside the writing of a single file; a `VoiceInstaller` held by the container,
which the screen watches rather than owns; a megabyte of buffer in both
directions; and `keepScreenOn` for as long as a copy is running.

None of that makes bzip2 fast. Android has no native one, so a `.tar.bz2` is
decompressed in Java on the phone's own processor at a few megabytes a second,
and a large model is minutes of that. The screen now says the percentage while it
happens, says why it is slow, and says that leaving is allowed. Being killed is
still fatal: this is not a foreground service, and the app does not pretend
otherwise -- it says not to put it away for long, which is the truth.

Unpacking the archive on a computer and adding the folder instead is still the
faster route, and still supported. It is no longer the only one that finishes.

---

## Building it

```bash
bash tools/voice/fetch-voice.sh
gradle assembleRelease
```

The script downloads one `.aar` into `app/libs/` and nothing else. It is not
optional any more: the runtime is part of the one build, so a fresh clone that
has never run it does not compile. Both workflows run it themselves before
anything is built, which is also what makes a failed download impossible to
miss -- it used to fail quietly and cost a release its second APK.
