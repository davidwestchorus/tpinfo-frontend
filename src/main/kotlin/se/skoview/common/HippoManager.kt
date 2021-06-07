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

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.kvision.core.Container
import io.kvision.html.main
import io.kvision.pace.Pace
import io.kvision.redux.createReduxStore
import io.kvision.navigo.Navigo
import se.skoview.hippo.hippoView
import se.skoview.stat.PreSelect
import se.skoview.stat.loadStatistics
import se.skoview.stat.preSelectInitialize
import se.skoview.stat.statPage

/**
 * HippoManager is a central component. It manages:
 * 1. Initialization of the different parts of the applications.
 * 1. URL and navigation
 * 1. Bookmarks
 * 1. All dispatch of state updates
 */
object HippoManager { // } : CoroutineScope by CoroutineScope(Dispatchers.Default + SupervisorJob()) {

    private val routing = Navigo(null, true, "#")

    // todo: Move remaining dispatch to HippoManager and make the hippoStore private
    private val hippoStore = createReduxStore(
        ::hippoReducer,
        initializeHippoState()
    )

    /**
     * Initialize the hippo or statistics application. It loads the base information, and then integrations or
     * statistics dependeing on the URL.
     */
    fun initialize() {

        Pace.init()
        routing.initialize().resolve()

        val startUrl = window.location.href
        println("In HippoManager.initialize(), startUrl = $startUrl")

        GlobalScope.launch {

            hippoStore.dispatch(HippoAction.StartDownloadBaseItems)
            loadBaseItems()
            hippoStore.dispatch(HippoAction.DoneDownloadBaseItems)
            preSelectInitialize()

            if (hippoStore.getState().view == View.HIPPO) loadIntegrations(hippoStore.getState())
            else {
                if (hippoStore.getState().selectedPlattformChainsIds.isEmpty()) {
                    val tpId: Int? = Plattform.nameToId("SLL-PROD")
                    if (tpId != null) {
                        hippoStore.dispatch(HippoAction.StatTpSelected(tpId))
                    }
                }
                loadStatistics(hippoStore.getState())
            }
        }
    }

    /**
     * Defines the main() which is called at each state change.
     */
    fun Container.mainLoop() {
        // place for common header
        println("In mainLoop()")

        // Called after each state change
        main(hippoStore) { state ->
            println("In main()")
            if (state.downloadBaseItemStatus == AsyncActionStatus.COMPLETED) {
                when (state.view) {
                    View.HOME -> println("Got View.HOME in main()")
                    View.HIPPO -> {
                        if ( // This if-construct should be possible to remove
                            state.downloadBaseItemStatus == AsyncActionStatus.COMPLETED // &&
                         ) {
                             hippoView(state)
                         }
                    }
                    View.STAT -> {
                        statPage(state)
                    }
                }
            }
        }
        // footer()
    }

    fun newOrUpdatedUrlFromBrowser(
        view: View,
        params: String? = null,
        origin: String = ""
    ) {
        println("¤¤¤ In fromUrl(), view=$view, params=$params, origin=$origin")
        val fullUrl = window.location.href
        println("URL is: $fullUrl")

        // If no params then we might have an old saved hippo link with a filter parameter in the wrong place
        // Check the whole URL
        val filter: String = if (params == null) fullUrl
        else params

        val bookmark = parseBookmarkString(filter)
        println("bookmark from filter:")
        console.log(bookmark)

        val oldState = hippoStore.getState()
        hippoStore.dispatch(HippoAction.ApplyBookmark(view, bookmark))
        val newState = hippoStore.getState()

        val isIntegrationSelectionsChanged: Boolean = newState.isIntegrationSelectionsChanged(oldState)
        if (isIntegrationSelectionsChanged) println("Update integrations")
        else println("Do NOT update integrations")

        val isStatisticsSelectionsChanged = newState.isStatisticsSelectionsChanged(oldState)
        if (isStatisticsSelectionsChanged) println("Update statistics")
        else println("Do NOT update statistics")

        when (view) {
            View.HOME -> routing.navigate(View.HIPPO.url)
            View.HIPPO -> {
                document.title = "hippo v7"
                if (
                    newState.downloadBaseDatesStatus == AsyncActionStatus.COMPLETED &&
                    newState.isIntegrationSelectionsChanged(oldState)
                )
                    loadIntegrations(hippoStore.getState())
            }
            View.STAT -> {
                document.title = "Statistik v7"
                if (newState.downloadBaseItemStatus == AsyncActionStatus.COMPLETED) {
                    if (newState.isStatPlattformSelected()) {
                        if (
                            newState.isStatisticsSelectionsChanged(oldState) ||
                            oldState.view != View.STAT
                        ) {
                            loadStatistics(hippoStore.getState())
                        }
                    } else statTpSelected(Plattform.nameToId("SLL-PROD")!!)

                }
            }
        }
    }

