package com.ashrdev.aznd.ui.manage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.data.manage.DataTransfer

enum class ManagePage(val id: String, val title: String, val icon: ImageVector) {
    DELETE("delete", "Delete", Icons.Default.Delete),
    EXPORT("export", "Export", Icons.Default.FileUpload),
    IMPORT("import", "Import", Icons.Default.FileDownload);

    companion object {
        fun fromId(id: String?): ManagePage = entries.firstOrNull { it.id == id } ?: DELETE
    }
}

class ManageDataLauncher(
    val openForCurrentScreen: () -> Unit,
    val open: (app: DataApp?, page: ManagePage) -> Unit
)

val LocalManageData = staticCompositionLocalOf<ManageDataLauncher?> { null }

val LocalDataTransfer = staticCompositionLocalOf<DataTransfer?> { null }
