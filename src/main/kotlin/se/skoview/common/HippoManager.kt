/**
 * Copyright (C) 2013-2020 Lars Erik Röjerås
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package se.skoview.common

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pl.treksoft.kvision.core.Container
import pl.treksoft.kvision.html.main
import pl.treksoft.kvision.pace.Pace
import pl.treksoft.kvision.redux.createReduxStore
import pl.treksoft.navigo.Navigo
import se.skoview.hippo.hippoView
import se.skoview.stat.* // ktlint-disable no-wildcard-imports

object HippoManager { // } : CoroutineScope by CoroutineScope(Dispatchers.Default + SupervisorJob()) {

    private val routing = Navigo(null, true, "#")

    // todo: Move remaining dispatch to HippoManager and make the hippoStore private
    private val hippoStore = createReduxStore(
        ::hippoReducer,
        initializeHippoState()
    )

    fun initialize() {
        println("In Manager initialize()")
        Pace.init()
        routing.initialize().resolve()

        val startUrl = window.location.href
        println("window.location.href: $startUrl")

        val view = parseUrlForView(startUrl)

        val bookmark = parseBookmarkString(startUrl)
        hippoStore.dispatch(HippoAction.ApplyBookmark(view, bookmark))

        GlobalScope.launch {
            println("Will start to load dates")
            loadBaseItem("dates", Dates.serializer())
            println("--- Dates loaded")
            hippoStore.dispatch(HippoAction.SetDownloadBaseDatesStatus(AsyncActionStatus.COMPLETED))

            hippoStore.dispatch(HippoAction.StartDownloadBaseItems)
            loadBaseItems(hippoStore)
            hippoStore.dispatch(HippoAction.DoneDownloadBaseItems)
        }

        // Initialize the state change listener in the manager
        actUponStateChangeInitialize()

        println("""Exiting Manager initialize()""")
    }

    /**
     * This function is part of the manager. It listen to state changes, evaluates them and,
     * in some cases, act.
     * It ensures integrations and/or statistics load is initialized at program start when
     * necessary base data is available.
     */
    private fun actUponStateChangeInitialize() {
        hippoStore.subscribe { state ->
            println("--- In actUponStateChange() - subscribe")
            if (state.view == View.HIPPO) {
                // Load integrations if not loaded and dates are available
                if (
                    state.downloadIntegrationStatus == AsyncActionStatus.NOT_INITIALIZED &&
                    state.downloadBaseDatesStatus == AsyncActionStatus.COMPLETED
                ) {
                    println("Will now loadIntegrations()")
                    loadIntegrations(hippoStore.getState())
                }
            } else if (
                state.view == View.STAT_SIMPLE ||
                state.view == View.STAT_ADVANCED
            ) {
                // Load statistics data if not loaded and plattform are available
                if (
                    state.statDateEnd.isNotBlank() &&
                    state.statisticsDates.isNotEmpty() &&
                    state.statisticsDates.reversed()[0] < state.statDateEnd
                )
                    hippoStore.dispatch(
                        HippoAction.DateSelected(
                            DateType.STAT_END,
                            state.statisticsDates.reversed()[0]
                        )
                    )
                else if (
                    state.downloadStatisticsStatus == AsyncActionStatus.NOT_INITIALIZED &&
                    state.downloadBaseItemStatus == AsyncActionStatus.COMPLETED
                ) {
                    if (state.selectedPlattformChains.isEmpty()) {
                        val tpId: Int? = Plattform.nameToId("SLL-PROD")
                        if (tpId != null) {
                            hippoStore.dispatch(HippoAction.StatTpSelected(tpId))
                        }
                    }
                    println("Will now loadStatistics()")
                    loadStatistics(hippoStore.getState())
                }
            }
        }
    }

    fun Container.mainLoop() {
        // place for common header
        /*
        header(HippoManager.hippoStore) { state ->
            headerNav(state)
        }
         */
        main(HippoManager.hippoStore) { state ->
            // setUrlFilter(state)
            if (state.downloadBaseItemStatus == AsyncActionStatus.COMPLETED) {
                when (state.view) {
                    View.HOME -> println("View.HOME found in main")
                    View.HIPPO -> {
                        if (
                            state.downloadBaseItemStatus == AsyncActionStatus.COMPLETED &&
                            state.downloadIntegrationStatus == AsyncActionStatus.COMPLETED
                        ) {
                            hippoView(state)
                        }
                    }
                    View.STAT_SIMPLE -> {
                        // todo: Refactor and clean up code below
                        if (state.selectedPlattformChains.isNotEmpty()) {
                            val pcId = state.selectedPlattformChains[0]
                            val pc = PlattformChain.map[pcId]!!
                            val tp = Plattform.mapp[pc.last]!!
                            if (tp.name != "SLL-PROD") {
                                val tpId: Int? = Plattform.nameToId("SLL-PROD")
                                if (tpId != null) {
                                    hippoStore.dispatch(HippoAction.StatTpSelected(tpId))
                                }
                                println("Will now load statistics from main loop")
                                loadStatistics(hippoStore.getState())
                            }
                        }
                        statView(state, View.STAT_SIMPLE)
                    }
                    View.STAT_ADVANCED -> statView(state, View.STAT_ADVANCED)
                }
            }
        }
        // footer()
    }

    fun newOrUpdatedUrlFromBrowser(view: View, params: String? = null) {
        println("¤¤¤¤¤¤¤¤¤¤¤¤ In fromUrl(), view=$view, params=$params")
        val filterVals = if (params != null) params else ""
        val bookmark = parseBookmarkString(filterVals)
        println("bookmark from filter:")
        console.log(bookmark)
        hippoStore.dispatch(HippoAction.ApplyBookmark(view, bookmark))
        // hippoStore.dispatch(HippoAction.SetView(view))
        when (view) {
            // If HOME go to HIPPO
            View.HOME -> routing.navigate(View.HIPPO.url)
            View.HIPPO -> {
                if (hippoStore.getState().downloadBaseDatesStatus == AsyncActionStatus.COMPLETED)
                    loadIntegrations(hippoStore.getState())
            }
            else -> {
                if (hippoStore.getState().downloadBaseItemStatus == AsyncActionStatus.COMPLETED)
                    loadStatistics(hippoStore.getState())
            }
        }
    }

    fun dispatchProxy(action: HippoAction) {
        hippoStore.dispatch(action)
    }

    fun dateSelected(type: DateType, date: String) {
        val nextState = hippoStore.getState().dateSelected(date, type)
        navigateWithBookmark(nextState)
    }

    fun itemSelected(itemId: Int, type: ItemType) {
        val nextState = hippoStore.getState().itemIdSeclected(itemId, type)
        navigateWithBookmark(nextState)
    }

    fun itemDeselected(itemId: Int, type: ItemType) {
        val nextState = hippoStore.getState().itemIdDeseclected(itemId, type)
        navigateWithBookmark(nextState)
    }

    fun setViewMax(type: ItemType, lines: Int) {
        hippoStore.dispatch(HippoAction.SetVMax(type, lines))
    }

    fun statTpSelected(tpId: Int) {
        val nextState = hippoStore.getState().statTpSelected(tpId)
        navigateWithBookmark(nextState)
    }

    fun statHistorySelected(flag: Boolean) {
        if (flag) loadHistory(hippoStore.getState()) // Preload of history
        hippoStore.dispatch(HippoAction.ShowTimeGraph(flag))
    }

    fun statTechnialTermsSelected(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowTechnicalTerms(flag))
    }

    fun setView(view: View) {
        println("In setView, view=$view")
        val nextState = hippoStore.getState().setView(view)
        navigateWithBookmark(nextState)
    }

    fun statSetViewModePreselect(preSelectLabel: String) {
        // todo: Change to navigate call
        when (hippoStore.getState().view) {
            View.STAT_SIMPLE -> {
                val preSelect = SimpleViewPreSelect.mapp[preSelectLabel]
                    ?: throw NullPointerException("Internal error in Select View")
                hippoStore.dispatch(HippoAction.SetSimpleViewPreselect(preSelect))
            }
            View.STAT_ADVANCED -> {
                val preSelect = AdvancedViewPreSelect.mapp[preSelectLabel]
                    ?: throw NullPointerException("Internal error in Select View")
                hippoStore.dispatch(HippoAction.SetAdvancedViewPreselect(preSelect))
            }
        }
        loadStatistics(hippoStore.getState())
    }

    private fun navigateWithBookmark(nextState: HippoState) {
        val bookmarkString = nextState.createBookmarkString()
        println("In navigateWithBookmark, bookmarkString = '$bookmarkString', preState:")
        console.log(nextState)
        val route: String =
            if (bookmarkString.isNotEmpty()) "/filter=$bookmarkString"
            else ""
        val newView = nextState.view
        routing.navigate(newView.url + route)
    }

    private fun parseUrlForView(url: String): View {
        if (url.contains("integrationer.tjansteplattform")) return View.HIPPO
        if (url.contains("statistik.tjansteplattform")) return View.STAT_SIMPLE

        if (url.contains(View.STAT_SIMPLE.url)) return View.STAT_SIMPLE
        if (url.contains(View.STAT_ADVANCED.url)) return View.STAT_ADVANCED

        return View.HIPPO
    }
}
