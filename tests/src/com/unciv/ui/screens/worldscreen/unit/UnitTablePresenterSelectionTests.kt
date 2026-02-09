package com.unciv.ui.screens.worldscreen.unit

import com.unciv.logic.map.HexCoord
import com.unciv.models.Spy
import org.junit.Assert.assertSame
import org.junit.Test

class UnitTablePresenterSelectionTests {

    private class FakePresenter(
        private val shown: Boolean
    ) : UnitTable.Presenter {
        override val position: HexCoord? = null
        override fun shouldBeShown(): Boolean = shown
    }

    @Test
    fun normalizePresenterFallsBackToSummaryWhenHidden() {
        val summary = FakePresenter(shown = true)
        val spy = FakePresenter(shown = false)

        val resolved = normalizePresenter(spy, summary)

        assertSame(summary, resolved)
    }

    @Test
    fun selectPresenterForSpyUsesSummaryForNullSpy() {
        val summary = FakePresenter(shown = true)
        val spyPresenter = FakePresenter(shown = true)

        val resolved = selectPresenterForSpy(null, spyPresenter, summary)

        assertSame(summary, resolved)
    }

    @Test
    fun selectPresenterForSpyUsesSpyPresenterWhenSpyExists() {
        val summary = FakePresenter(shown = true)
        val spyPresenter = FakePresenter(shown = true)

        val resolved = selectPresenterForSpy(Spy("Agent", 1), spyPresenter, summary)

        assertSame(spyPresenter, resolved)
    }
}
