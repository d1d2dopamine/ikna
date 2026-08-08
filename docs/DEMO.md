# The demo video

The app records its own demo. Settings → РЕДКОЕ → *record a demo* produces a
silent MP4 in `Documents/ikna/`, named `ikna-demo-YYYY-MM-DD.mp4`.

## Why the app records it and not a person

Every interesting thing this app does is a movement: a card thrown to the right,
the counter that refuses to grow, the deck line filling. A screenshot shows none
of it, and a hand-held screen recording of a swipe shows a thumb and a stutter.

So the recording does not capture the screen at all. It runs the real interface
off-screen, with a virtual clock: the composition is told that 33 milliseconds
have passed, the resulting frame is copied out, and the encoder is handed it. The
phone can take as long as it likes over each frame — the output is still exactly
30 frames per second of animation running at its designed speed. A slow phone
produces the same video as a fast one, just later.

The consequence worth knowing: it is not a recording of *your* session. The decks
and the cards in it are synthetic, generated for the script, and nothing in the
video comes from your database. That is deliberate — a demo of a learning app
otherwise publishes what somebody is learning and how badly.

## What is in it

Roughly 45 seconds, 1080x1920, dark theme, no sound:

1. The deck screen, with a number for today.
2. A deck opening.
3. Four cards: one turned over, then thrown right, left, up.
4. The end of the day.
5. Statistics.
6. Appearance: theme and font changing.

No captions and no music. Both belong in an editor, where they can be redone
without re-rendering, and neither can be translated once burned into pixels.

## Putting it in the README

`README.md` links `docs/ikna.mp4`. Drop the finished, edited file there under
that name and the link works.

GitHub only plays a video inline if the file was uploaded through its own
interface, which rewrites it to a `user-images` address. To get a player instead
of a download link: open the pull request or release description, drag the MP4
into the text box, wait for the upload to finish, and copy the address it inserts.
A repository-relative path can never autoplay, no matter how it is written.

Keep the committed file small — a repository is not a video host, and every
version of it stays in the history forever.
