package dev.ikna.ui.nav

import dev.ikna.ui.onboarding.FirstStudyDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstStudyRouteTest {
    @Test fun startOpensTheWholeDaySession() {
        assertEquals(Routes.session(null), Routes.firstStudy(FirstStudyDestination.SESSION))
    }

    @Test fun optionalChoiceOpensExistingCatalogue() {
        assertEquals(Routes.CATALOG, Routes.firstStudy(FirstStudyDestination.CATALOGUE))
    }
}
