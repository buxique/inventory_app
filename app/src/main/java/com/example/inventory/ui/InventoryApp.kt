package com.example.inventory.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inventory.ui.screens.AddItemScreen
import com.example.inventory.ui.screens.CaptureScreen
import com.example.inventory.ui.screens.ImagePickerScreen
import com.example.inventory.ui.screens.InventoryListScreenWithMultiList
import com.example.inventory.ui.screens.SettingsScreen
import com.example.inventory.ui.screens.StartScreen
import com.example.inventory.ui.viewmodel.AppViewModelFactory
import com.example.inventory.ui.viewmodel.CaptureViewModel
import com.example.inventory.ui.viewmodel.ImportViewModel
import com.example.inventory.ui.viewmodel.InventoryListViewModel
import com.example.inventory.ui.viewmodel.InventoryViewModelRefactored
import com.example.inventory.ui.viewmodel.SettingsViewModel

/**
 * 应用主导航
 * 
 * 使用多库存列表版本，支持：
 * - 多个独立的库存列表管理
 * - 列表切换和重命名
 * - 文件导入创建新列表
 * - 完整的库存管理功能
 */
@Composable
fun InventoryApp(factory: AppViewModelFactory) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // 在父级作用域创建 ViewModel，避免重复注册 SavedStateProvider
    val captureViewModel: CaptureViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val inventoryViewModel: InventoryViewModelRefactored = viewModel(factory = factory)
    val listViewModel: InventoryListViewModel = viewModel(factory = factory)
    val importViewModel: ImportViewModel = viewModel(factory = factory)

    // 检查是否有库存列表，决定启动页面
    // 注意：不要自动跳转，让用户在StartScreen选择列表
    // LaunchedEffect 已移除，避免自动跳转

    NavHost(navController = navController, startDestination = "start") {
        composable("start") {
            StartScreen(
                listViewModel = listViewModel,
                onCreateNew = {
                    navController.navigate("inventory")
                },
                onImportExcel = { uri: Uri ->
                    // 导入 Excel 文件：创建新列表并导航到导入流程
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_uri", uri.toString())
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_type", "excel")
                    navController.navigate("inventory?showImport=true")
                },
                onImportAccess = { uri: Uri ->
                    // 导入 Access 文件
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_uri", uri.toString())
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_type", "access")
                    navController.navigate("inventory?showImport=true")
                },
                onImportDatabase = { uri: Uri ->
                    // 导入数据库文件
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_uri", uri.toString())
                    navController.currentBackStackEntry?.savedStateHandle?.set("import_type", "database")
                    navController.navigate("inventory?showImport=true")
                },
                onSelectList = { listId ->
                    // 切换到选中的列表并跳转
                    listViewModel.switchToList(listId)
                    navController.navigate("inventory") {
                        popUpTo("start") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "inventory?showImport={showImport}",
            arguments = listOf(navArgument("showImport") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val showImport = backStackEntry.arguments?.getBoolean("showImport") ?: false
            
            // 使用多列表版本
            InventoryListScreenWithMultiList(
                inventoryViewModel = inventoryViewModel,
                listViewModel = listViewModel,
                importViewModel = importViewModel,
                inventoryRepository = factory.container.inventoryRepository,
                importCoordinator = factory.container.importCoordinator,
                showImport = showImport,
                onNavigateCapture = { navController.navigate("capture") },
                onNavigateAddItem = { navController.navigate("addItem") },
                onNavigateImagePicker = { navController.navigate("imagePicker") },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }
        composable("addItem") {
            AddItemScreen(
                onBack = { navController.popBackStack() },
                onSave = { formData ->
                    inventoryViewModel.addItemFromForm(formData)
                    navController.popBackStack()
                }
            )
        }
        composable("imagePicker") {
            ImagePickerScreen(
                onBack = { navController.popBackStack() },
                onComplete = { selectedImages ->
                    // TODO: 处理选中的图片
                    navController.popBackStack()
                }
            )
        }
        composable("capture") {
            CaptureScreen(
                viewModel = captureViewModel,
                inventoryViewModel = inventoryViewModel,
                listViewModel = listViewModel,
                importViewModel = importViewModel,
                inventoryRepository = factory.container.inventoryRepository,
                importCoordinator = factory.container.importCoordinator,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
