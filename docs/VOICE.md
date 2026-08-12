# Voice

How ikna reads a card out loud, why the model is not in the APK, and what to do
when nothing is speaking.

---

## Two builds

There are two APKs in every release, from the same source:

| Build | Size | What speaks |
| --- | --- | --- |
| `lite` (`ikna-<tag>.apk`) | ~21 MB | whatever text-to-speech the phone already has |
| `voice` (`ikna-<tag>-voice.apk`) | ~31 MB | the same, plus a neural engine for a model you add |

Neither one contains a model. The `voice` build carries the sherpa-onnx runtime,
which is about ten megabytes of native code and no weights at all.

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

## When it still does not speak

The voice screen answers this without guessing. The line at the top always names
who is speaking right now, and **Test the voice** proves it out loud.

- **"Reading aloud is switched off in settings"** -- speech is off by default;
  turn it on one screen up.
- **"No model. The phone's own voice reads"** -- nothing was added yet. If the
  phone has no voice for that language, install one from the system's own speech
  settings, or add a model here.
- **"A model is installed but did not load"** -- the folder is a model but this
  phone would not run it: usually memory. Take an `int8` folder, or a `low`
  quality Piper voice instead of `medium`.
- **"This build carries no model engine"** -- this is the `lite` APK. Take the
  `-voice` one.

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
| `app/src/voice/java/dev/ikna/audio/SherpaSpeech.kt` | the engine itself, `voice` build only |
| `app/src/lite/java/dev/ikna/audio/NeuralSpeechFactory.kt` | returns null: the plain build has no engine |
| `app/src/main/java/dev/ikna/ui/settings/VoiceScreen.kt` | the screen that says who is speaking |
| `tools/voice/fetch-voice.sh` | downloads the runtime `.aar` before a `voice` build |

The model is copied to `files/voice-model/` inside the app, with a small
`ikna-model.txt` beside it recording what it is. Removing the model, or
uninstalling the app, removes all of it.

Rendered audio is cached in `cache/speech/` and capped at 240 files. Changing the
model, its language or its speaker clears that cache, so a card is never read
back in yesterday's voice.

---

## Building the voice APK

```bash
bash tools/voice/fetch-voice.sh
gradle assembleVoiceRelease
```

The script downloads one `.aar` into `app/libs/` and nothing else. The `lite`
build ignores all of it and builds on a checkout that has never run the script,
which is why CI builds `lite` and attaches `voice` separately.
