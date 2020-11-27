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

import se.skoview.stat.AdvancedViewPreSelect
import se.skoview.stat.SimpleViewPreSelect
import se.skoview.stat.StatisticsBlob
import se.skoview.stat.simpleViewPreSelectDefault
import kotlin.reflect.KClass

enum class AsyncActionStatus {
    NOT_INITIALIZED,
    INITIALIZED,
    COMPLETED,
    ERROR
}

enum class DateType {
    EFFECTIVE,
    END,
    EFFECTIVE_AND_END,
    STAT_EFFECTIVE,
    STAT_END
}

data class HippoState(
    // Status information
    val currentAction: KClass<out HippoAction> = HippoAction.SetView::class,
    val view: View = View.HOME,
    val downloadBaseDatesStatus: AsyncActionStatus = AsyncActionStatus.NOT_INITIALIZED,
    val downloadBaseItemStatus: AsyncActionStatus = AsyncActionStatus.NOT_INITIALIZED,
    val downloadIntegrationStatus: AsyncActionStatus = AsyncActionStatus.NOT_INITIALIZED,
    val downloadStatisticsStatus: AsyncActionStatus = AsyncActionStatus.NOT_INITIALIZED,
    val downloadHistoryStatus: AsyncActionStatus = AsyncActionStatus.NOT_INITIALIZED,
    val errorMessage: String? = null,

    // Base Items
    // todo: Why are the base items stored via the state? Ought to be enough to register when they are loaded.
    val integrationDates: List<String> = listOf(),
    val statisticsDates: List<String> = listOf(),
    val serviceComponents: Map<Int, ServiceComponent> = mapOf(),
    val logicalAddresses: Map<Int, LogicalAddress> = mapOf(),
    val serviceContracts: Map<Int, ServiceContract> = mapOf(),
    val serviceDomains: Map<Int, ServiceDomain> = mapOf(),
    val plattforms: Map<Int, Plattform> = mapOf(),
    val plattformChains: Map<Int, PlattformChain> = mapOf(),
    val statisticsPlattforms: Map<Int, StatisticsPlattform> = mapOf(),

    // Filter parameters
    val dateEffective: String? = null,
    val dateEnd: String? = null,
    val statDateEffective: String = "",
    val statDateEnd: String = "",

    val selectedConsumers: List<Int> = listOf(),
    val selectedProducers: List<Int> = listOf(),
    val selectedLogicalAddresses: List<Int> = listOf(),
    val selectedContracts: List<Int> = listOf(),
    val selectedDomains: List<Int> = listOf(),
    val selectedPlattformChains: List<Int> = listOf(),

    // Integrations data
    val selectedPlattformName: String = "",
    val integrationArrs: List<Integration> = listOf(),
    val maxCounters: MaxCounter = MaxCounter(0, 0, 0, 0, 0, 0),
    val updateDates: List<String> = listOf(),

    // Max number of items to display
    val vServiceConsumersMax: Int = 100,
    val vServiceProducersMax: Int = 100,
    val vLogicalAddressesMax: Int = 100,
    val vServiceContractsMax: Int = 500,

    // Statistics information
    val statBlob: StatisticsBlob = StatisticsBlob(arrayOf(arrayOf())),

    // History information
    val historyMap: Map<String, Int> = mapOf(),
    val showTimeGraph: Boolean = false,

    // View controllers
    val showTechnicalTerms: Boolean = false,
    // val viewMode: ViewMode = ViewMode.SIMPLE,
    val simpleViewPreSelect: SimpleViewPreSelect = simpleViewPreSelectDefault,
    val advancedViewPreSelect: AdvancedViewPreSelect? = null
)

fun initializeHippoState(): HippoState {
    val datesPair = getDatesLastMonth()
    val statDateEffective = datesPair.first.toSwedishDate()
    val statDateEnd = datesPair.second.toSwedishDate()

    return HippoState(
        statDateEffective = statDateEffective,
        statDateEnd = statDateEnd,
    )
}

