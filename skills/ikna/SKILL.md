---
name: ikna
description: Prepare and verify language or subject flashcards for an Ikna deck, including a sample and confirmation before importing a batch.
---

# Prepare an Ikna deck

Establish the language or subject, learner's current level, approximate card
count, and preferred style. Ask only for missing details. Use ordinary context
examples by default, or follow a requested setting, story, or situation.

Show three to five sample cards using Ikna's plain-text format
`expression | example in context | meaning`. Wait for the learner to approve
the level and style before preparing the full batch.

For every proposed card:

- Verify that the expression appears in the example and has the intended meaning.
- Check that the sentence sounds natural and the translation fits that specific
  context, register, and level.
- Compare normalized expressions and meanings with the existing material
  available to you and the rest of the batch. Remove actual duplicates.
- Flag uncertain examples for review. Replace or omit them when you cannot
  verify them.

Use at most 1,000 cards in one import operation. Before writing, confirm the
exact destination deck and the number of cards. If the Ikna MCP server is
connected, use `preview_cards` for the sample and `prepare_import` after the
learner confirms it. Give the learner the returned path and target deck so
they can open the file in Ikna. Report the prepared count separately from
the number Ikna actually imports. Without the server, provide a plain-text
pack for the same deck import screen.
