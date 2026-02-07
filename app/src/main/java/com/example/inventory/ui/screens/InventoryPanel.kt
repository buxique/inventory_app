package com.example.inventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import com.example.inventory.R
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.viewmodel.ImportViewModel
import com.example.inventory.ui.viewmodel.InventoryListViewModel
import com.example.inventory.ui.viewmodel.InventoryViewModelRefactored
import androidx.compose.foundation.lazy.LazyListState

@Composable
internal fun InventoryPanel(
    modifier: Modifier,
    inventoryViewModel: InventoryViewModelRefactored,
    listViewModel: InventoryListViewModel,
    importViewModel: ImportViewModel,
    inventoryRepository: InventoryRepository,
    dropTargets: MutableMap<Long, Rect>,
    cellDropTargets: MutableMap<Long, MutableMap<String, Rect>>,
    highlightedCell: Pair<Long, String>?,
    isEditMode: Boolean,
    tableState: LazyListState,
    draggingGroup: OcrGroup?
) {
    Box(modifier = modifier) {
        InventoryListScreenWithMultiList(
            inventoryViewModel = inventoryViewModel,
            listViewModel = listViewModel,
            importViewModel = importViewModel,
            inventoryRepository = inventoryRepository,
            showImport = false,
            onNavigateCapture = {},
            onNavigateAddItem = {},
            onNavigateImagePicker = {},
            onNavigateSettings = {},
            onRowBoundsUpdated = { id, rect -> dropTargets[id] = rect },
            isEditMode = isEditMode,
            onCellBoundsUpdated = { itemId, field, rect ->
                cellDropTargets.getOrPut(itemId) { mutableMapOf() }[field] = rect
            },
            highlightedCell = highlightedCell,
            tableState = tableState
        )

        if (draggingGroup != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.drop_to_apply_text),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