fun HippoState.isItemSelected(itemType: ItemType, id: Int): Boolean {
    return when (itemType) {
        ItemType.CONSUMER -> this.selectedConsumers.contains(id)
        ItemType.DOMAIN -> this.selectedDomains.contains(id)
        ItemType.CONTRACT -> this.selectedContracts.contains(id)
        ItemType.PLATTFORM_CHAIN -> this.selectedPlattformChains.contains(id)
        ItemType.LOGICAL_ADDRESS -> this.selectedLogicalAddresses.contains(id)
        ItemType.PRODUCER -> this.selectedProducers.contains(id)
    }
}

fun HippoState.isPlattformSelected(id: Int): Boolean {
    val pChainId = PlattformChain.calculateId(first = id, middle = null, last = id)

    return this.selectedPlattformChains.contains(pChainId)
}

// The extension function create the part of the URL to fetch integrations
fun HippoState.getParams(view: View): String {

    // var params = "?dummy&contractId=379"
    var params = "?dummy"

    if (view == View.HIPPO) {
        params += "&dateEffective=" + this.dateEffective
        params += "&dateEnd=" + this.dateEnd
    } else {
        params += "&dateEffective=" + this.statDateEffective
        params += "&dateEnd=" + this.statDateEnd
    }

    params += if (this.selectedConsumers.isNotEmpty()) this.selectedConsumers.joinToString(
        prefix = "&consumerId=",
        separator = ","
    ) else ""
    params += if (this.selectedDomains.isNotEmpty()) this.selectedDomains.joinToString(
        prefix = "&domainId=",
        separator = ","
    ) else ""
    params += if (this.selectedContracts.isNotEmpty()) this.selectedContracts.joinToString(
        prefix = "&contractId=",
        separator = ","
    ) else ""
    params += if (this.selectedLogicalAddresses.isNotEmpty()) this.selectedLogicalAddresses.joinToString(
        prefix = "&logicalAddressId=",
        separator = ","
    ) else ""
    params += if (this.selectedProducers.isNotEmpty()) this.selectedProducers.joinToString(
        prefix = "&producerId=",
        separator = ","
    ) else ""

    // Separate plattforms now stored in filter, not the chain
    for (pcId in this.selectedPlattformChains) {
        print("In getParams()")
        val firstId = PlattformChain.map[pcId]?.first
        val lastId = PlattformChain.map[pcId]?.last
        params += "&firstPlattformId=$firstId"
        params += "&lastPlattformId=$lastId"
    }

    return params
}

// is HippoAction.ItemIdSelected -> {
fun HippoState.itemIdSeclected(id: Int, viewType: ItemType): HippoState {

    return when (viewType) {
        ItemType.CONSUMER -> {
            val newList = listOf(this.selectedConsumers, listOf(id)).flatten().distinct()
            this.copy(
                selectedConsumers = newList
            )
        }
        ItemType.DOMAIN -> {
            val newList = listOf(this.selectedDomains, listOf(id)).flatten().distinct()
            this.copy(
                selectedDomains = newList
            )
        }
        ItemType.CONTRACT -> {
            val newList = listOf(this.selectedContracts, listOf(id)).flatten().distinct()
            this.copy(
                selectedContracts = newList
            )
        }
        ItemType.PLATTFORM_CHAIN -> {
            val newList = listOf(this.selectedPlattformChains, listOf(id)).flatten().distinct()
            this.copy(
                selectedPlattformChains = newList
            )
        }
        ItemType.LOGICAL_ADDRESS -> {
            val newList = listOf(this.selectedLogicalAddresses, listOf(id)).flatten().distinct()
            this.copy(
                selectedLogicalAddresses = newList
            )
        }
        ItemType.PRODUCER -> {
            val newList = listOf(this.selectedProducers, listOf(id)).flatten().distinct()
            this.copy(
                selectedProducers = newList
            )
        }
    }
}

