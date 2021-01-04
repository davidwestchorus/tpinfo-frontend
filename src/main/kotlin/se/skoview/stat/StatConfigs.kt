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
package se.skoview.stat

import com.github.snabbdom._get
import pl.treksoft.kvision.chart.*
import pl.treksoft.kvision.core.*
import pl.treksoft.kvision.html.Div
import pl.treksoft.kvision.panel.SimplePanel
import pl.treksoft.kvision.table.TableType
import pl.treksoft.kvision.tabulator.*
import pl.treksoft.kvision.utils.perc
import se.skoview.common.HippoManager
import se.skoview.common.HippoState
import se.skoview.common.ItemType
import se.skoview.common.isItemSelected

fun getPieChartConfig(
    state: HippoState,
    itemType: ItemType,
    itemSInfoList: SInfoList,
    animationTime: Int = 0,
    responsive: Boolean = false,
    maintainAspectRatio: Boolean = true
): Configuration {
    return Configuration(
        ChartType.PIE,
        listOf(
            DataSets(
                data = itemSInfoList.callList(),
                backgroundColor = itemSInfoList.colorList()
            )
        ),
        itemSInfoList.descList(),
        options = ChartOptions(
            elements = ElementsOptions(arc = ArcOptions(borderWidth = 0)),
            animation = AnimationOptions(duration = animationTime),
            responsive = responsive,
            legend = LegendOptions(display = false),
            maintainAspectRatio = maintainAspectRatio,
            onClick = { _, activeElements ->
                val sliceIx = activeElements[0]._get("_index") as Int
                val itemId: Int = itemSInfoList.recordList[sliceIx].itemId
                itemSelectDeselect(state, itemId, itemType)
                "" // This lambda returns Any, which mean the last line must be an expression
            }
        )
    )
}

open class ChartLabelTable(
    state: HippoState,
    itemType: ItemType,
    itemSInfoList: List<SInfoRecord>,
    dataField: String = "description",
    colorField: String = "color",
    callsField: String = "calls",
    heading: String
) : SimplePanel() {
    init {
        id = "ChartLabelTable: SimpleTable"
        // Footer pagination buttons hidden through CSS
        tabulator(
            data = itemSInfoList,
            types = setOf(TableType.BORDERED, TableType.STRIPED, TableType.HOVER),
            options = TabulatorOptions(
                layout = Layout.FITCOLUMNS,
                pagination = PaginationMode.LOCAL,
                paginationSize = 1000,
                paginationButtonCount = 0,
                selectable = true,
                columns = listOf(
                    ColumnDefinition<Any>(
                        headerSort = false,
                        title = "",
                        field = colorField,
                        width = "(0.3).px",
                        formatter = Formatter.COLOR
                    ),
                    ColumnDefinition(
                        headerSort = false,
                        title = "$heading (${itemSInfoList.size})",
                        field = dataField,
                        topCalcFormatter = Formatter.COLOR,
                        headerFilter = Editor.INPUT,
                        headerFilterPlaceholder = "Sök ${heading.toLowerCase()}",
                        editable = { false },
                        widthGrow = 3,
                        formatterComponentFunction = { cell, _, item ->
                            // cell.apply { background = Background(Color.name(Col.ALICEBLUE)) }
                            val itemRecord = item.unsafeCast<SInfoRecord>()
                            var textToShow: String = itemRecord.description
                            Div(rich = true) {
                                whiteSpace = WhiteSpace.PREWRAP
                                wordBreak = WordBreak.BREAKALL
                                if (state.isItemSelected(itemRecord.itemType, itemRecord.itemId)) {
                                    fontWeight = FontWeight.BOLD
                                    cell.apply {
                                        background = Background(Color.name(Col.LIGHTGRAY))
                                    }
                                    textToShow = "$textToShow (<i>vald</i>)"
                                }
                                // whiteSpace = WhiteSpace.PREWRAP
                                // wordBreak = WordBreak.BREAKALL
                                +textToShow
                            }
                        }

                    ),
                    ColumnDefinition(
                        widthGrow = 1,
                        title = "Antal anrop",
                        hozAlign = Align.RIGHT,
                        field = callsField
                    )
                ),
                rowSelected = { row ->
                    val item = row.getData().unsafeCast<SInfoRecord>()
                    if (item.calls > -1) itemSelectDeselect(state, item.itemId, item.itemType)
                },
                // todo: Hide the tabulator footer here
            )
        )
            .apply {
                height = 100.perc
            }
    }
}

private fun itemSelectDeselect(state: HippoState, itemId: Int, itemType: ItemType) {
    if (state.isItemSelected(itemType, itemId)) {
        HippoManager.itemDeselected(itemId, itemType)
    } else {
        // Select an item
            /*
        if (state.view == View.STAT)
            HippoManager.itemAndViewSelected(itemId, itemType, View.STAT_ADVANCED)
        else
            HippoManager.itemSelected(itemId, itemType)
             */
        HippoManager.itemSelected(itemId, itemType)
    }
}
