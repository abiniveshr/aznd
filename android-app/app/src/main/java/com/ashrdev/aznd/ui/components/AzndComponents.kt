package com.ashrdev.aznd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.ui.theme.AzndStyle
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.drawTaperLine
import com.ashrdev.aznd.ui.theme.taperEdge
import androidx.compose.ui.draw.drawBehind

// Drop-in replacements for the Material3 components of the same name. Every screen imports these
// (not androidx.compose.material3.Card etc.) so the Appearance settings apply everywhere: border widths,
// and the gradient / pattern fills of cards, buttons, the top bar, dialogs and text fields.

/** A border of [width] in [color], or none when the width is 0. */
private fun borderOf(width: Dp, color: Color): BorderStroke? = if (width > 0.dp) BorderStroke(width, color) else null

private fun AzndStyle.edge(width: Dp, line: Boolean): BorderStroke? = if (line) null else borderOf(width, outline)

private fun AzndStyle.underline(width: Dp, line: Boolean, inset: Dp): Modifier =
    if (line && width > 0.dp) Modifier.taperEdge(borderTaper, outline, width, top = false, inset = inset) else Modifier

/**
 * A card. With no [colors] it takes the theme's Cards fill (or, with [highlight], the Highlight cards fill).
 * Passing [colors] opts out of the theme fill and uses exactly those colours (error cards, translucent
 * placeholders ...).
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    val border = style.edge(style.cardBorder, style.cardBorderLine)
    val line = style.underline(style.cardBorder, style.cardBorderLine, style.cardCorner.dp)
    val fill = style.fill(if (highlight) ThemeColor.HIGHLIGHT else ThemeColor.CARD)
    val scheme = MaterialTheme.colorScheme
    if (colors == null && !fill.isPlain) {
        // The card itself is see-through; the fill is painted behind it.
        val shape = CardDefaults.shape
        androidx.compose.material3.Card(
            modifier = modifier.azndFill(fill, shape).then(line),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = if (highlight) scheme.onPrimaryContainer else scheme.onSurface
            ),
            border = border,
            content = content
        )
    } else {
        androidx.compose.material3.Card(
            modifier = modifier.then(line),
            colors = colors ?: if (highlight) {
                CardDefaults.cardColors(containerColor = scheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            },
            border = border,
            content = content
        )
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalAzndStyle.current
    val fill = style.fill(ThemeColor.BUTTON)
    val custom = enabled && !fill.isPlain
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = (if (custom) modifier.azndFill(fill, style.buttonShape) else modifier)
            .then(style.underline(style.filledButtonBorder, style.filledButtonBorderLine, style.buttonCorner.dp)),
        enabled = enabled,
        shape = style.buttonShape,
        colors = if (custom) ButtonDefaults.buttonColors(containerColor = Color.Transparent) else ButtonDefaults.buttonColors(),
        border = style.edge(style.filledButtonBorder, style.filledButtonBorderLine),
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
    val fill = style.fill(ThemeColor.HIGHLIGHT)
    val custom = enabled && !fill.isPlain
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = (if (custom) modifier.azndFill(fill, style.buttonShape) else modifier)
            .then(style.underline(style.filledButtonBorder, style.filledButtonBorderLine, style.buttonCorner.dp)),
        enabled = enabled,
        shape = style.buttonShape,
        colors = if (custom) ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent) else ButtonDefaults.filledTonalButtonColors(),
        border = style.edge(style.filledButtonBorder, style.filledButtonBorderLine),
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
        modifier = modifier.then(style.underline(style.outlinedButtonBorder, style.outlinedButtonBorderLine, style.buttonCorner.dp)),
        enabled = enabled,
        shape = style.buttonShape,
        border = style.edge(style.outlinedButtonBorder, style.outlinedButtonBorderLine),
        content = content
    )
}

/**
 * Outlined text field. With the border at its default 1 dp (or the plain-colour no-border look) this is
 * Material's own field. Any other border width, or a gradient / pattern Cards fill behind a borderless
 * field, needs the field assembled from Material's parts ([CustomOutlinedField]).
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = LocalTextStyle.current
) {
    val style = LocalAzndStyle.current
    val width = style.fieldBorder
    val cardFill = style.fill(ThemeColor.CARD)
    val shape = OutlinedTextFieldDefaults.shape

    val underline = style.fieldBorderLine && width > 0.dp
    val needsCustom = underline || if (width <= 0.dp) !cardFill.isPlain else width != 1.dp
    if (needsCustom) {
        CustomOutlinedField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.then(style.underline(width, underline, (style.cardCorner * 0.33f).dp)),
            label = label,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle,
            fill = if (width <= 0.dp) cardFill else null,
            shape = shape,
            borderWidth = if (underline) 0.dp else width
        )
        return
    }

    val colors = if (width > 0.dp) {
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
        textStyle = textStyle,
        colors = colors
    )
}

/**
 * The same field Material builds, put together by hand so the border thickness and the container's fill
 * can be chosen: a BasicTextField with Material's decoration box and container.
 */
@Composable
private fun CustomOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    label: @Composable (() -> Unit)?,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    textStyle: TextStyle,
    fill: com.ashrdev.aznd.ui.theme.ResolvedFill?,
    shape: Shape,
    borderWidth: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val noBorder = borderWidth <= 0.dp
    val colors: TextFieldColors = if (noBorder) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
    val textColor = if (textStyle.color != Color.Unspecified) textStyle.color else scheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            // Room above the box for the label when it floats up onto the border.
            .then(if (label != null) Modifier.padding(top = 8.dp) else Modifier)
            .defaultMinSize(minWidth = 280.dp, minHeight = 56.dp),
        textStyle = textStyle.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = label,
                colors = colors,
                container = {
                    if (fill != null) Box(Modifier.fillMaxSize().azndFill(fill, shape))
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                        focusedBorderThickness = if (noBorder) 0.dp else borderWidth + 1.dp,
                        unfocusedBorderThickness = if (noBorder) 0.dp else borderWidth
                    )
                }
            )
        }
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
    val fill = style.fill(ThemeColor.TOP_BAR)
    val custom = colors == null && !fill.isPlain
    Column(modifier) {
        androidx.compose.material3.TopAppBar(
            title = title,
            // The fill covers the whole bar, including the strip under the status bar.
            modifier = if (custom) Modifier.azndFill(fill) else Modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors ?: TopAppBarDefaults.topAppBarColors(
                containerColor = if (custom) Color.Transparent else style.topBar,
                titleContentColor = style.onTopBar,
                navigationIconContentColor = style.onTopBar,
                actionIconContentColor = style.onTopBar
            )
        )
        if (style.topBarBorder > 0.dp) {
            if (style.topBarBorderLine) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(style.topBarBorder)
                        .drawBehind { drawTaperLine(style.borderTaper, style.outline, 0f, 0f, size.width, size.height) }
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(style.topBarBorder)
                        .background(style.outline)
                )
            }
        }
    }
}

/**
 * Drop-in for Material's AlertDialog: with a gradient / pattern Cards fill the dialog's surface is painted
 * with it. Otherwise it is Material's dialog, untouched.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val fill = LocalAzndStyle.current.fill(ThemeColor.CARD)
    if (fill.isPlain) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
            properties = properties
        )
    } else {
        val shape = AlertDialogDefaults.shape
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier.azndFill(fill, shape),
            dismissButton = dismissButton,
            title = title,
            text = text,
            shape = shape,
            containerColor = Color.Transparent,
            properties = properties
        )
    }
}
