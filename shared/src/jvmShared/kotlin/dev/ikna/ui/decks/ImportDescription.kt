package dev.ikna.ui.decks
import dev.ikna.data.repo.DeckImport
import dev.ikna.data.pack.SeedProblem
import dev.ikna.data.pack.SeedWarning
import dev.ikna.ui.text.S

fun describe(report: DeckImport): String {
	if (report.installed == 0) {
		val problem = report.firstProblem
			?: return S.t("add.025")
		return S.t("add.025") + "\n" + S.t("add.023") + problem.line +
			S.t("add.024") + reason(problem.problem)
	}
	val head = S.t("add.021") + report.installed
	val flagged = flagged(report)
	if (report.skipped == 0) return head + flagged
	val problem = report.firstProblem
		?: return head + S.t("add.022") + report.skipped + flagged
	return head + S.t("add.022") + report.skipped + "\n" +
		S.t("add.023") + problem.line + S.t("add.024") + reason(problem.problem) + flagged
}

/**
 * What landed and is still worth reading before it is learned.
 *
 * Not an error and not a refusal: the deck is installed. It is the only honest
 * thing an app with no network can say about a deck a model wrote in thirty
 * seconds -- these lines look like they were written to reach a number.
 */
private fun flagged(report: DeckImport): String {
	val warning = report.firstWarning ?: return ""
	return "\n" + S.t("add.050") + report.flagged + "\n" +
		S.t("add.023") + warning.line + S.t("add.024") + warningReason(warning.warning)
}

private fun warningReason(warning: SeedWarning): String = when (warning) {
	SeedWarning.DEFINITION_REPEATS_TERM -> S.t("add.051")
	SeedWarning.HEDGED -> S.t("add.052")
	SeedWarning.SAME_MEANING -> S.t("add.053")
	SeedWarning.HAS_NUMBERS -> S.t("add.054")
}

/**
 * A paste that lost its line breaks: one line carrying a whole deck's worth of
 * separators. A statement about the shape of the text, never about its content.
 */
fun glued(text: String): Boolean {
	if (text.isBlank()) return false
	if (text.lineSequence().count { it.isNotBlank() } > 1) return false
	return text.count { it == '|' } >= 6 || text.count { it == '\t' } >= 6
}

private fun reason(problem: SeedProblem): String = when (problem) {
	SeedProblem.NOT_THREE_COLUMNS -> S.t("add.031")
	SeedProblem.EMPTY_FIELD -> S.t("add.032")
	SeedProblem.PHRASE_NOT_IN_SENTENCE -> S.t("add.033")
	SeedProblem.TOO_LONG -> S.t("add.034")
	SeedProblem.DUPLICATE -> S.t("add.035")
	SeedProblem.ONE_LONG_LINE -> S.t("add.070")
}
