# Voice

How the app speaks, why there are two downloads, and what the second one costs.

## The default: the phone's own engine

Every build talks to the speech engine installed on the phone through the
platform API. On a normal Android phone that engine already has English, Polish
and Russian and the user paid for it with the phone. On a de-googled phone the
user installs one from F-Droid -- RHVoice, or one built on Piper models -- picks
voices inside it, and this app speaks with them without knowing they exist.

This costs the APK nothing, works in every language the user has, and needs no
network. Voices whose own descriptor admits they need a network are filtered out
rather than merely discouraged, so nothing can leave the phone by accident.

It has one weakness: on a phone whose engine is poor, speech sounds poor, and
there is nothing the app can do about it from inside.

## The second download: `voice`

The `voice` build is the same app with a neural synthesiser and its model packed
inside. Same applicationId, same database, same review history -- one installs
over the other, and switching loses nothing.

- Model: **Kokoro-82M**, multi-language release, run by **sherpa-onnx**
  (ONNX Runtime).
- Languages: **English and Chinese**. The weights know more, but this is what
  the sherpa-onnx release documents and ships dictionaries for, so it is what
  the app claims.
- **No Russian and no Polish.** The model does not have them. A deck in a
  language the model cannot speak falls back to the platform engine, silently
  and per card, because a model reading Polish with an English mouth is worse
  than the voice the phone already offers.
- Still no network permission. The model ships in the APK; nothing is
  downloaded at runtime, ever.

### What it costs

| | |
|---|---|
| Runtime in the APK | ~7 MB per architecture, most of it `libonnxruntime.so` |
| Model in the APK | ~330 MB at full precision, ~90 MB for the int8 release |
| Unpacked on first use | the same again in the app's own storage |

The second row is the one people are surprised by. Kokoro is 82 million
parameters -- small as models go -- but a parameter is not a byte, and the file
is the size above. The third row is worse than it looks and is not avoidable:
espeak-ng turns letters into phonemes and opens its data by path through libc,
which cannot see inside an APK, so the model directory is copied out to storage
the first time speech is used and loaded from there.

That is the entire reason the plain build exists, and why it stays the default.

## Why this is not the browser demo

Kokoro in a browser tab on a phone is slow, and often garbled -- a squeak, a
mangled first word, a frozen page. Four reasons, none of which apply here.

| In the browser | Here |
|---|---|
| WebAssembly, one thread, no SIMD | native ARM code, two threads |
| q8 weights, the quantisation known to garble the start of a clip | the full-precision release |
| audio streamed to the speaker while it is still being computed | the whole phrase is written to a file, then played |
| synthesis competing with the page for the main thread | rendered on a background thread, never on the UI one |

The squeaking in particular is a streaming artefact: playback starts, the next
chunk does not arrive in time, and the buffer runs dry. Nothing here starts
playing until the file is complete, so there is no buffer to starve.

What does carry over is speed. Kokoro on a slow ARM CPU can take longer to
synthesise a phrase than the phrase lasts, and no amount of engineering makes an
82M model free. Two things hide it: the next card is rendered while the current
one is still on screen, and every rendering is cached, so a chunk met a second
time is instant. Automatic speech never waits -- if the file is not ready, the
card is simply silent rather than late.

And if the phone cannot keep up at all, the model stops. Three renders in a row
over four seconds and `KokoroSpeech` gives up for the rest of the run, releases
the model, and lets the platform engine take over. A plainer voice is a better
outcome than a session that stutters.

## Building the voice APK

```bash
bash tools/voice/fetch-voice.sh
gradle assembleVoiceRelease
```

The script downloads the runtime into `app/libs` and the model into
`app/src/voice/assets/kokoro`. Neither is committed: both are large, both belong
to other projects, and both are reproducible from two URLs. Both paths are in
`.gitignore`.

Versions are pinned at the top of the script and can be overridden:

```bash
SHERPA_VERSION=1.11.0 MODEL_NAME=kokoro-int8-multi-lang-v1_1 bash tools/voice/fetch-voice.sh
```

The release workflow runs the script and attaches the result as
`ikna-<tag>-voice.apk` next to the normal one. That step is allowed to fail: the
downloads come from servers this project does not control, and a release should
not wait on someone else's outage.

## Where it lives in the code

| Path | |
|---|---|
| `audio/Speaker.kt` | cache, playback, speed, prefetch, fallback. Shared. |
| `audio/NeuralSpeech.kt` | the seam. Five members. Present in every build. |
| `app/src/lite/.../NeuralSpeechFactory.kt` | returns `null`. |
| `app/src/voice/.../NeuralSpeechFactory.kt` | returns `KokoroSpeech`. |
| `app/src/voice/.../KokoroSpeech.kt` | unpacking, loading, synthesis. |

Both engines render into the same cache, keyed by language, voice, speed and
pitch, so a card met a second time never waits for either of them. A bundled
model that is missing, truncated or built against a different runtime version
makes every call return `false`, and the platform engine takes the card as if
none of this had been compiled in. The user hears a different voice, not an
error.

`speakers.txt` in the model directory maps a language to a numbered voice inside
`voices.bin`. It is data, not code: correcting a voice choice does not need a
Kotlin change.

## Replacing the model

Anything sherpa-onnx can run will do. A different model means: change the two
variables in the fetch script, change `MODEL_ID` in `KokoroSpeech` (which
invalidates unpacked copies and cached audio), and change `SUPPORTED` to the
languages it really has.

Per-language Piper models are the way to get Russian or Polish. They are around
60 MB each and one model speaks one language, which is why they are not bundled
here: three languages would be three models and the same argument as above,
three times over.
