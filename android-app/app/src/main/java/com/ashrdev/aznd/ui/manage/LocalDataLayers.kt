package com.ashrdev.aznd.ui.manage

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.data.manage.DataTransfer
import com.ashrdev.aznd.data.manage.EXPORT_MIME
import com.ashrdev.aznd.data.manage.ImportReport
import com.ashrdev.aznd.data.manage.ImportStatus
import com.ashrdev.aznd.ui.common.DayRange
import kotlinx.coroutines.launch

class LocalDataLayer(
    val key: String,
    val app: DataApp,
    val label: String,
    val moduleIds: Set<String>
)

object LocalDataLayers {
    val WorkoutRoutines = LocalDataLayer(
        key = "workout_routines",
        app = DataApp.WORKOUTS,
        label = "routines",
        moduleIds = setOf("workout_routines", "custom_exercises")
    )
    val WorkoutHistory = LocalDataLayer(
        key = "workout_history",
        app = DataApp.WORKOUTS,
        label = "workout history",
        moduleIds = setOf("workout_history")
    )
    val Streaks = LocalDataLayer(
        key = "streaks",
        app = DataApp.STREAKS,
        label = "streaks",
        moduleIds = setOf("streaks", "streak_saved_days")
    )
    val Body = LocalDataLayer(
        key = "body",
        app = DataApp.BODY,
        label = "body data",
        moduleIds = setOf("bodyweight", "body_fat", "body_measurements")
    )
    val Macros = LocalDataLayer(
        key = "macros",
        app = DataApp.MACROS,
        label = "macros",
        moduleIds = setOf("food_log", "custom_ingredients", "macro_goals")
    )

    // FUTURE: one layer per future screen that wants its own export / import buttons, e.g.
    // val MealPlans = LocalDataLayer("meal_plans", DataApp.MEALS, "meal plans", setOf("meal_plans")).
    // Then, in that screen: val data = rememberLocalDataActions(LocalDataLayers.MealPlans) and call
    // data.exportNow() / data.importFromFile() from its own buttons or menu.
}

class LocalDataActions(
    val exportNow: () -> Unit,
    val importFromFile: () -> Unit,
    val openManage: (ManagePage) -> Unit
)

@Composable
fun rememberLocalDataActions(
    layer: LocalDataLayer,
    range: DayRange = DayRange.All,
    onExported: (counts: Map<String, Int>) -> Unit = {},
    onImported: ((List<ImportReport>) -> Unit)? = null
): LocalDataActions {
    val transfer = LocalDataTransfer.current
    val openManage = LocalManageData.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME)) { uri ->
        if (uri == null || transfer == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = try {
                val result = transfer.export(layer.moduleIds, range)
                if (result.counts.isEmpty()) {
                    "No ${layer.label} to export"
                } else {
                    transfer.write(context.contentResolver, uri, result.json)
                    onExported(result.counts)
                    "Exported ${layer.label}"
                }
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || transfer == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val pkg = transfer.parse(transfer.read(context.contentResolver, uri))
                val ids = pkg.sections
                    .filter { it.id in layer.moduleIds && transfer.statusOf(it) == ImportStatus.READY }
                    .map { it.id }.toSet()
                if (ids.isEmpty()) {
                    Toast.makeText(context, "This file has no ${layer.label}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val reports = transfer.import(pkg, ids, replaceExisting = false)
                if (onImported != null) {
                    onImported(reports)
                } else {
                    val added = reports.sumOf { it.outcome?.added ?: 0 }
                    Toast.makeText(context, "Imported $added ${layer.label}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    return remember(layer, openManage) {
        LocalDataActions(
            exportNow = { exportLauncher.launch(DataTransfer.suggestedFileName()) },
            importFromFile = { importLauncher.launch(arrayOf("*/*")) },
            openManage = { page -> openManage?.open(layer.app, page) }
        )
    }
}