// is HippoAction.ItemIdDeselected -> {
fun HippoState.itemIdDeseclected(id: Int, viewType: ItemType): HippoState {
    return when (viewType) {
        ItemType.CONSUMER -> {
            val newList = this.selectedConsumers as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedConsumers = newList,
                advancedViewPreSelect = null
            )
        }
        ItemType.DOMAIN -> {
            val newList = this.selectedDomains as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedDomains = newList,
                advancedViewPreSelect = null
            )
        }
        ItemType.CONTRACT -> {
            val newList = this.selectedContracts as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedContracts = newList,
                advancedViewPreSelect = null
            )
        }
        ItemType.PLATTFORM_CHAIN -> {
            val newList = this.selectedPlattformChains as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedPlattformChains = newList,
                advancedViewPreSelect = null
            )
        }
        ItemType.LOGICAL_ADDRESS -> {
            val newList = this.selectedLogicalAddresses as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedLogicalAddresses = newList,
                advancedViewPreSelect = null
            )
        }
        ItemType.PRODUCER -> {
            val newList = this.selectedProducers as MutableList<Int>
            newList.remove(id)
            this.copy(
                selectedProducers = newList,
                advancedViewPreSelect = null
            )
        }
    }
}

// is HippoAction.StatTpSelected -> {
fun HippoState.statTpSelected(tpId: Int): HippoState {
    // TP can only be selected in advanced mode

    val preSelect: AdvancedViewPreSelect = AdvancedViewPreSelect.getDefault()
    val pChainId = PlattformChain.calculateId(first = tpId, middle = null, last = tpId)

    return this.copy(
        selectedPlattformChains = listOf(pChainId),
        advancedViewPreSelect = preSelect
    )
}

// is HippoAction.SetView -> {
fun HippoState.setNewView(newView: View): HippoState {

    val currentView = this.view
    if (currentView == newView) throw RuntimeException("Current view  == new  in reducer SetViewMode")

    // If the new mode have a preselect with the same label as the current, apply it. Otherwise use its default.
    if (
        currentView == View.STAT_ADVANCED &&
        newView == View.STAT_SIMPLE
    ) {
        val currentAdvancedPreSelect = this.advancedViewPreSelect ?: AdvancedViewPreSelect.getDefault()
        val currentAdvancedPreSelectLabel = currentAdvancedPreSelect.label
        val newSimpleViewPreSelect =
            SimpleViewPreSelect.mapp[currentAdvancedPreSelectLabel] ?: SimpleViewPreSelect.getDefault()
        return applyFilteredItemsSelection(
            this,
            newSimpleViewPreSelect.filteredItems
        ).copy(
            simpleViewPreSelect = newSimpleViewPreSelect,
            view = newView
        )
    }

    if (currentView == View.STAT_SIMPLE &&
        newView == View.STAT_ADVANCED
    ) {
        val currentSimplePreSelectLabel = this.simpleViewPreSelect.label
        val newAdvancedViewPreSelect =
            AdvancedViewPreSelect.mapp[currentSimplePreSelectLabel] ?: AdvancedViewPreSelect.getDefault()
        return applyFilteredItemsSelection(
            this,
            newAdvancedViewPreSelect.filteredItems
        ).copy(
            advancedViewPreSelect = newAdvancedViewPreSelect,
            view = newView
        )
    }

    // Switch from hippo to statistics
    if (
        currentView == View.HIPPO &&
        (newView == View.STAT_SIMPLE || newView == View.STAT_ADVANCED)
    ) {
        if (this.selectedPlattformChains.isNotEmpty()) {
            val pcId = this.selectedPlattformChains[0]
            val pc = PlattformChain.map[pcId]!!
            val tpFirstId = Plattform.mapp[pc.first]!!.id
            val tpLastId = Plattform.mapp[pc.last]!!.id

            val tpId = if (this.statisticsPlattforms.containsKey(tpFirstId)) tpFirstId
            else if (this.statisticsPlattforms.containsKey(tpLastId)) tpLastId
            else Plattform.nameToId("SLL-PROD")

            return this.copy(
                selectedPlattformChains = listOf(PlattformChain.calculateId(tpId!!, 0, tpId)),
                view = newView
            )
        }
    }

    return this.copy(view = newView)
}

