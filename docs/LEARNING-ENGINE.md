# Learning engine

The learning engine is the part of ikna that decides what practice happens. UI may
collect an answer and render the result; it must not quietly become a second
scheduler.

## Responsibilities

```text
content / learner history
          |
          v
    Target Policy       what knowledge is eligible to be tested?
          |
          v
    Scheduler           when is that memory due?
          |
          v
    Governor            how much work fits today?
          |
          v
    Context Policy      which valid context should represent the target?
          |
          v
    Transfer Policy     is an unseen context appropriate yet?
          |
          v
    Presentation        classic full context -> tap for meaning
          |
          v
       learner
          |
          v
       Grading          what does the response mean?
          |
          v
    append-only review history
```

This diagram describes ownership, not a requirement that every box become a class.
A boundary is useful only when it prevents two parts of the system from making the
same decision.

## Scheduler

The scheduler answers **when** an item should be tested. FSRS-6 currently owns this
job. It does not decide how many new items the learner receives or which sentence
should be used to represent a target.

## Governor

The governor answers **how much** work is allowed into the day. It may cap new
material, react to backlog and preserve capacity. It should not select a novel
context merely because contextual diversity is desirable; that would make workload
policy responsible for content semantics.

## Target Policy

Target Policy answers **what knowledge** may enter or remain in the session. Today
this is largely implicit in chunk/card selection. 0.11 makes the concept explicit so
a card does not have to remain the permanent identity of the knowledge it displays.

A target may eventually have several source occurrences. The policy still has to be
conservative: two related phrases do not become one target unless the content
pipeline can justify the relationship.

## Context Policy

Context Policy answers **which valid context** can display a target for a particular
review. It is not a random sentence rotator.

Its constraints include:

- use only content whose provenance and licence are known;
- keep the full classic context visible and reveal the meaning through the existing tap action;
- do not select a context that changes the target's intended meaning;
- do not assume that maximal variety is always beneficial;
- preserve a stable fallback to the original context;
- keep selection deterministic when replay requires determinism.

## Transfer Policy

Transfer Policy answers a narrower question: **may this review use a context the
learner has never seen before?**

The policy is separate because "choose another context" and "test transfer" are not
the same operation. A learner may have seen several contexts already. A truly novel
context is a different observation and should be identifiable as such in history.

0.11 starts by representing the distinction. It does not assume a novel-context
success deserves a different FSRS rating.

## Active card presentation (0.12 owner decision)

Classic recognition is the sole active presentation: full context, tap for
translation/meaning, then the existing rating/side choice. Gesture handling,
first-contact repetition, optional pronunciation playback, grading and FSRS
updates for this presentation are unchanged. No voice-input exercise is added.
Cloze and reverse/production prompts and automatic level promotion are removed.

Persisted level 1/2 schedules and review rows remain readable for history,
export/restore, scheduler replay and historical undo. They are excluded from
active queues, due/backlog/forecast and active-card counts. An existing daily
plan drops only retired keys, preserving its capacity/new allowance/time and
required/extra split; no fresh daily budget is awarded by this conversion.
Deck progress still counts unique targets with actual historical answers.
The schema and append-only review contract are unchanged. Importing Anki cloze
source syntax still extracts content; this is not an active cloze exercise.

## Grading

Grading converts the learner's response into the scheduling observation used by the
rest of the engine. The learner-facing contract remains binary. Derived HARD/EASY
is an internal, versioned refinement and must remain bounded.

Context novelty may become another input to research and replay, but it must not
silently change the grade without an explicit, documented policy version.

## Feedback Policy

Feedback Policy exists to stop motivation experiments from leaking into unrelated UI
code. A correct answer does not need a congratulatory message. A successful novel
context does not automatically need to be announced.

If ikna ever tests a special form of feedback, the behaviour belongs here, with a
clear hypothesis and a way to disable or remove it.

## Invariants

1. **Review history stays append-only.** New observations are additive columns or
   additive rows; old answers are never rewritten to match a new theory.
2. **A policy change is versioned.** Replay must know which rule produced an
   observation when the distinction matters.
3. **Content and learner state are different data.** Replacing a corpus must not
   erase review history.
4. **The UI does not own learning policy.** Android and desktop must receive the same
   decision from shared code.
5. **No hidden remote learner model.** Learning decisions use local history and
   static content unless the privacy contract is deliberately changed in a future
   release.
6. **Unknown is a valid state.** If the content pipeline cannot safely connect two
   occurrences, they remain unrelated. Missing contextual diversity is preferable
   to a fabricated relationship.

## 0.11 implementation order

1. Define the content model and stable identities in `CATALOGUE-V2.md`.
2. Build the corpus-side grouping pipeline and fixtures before changing runtime
   scheduling.
3. Teach the app to read target/context metadata while preserving old packs.
4. Record enough review context to distinguish original, seen and novel contexts.
5. Add a conservative Context/Transfer Policy with deterministic tests.
6. Observe and validate behaviour before allowing context novelty to affect grading
   or FSRS intervals.

This order intentionally postpones the tempting part. The app should know what a
novel context *is* before it tries to be clever about when to show one.
