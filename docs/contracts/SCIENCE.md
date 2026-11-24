# Science

ikna uses research to constrain learning behaviour. Research does not choose the
colour of a button and it does not turn every plausible idea into a feature. It
matters when the application makes a claim about memory, practice, transfer,
feedback or workload.

The rule for learning code is simple: **separate established evidence, reasonable
inference, an ikna hypothesis and product policy.** They are not interchangeable.

## Evidence labels

| Label | Meaning | What ikna may do with it |
| --- | --- | --- |
| **Established** | Replicated across many studies or supported by a strong synthesis, with a reasonably direct match to the behaviour in ikna. | Use as a default learning principle, while keeping implementation assumptions visible. |
| **Supported** | Good evidence exists, but the match to ikna's exact task, population or implementation is incomplete. | Build conservatively and keep the claim narrow. |
| **Experimental** | The mechanism is plausible or partially supported, but ikna's exact policy has not been validated. | Keep it versioned, observable and easy to remove or change. |
| **Product policy** | A deliberate product constraint rather than a scientific conclusion. | Keep it if it serves the product, but never describe it as research-proven. |

A paper does not automatically promote an implementation to **Established**. The
implementation has to resemble what the evidence actually tested.

## Current learning decisions

| Mechanism | Status | What the evidence supports | What it does not establish |
| --- | --- | --- | --- |
| Retrieval practice | **Established** | Actively retrieving information generally improves later retention more than passive restudy. | That any particular card format, gesture or grading rule is optimal. |
| Distributed practice | **Established** | Spreading practice over time generally improves long-term retention compared with massed practice. | The exact interval produced by one scheduler for every learner and every item. |
| FSRS-6 scheduling | **Supported / implementation** | It is an explicit model for applying spaced retrieval to review history. | That the default or locally fitted parameters are universally optimal. ikna validates fitted parameters before activation. |
| Corrective feedback after retrieval | **Supported** | Feedback can improve learning, and its effect depends strongly on the information it carries. | That praise, celebration, points or motivational messages improve learning. ikna does not infer this from the feedback literature. |
| Contextual diversity | **Supported, candidate for 0.11** | Experiencing lexical material across varied contexts is associated with better word learning; a 2026 meta-analysis found a positive behavioural effect across the included studies. | That ikna should rotate context on every review, that more variation is always better, or that the same effect size applies to chunks and every L2 learner. |
| Transfer through retrieval | **Supported, candidate for 0.11** | Retrieval practice can produce transfer to new tasks and contexts, with effects that depend on the kind of transfer and retrieval. | That a novel-context success should immediately receive a different FSRS grade or interval. |
| Automatic GOOD -> HARD/EASY refinement | **Experimental** | Response behaviour can contain useful information. | That ikna's current timing policy is a validated psychological scale. It is bounded and versioned for this reason. |
| Component memory | **Experimental** | Knowledge of parts can reasonably inform an initial prior for a new item. | That the current lemma/POS weighting is a validated model of lexical memory. |
| Load governor | **Experimental product policy** | Workload, backlog and recent performance are relevant signals for study planning. | That ikna's exact governor equation is an experimentally established optimum. |
| 20% amnesty | **Product policy** | It limits same-day failure pressure while preserving review history. | A claim that 20% is a scientifically optimal value. |
| Day boundary at 04:00 | **Product policy** | It keeps late-night work attached to the preceding evening in the product model. | A neuroscience claim about circadian memory. |
| Browse | **Product policy with a learning-safety constraint** | Reading can be useful, but an exposure is not equivalent to a successful retrieval. | That Browse itself should advance FSRS. It deliberately does not. |

## 0.11 research questions

The first 0.11 work should answer questions before it adds behaviour.

### What is the unit being learned?

A catalogue card currently couples a chunk to one sentence. For contextual
variation, ikna has to distinguish at least three things:

- a **learning target**: the phrase or construction the learner is expected to know;
- an **occurrence**: one real occurrence of that target in source material;
- a **context**: the sentence and translation in which that occurrence is shown.

These are not automatically interchangeable. Two similar strings are not one target
just because a normalizer can make them look alike.

### When is a new context informative?

A new context is not automatically a better review. Early learning may benefit from
stability; later learning may benefit from testing whether knowledge survives a
change of context. 0.11 must not invent a percentage or maturity threshold simply
because it feels reasonable. The policy remains conservative until its assumptions
are documented and testable.

### What can a novel-context answer tell us?

A successful answer in a context the learner has never seen may be evidence of
transfer. 0.11 may record that distinction. It must not silently reinterpret it as a
stronger FSRS grade until there is a validated rule for doing so.

### Does the learner need to be told?

No assumption is made that a message such as "you understood a new sentence" is
helpful. If such feedback is ever considered, it is a separate research question.
The default is normal study behaviour with no gamified announcement.

## ADHD

ikna may be designed to reduce avoidable friction for people with ADHD, but it must
not justify product behaviour with simplified dopamine stories. ADHD research does
not support treating every learner as if they share one fixed "low dopamine" state,
and a neural finding does not directly specify an interface or learning policy.

For ikna, ADHD-relevant design claims should be phrased at the behavioural level:
attention, delay, reward processing, task initiation, working-memory demands,
interruptions and unnecessary choice. A neuroscience result may inform a hypothesis;
it does not skip the behavioural evidence.

## Things ikna will not call science

- learning styles;
- left-brain/right-brain teaching;
- arbitrary "dopamine" features;
- a fixed number presented as optimal without evidence for that number;
- a feature justified only by saying it feels engaging;
- a correlation presented as proof of a causal learning mechanism.

## Research references

These are starting points, not a closed bibliography.

1. Dunlosky J, Rawson KA, Marsh EJ, Nathan MJ, Willingham DT. *Improving Students' Learning With Effective Learning Techniques: Promising Directions From Cognitive and Educational Psychology.* Psychological Science in the Public Interest. 2013;14(1):4-58. https://doi.org/10.1177/1529100612453266
2. Donoghue GM, Hattie JAC. *A Meta-Analysis of Ten Learning Techniques.* Frontiers in Education. 2021;6:581216. https://doi.org/10.3389/feduc.2021.581216
3. Pan SC, Rickard TC. *Transfer of test-enhanced learning: Meta-analytic review and synthesis.* Psychological Bulletin. 2018;144(7):710-756. https://doi.org/10.1037/bul0000151
4. Wisniewski B, Zierer K, Hattie J. *The Power of Feedback Revisited: A Meta-Analysis of Educational Feedback Research.* Frontiers in Psychology. 2020;10:3087. https://doi.org/10.3389/fpsyg.2019.03087
5. Chen S, Miller RT, Ke S. *The effect of contextual diversity on L1 and L2 word learning: A systematic review and meta-analysis.* Applied Psycholinguistics. 2026;47:e26. https://doi.org/10.1017/S0142716426100691
6. Plichta MM, Scheres A. *Ventral-striatal responsiveness during reward anticipation in ADHD and its relation to trait impulsivity in the healthy population: a meta-analytic review of the fMRI literature.* Neuroscience & Biobehavioral Reviews. 2014;38:125-134. https://doi.org/10.1016/j.neubiorev.2013.07.012

## Rule for future learning changes

A pull request that changes learning behaviour should be able to answer four
questions:

1. What learner outcome is this supposed to improve?
2. What evidence supports the mechanism, and how directly does it match ikna?
3. Which part is still an ikna assumption?
4. How can the decision be replayed, inspected or reversed if the assumption is wrong?

If those questions have no useful answer yet, the feature is not ready to become a
learning rule.
