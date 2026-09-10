package dev.ikna.ui.session

import dev.ikna.domain.session.BrowseAvailability
import dev.ikna.domain.session.BrowseUnavailableReason
import dev.ikna.ui.text.S

/** Multiple unmet conditions are shown together, so finishing one reveals no surprise. */
fun browseUnavailableText(availability: BrowseAvailability): String {
    val reasons = availability.blockers.ifEmpty {
        listOf(BrowseUnavailableReason.CHECK_FAILED)
    }
    return reasons.distinct().joinToString(separator = "\n") { reason ->
        when (reason) {
            BrowseUnavailableReason.PLAN_NOT_COMPLETE -> S.t("browse.008")
            BrowseUnavailableReason.NO_CREDITS -> S.t("browse.009")
            BrowseUnavailableReason.NO_CANDIDATES -> S.t("browse.010")
            BrowseUnavailableReason.LIMIT_REACHED -> S.t("browse.011")
            BrowseUnavailableReason.LATE_NIGHT -> S.t("browse.012")
            BrowseUnavailableReason.BACKLOG_GUARD -> S.t("browse.013")
            BrowseUnavailableReason.POST_SKIP_GUARD -> S.t("browse.014")
            BrowseUnavailableReason.LOW_ACTIVITY_GUARD -> S.t("browse.015")
            BrowseUnavailableReason.LOW_ACCURACY_GUARD -> S.t("browse.016")
            BrowseUnavailableReason.RETURN_GUARD -> S.t("browse.017")
            BrowseUnavailableReason.OVERHEATED_GUARD -> S.t("browse.018")
            BrowseUnavailableReason.SAFETY_GUARD -> S.t("browse.019")
            BrowseUnavailableReason.CHECKING -> S.t("browse.020")
            BrowseUnavailableReason.CHECK_FAILED -> S.t("browse.021")
        }
    }
}
