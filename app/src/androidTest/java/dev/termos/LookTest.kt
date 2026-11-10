package dev.termos

import org.junit.Test

/** AC-28 .. AC-31, see docs/ACCEPTANCE.md */
class LookTest : TermTest() {

    @Test fun ac28_lookShowsPanel() {
        login(); type("look")
        screenHas("autofocus"); screenHas("banner")
    }

    @Test fun ac29_bannerDrawsYourWords() {
        login(); type("look fill #"); type("banner HI")
        screenHas("###"); screenHas("banner set")
    }

    @Test fun ac30_badColourIsRejected() {
        login(); type("look color user nothex")
        screenHas("invalid colour")
    }

    @Test fun ac31_logoutShowsTimeLine() {
        login(); type("logout")
        screenHas("logged out:"); screenHas("terminal locked")
    }
}
