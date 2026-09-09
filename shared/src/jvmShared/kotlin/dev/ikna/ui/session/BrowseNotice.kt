package dev.ikna.ui.session

import dev.ikna.domain.session.BrowseUnavailableReason
import dev.ikna.ui.text.S

fun browseUnavailableText(reason: BrowseUnavailableReason): String = when (reason) {
    BrowseUnavailableReason.PLAN_NOT_COMPLETE -> S.t("browse.008")
    BrowseUnavailableReason.PLAN_TOO_SMALL -> S.t("browse.009")
    BrowseUnavailableReason.NO_CANDIDATES -> S.t("browse.010")
    BrowseUnavailableReason.LIMIT_REACHED -> S.t("browse.011")
    BrowseUnavailableReason.LATE_NIGHT -> S.t("browse.012")
    BrowseUnavailableReason.LOAD_GUARD -> S.t("browse.013")
}
