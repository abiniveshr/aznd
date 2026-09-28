package com.ashrdev.aznd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle

@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = colors,
        border = if (style.cardOutline) BorderStroke(1.dp, style.outline) else null,
        content = content
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = style.buttonShape,
        border = if (style.filledButtonOutline) BorderStroke(1.dp, style.outline) else null,
        content = content
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = style.buttonShape,
        border = if (style.filledButtonOutline) BorderStroke(1.dp, style.outline) else null,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = style.buttonShape,
        border = if (style.outlinedButtonOutline) BorderStroke(1.dp, style.outline) else null,
        content = content
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val style = LocalAzndStyle.current
    val colors = if (style.fieldOutline) {
        OutlinedTextFieldDefaults.colors()
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent,
            focusedContainerColor = style.card,
            unfocusedContainerColor = style.card
        )
    }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors? = null
) {
    val style = LocalAzndStyle.current
    Column(modifier) {
        androidx.compose.material3.TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors ?: TopAppBarDefaults.topAppBarColors(
                containerColor = style.topBar,
                titleContentColor = style.onTopBar,
                navigationIconContentColor = style.onTopBar,
                actionIconContentColor = style.onTopBar
            )
        )
        if (style.topBarOutline) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(style.outline)
            )
        }
    }
}