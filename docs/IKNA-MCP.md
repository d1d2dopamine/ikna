# Ikna MCP import preparation

Install the official Python MCP SDK (the 1.x API) in a virtual environment.
Configure the MCP client to start that environment's Python with these args:

```text
tools/ikna_mcp.py --enable-deck-tools --output-dir <your local staging folder>
```

Enable this only for an assistant you intend to use for authoring cards. Prefer a
model that knows the subject or language well. Verify natural usage, meanings
and difficulty in the sample before approving the batch.

The server uses standard input and output. It presents a 3–5 card preview and
prepares at most 1,000 approved cards in Ikna's plain-text format. The result
shows the intended deck and local file path. Open the file from Add Deck or the
intended deck's append action in Ikna. The application remains the owner of the
learner database. Batch history, reversible whole-batch hiding and direct
application-side MCP imports are later stages.

See [the Ikna skill](../skills/ikna/SKILL.md) for the authoring workflow.