    fun dispatchProxy(action: HippoAction) {
        hippoStore.dispatch(action)
    }

    fun dateSelected(type: DateType, date: String) {
        val nextState = hippoStore.getState()
            .dateSelected(date, type)
        navigateWithBookmark(nextState)
    }

    fun itemSelected(itemId: Int, type: ItemType) {
        val nextState = hippoStore.getState()
            .setShowAllItemTypes(true)
            .itemIdSeclected(itemId, type)
        navigateWithBookmark(nextState)
    }

    fun itemDeselected(itemId: Int, type: ItemType) {
        println("Deselect item, state:")
        console.log(hippoStore.getState())
        val nextState = hippoStore.getState()
            .itemIdDeseclected(itemId, type)
        navigateWithBookmark(nextState)
    }

    fun itemSelectDeselect(itemId: Int, itemType: ItemType) {
        if (hippoStore.getState().isItemSelected(itemType, itemId)) {
            itemDeselected(itemId, itemType)
        } else {
            itemSelected(itemId, itemType)
        }
    }

    fun setViewMax(type: ItemType, lines: Int) {
        hippoStore.dispatch(HippoAction.SetVMax(type, lines))
    }

    fun statTpSelected(tpId: Int) {
        // hippoStore.dispatch(HippoAction.StatTpSelected(tpId))
        val nextState = hippoStore.getState().statTpSelected(tpId)
        navigateWithBookmark(nextState)
    }

    fun statTechnicalTermsSelected(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowTechnicalTerms(flag))
        // val nextState = hippoStore.getState().setFlag(HippoAction.ShowTechnicalTerms(flag))
        // navigateWithBookmark(nextState)
    }

    /*
    fun statShowConsumers(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowConsumers(flag))
    }

    fun statShowProducers(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowProduceras(flag))
    }

    fun statShowContracts(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowContracts(flag))
    }

    fun statShowLogicalAddresses(flag: Boolean) {
        hippoStore.dispatch(HippoAction.ShowLogicalAddresses(flag))
    }
*/
    fun statShowAllItemTypes() {
        // hippoStore.dispatch(HippoAction.ShowAllItemTypes(flag))
        val nextState = hippoStore.getState()
            .setShowAllItemTypes(true)
        navigateWithBookmark(nextState)
    }

    fun statHistorySelected(flag: Boolean) {
        // if (flag) loadHistory(hippoStore.getState()) // Preload of history
        // hippoStore.dispatch(HippoAction.ShowTimeGraph(flag))
        val nextState = hippoStore.getState()
            .setFlag(HippoAction.ShowTimeGraph(flag)) // .setShowAllItemTypes(true)
        navigateWithBookmark(nextState)
    }

    fun setView(view: View) {
        val nextState = hippoStore.getState()
            .setNewView(view)
        console.log(nextState)
        navigateWithBookmark(nextState)
    }

    fun statSetPreselect(preSelectLabel: String) {
        val preSelect: PreSelect? = PreSelect.mapp[preSelectLabel]
        val nextState =
            hippoStore.getState()
                .setPreselect(preSelect)
                .setFlag(HippoAction.ShowTimeGraph(false))
        navigateWithBookmark(nextState)
    }

    private fun navigateWithBookmark(nextState: HippoState) {
        val bookmarkString = nextState.createBookmarkString()
        println("In navigateWithBookmark, bookmarkString = '$bookmarkString', nextState:")
        console.log(nextState)
        val route: String =
            if (bookmarkString.isNotEmpty()) "/filter=$bookmarkString"
            else ""
        val newView = nextState.view
        routing.navigate(newView.url + route)
    }
}