/*
fun HippoState.setView(newView: View): HippoState {

    val currentView = this.view
    if (currentView == newView) throw RuntimeException("Current view  == new  in reducer SetViewMode")

    // If the new mode have a preselect with the same label as the current, apply it. Otherwise use its default.
    return when (newView) {
        View.STAT_SIMPLE -> {
            val currentAdvancedPreSelect = this.advancedViewPreSelect ?: AdvancedViewPreSelect.getDefault()
            val currentAdvancedPreSelectLabel = currentAdvancedPreSelect.label
            val newSimpleViewPreSelect =
                SimpleViewPreSelect.mapp[currentAdvancedPreSelectLabel] ?: SimpleViewPreSelect.getDefault()
            applyFilteredItemsSelection(
                this,
                newSimpleViewPreSelect.filteredItems
            ).copy(
                simpleViewPreSelect = newSimpleViewPreSelect,
                view = View.STAT_SIMPLE
            )
        }
        View.STAT_ADVANCED -> {
            val currentSimplePreSelectLabel = this.simpleViewPreSelect.label
            val newAdvancedViewPreSelect =
                AdvancedViewPreSelect.mapp[currentSimplePreSelectLabel] ?: AdvancedViewPreSelect.getDefault()
            applyFilteredItemsSelection(this, newAdvancedViewPreSelect.filteredItems).copy(
                advancedViewPreSelect = newAdvancedViewPreSelect,
                view = View.STAT_ADVANCED
            )
        }
        else -> this.copy(view = View.HIPPO)
    }
}
*/
fun HippoState.dateSelected(selectedDate: String, dateType: DateType): HippoState {
    //  is HippoAction.DateSelected -> {
    return when (dateType) {
        DateType.EFFECTIVE -> this.copy(dateEffective = selectedDate)
        DateType.END -> this.copy(dateEnd = selectedDate)
        DateType.EFFECTIVE_AND_END -> this.copy(
            dateEffective = selectedDate,
            dateEnd = selectedDate
        )
        DateType.STAT_EFFECTIVE -> this.copy(statDateEffective = selectedDate)
        DateType.STAT_END -> this.copy(statDateEnd = selectedDate)
    }
}

// is HippoAction.ApplyBookmark -> {
fun HippoState.applyBookmark(view: View, bookmark: BookmarkInformation): HippoState {

    val newState =

        if (view == View.HIPPO) {
            val newDateEffective: String? =
                if (bookmark.dateEffective != null) bookmark.dateEffective
                else this.dateEffective
            val newDateEnd: String? =
                if (bookmark.dateEnd != null) bookmark.dateEnd
                else this.dateEnd

            this.copy(
                dateEffective = newDateEffective,
                dateEnd = newDateEnd,
            )
        } else {
            val datesLastMonth = getDatesLastMonth()

            val newDateEffective: String =
                if (bookmark.dateEffective != null) bookmark.dateEffective!!
                else datesLastMonth.first.toSwedishDate()

            val newDateEnd: String =
                if (bookmark.dateEnd != null) bookmark.dateEnd!!
                else datesLastMonth.second.toSwedishDate()

            this.copy(
                statDateEffective = newDateEffective,
                statDateEnd = newDateEnd
            )
        }

    return newState.copy(
        view = view,
        selectedConsumers = bookmark.selectedConsumers,
        selectedProducers = bookmark.selectedProducers,
        selectedLogicalAddresses = bookmark.selectedLogicalAddresses,
        selectedContracts = bookmark.selectedContracts,
        selectedDomains = bookmark.selectedDomains,
        selectedPlattformChains = bookmark.selectedPlattformChains
    )
}
