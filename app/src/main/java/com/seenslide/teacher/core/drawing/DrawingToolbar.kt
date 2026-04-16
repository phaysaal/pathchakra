package com.seenslide.teacher.core.drawing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val COLORS = listOf(
    0xFF000000L, // Black
    0xFFEF4444L, // Red
    0xFF3B82F6L, // Blue
    0xFF10B981L, // Green
    0xFFF59E0BL, // Yellow
    0xFF8B5CF6L, // Purple
    0xFFF97316L, // Orange
    0xFFFFFFFFL, // White
)

@Composable
fun DrawingToolbar(
    currentTool: DrawTool,
    currentColor: Long,
    currentWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (DrawTool) -> Unit,
    onColorSelected: (Long) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // Compact bar — always visible: current tool indicator, color dot, undo/redo, expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Current tool + quick access to pen/highlighter/eraser
                ToolButtonCompact(Icons.Default.Edit, currentTool == DrawTool.PEN) {
                    onToolSelected(DrawTool.PEN)
                }
                ToolButtonCompact(Icons.Default.Highlight, currentTool == DrawTool.HIGHLIGHTER) {
                    onToolSelected(DrawTool.HIGHLIGHTER)
                }
                ToolButtonCompact(Icons.Default.FormatColorFill, currentTool == DrawTool.ERASER) {
                    onToolSelected(DrawTool.ERASER)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Current color indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(currentColor))
                        .then(
                            if (currentColor == 0xFFFFFFFFL) {
                                Modifier.border(1.dp, Color.LightGray, CircleShape)
                            } else Modifier
                        )
                        .clickable { expanded = !expanded },
                )

                Spacer(modifier = Modifier.weight(1f))

                // Undo / Redo
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo, "Undo",
                        modifier = Modifier.size(20.dp),
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo, "Redo",
                        modifier = Modifier.size(20.dp),
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // Expand/collapse toggle
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Expandable section — shapes, colors, width slider
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    // Shape tools row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolButton(Icons.Default.HorizontalRule, "Line", currentTool == DrawTool.LINE) {
                            onToolSelected(DrawTool.LINE)
                        }
                        ToolButton(Icons.Default.NorthEast, "Arrow", currentTool == DrawTool.ARROW) {
                            onToolSelected(DrawTool.ARROW)
                        }
                        ToolButton(Icons.Default.CropSquare, "Rect", currentTool == DrawTool.RECT) {
                            onToolSelected(DrawTool.RECT)
                        }
                        ToolButton(Icons.Default.Circle, "Circle", currentTool == DrawTool.CIRCLE) {
                            onToolSelected(DrawTool.CIRCLE)
                        }
                        ToolButton(Icons.Default.TextFields, "Text", currentTool == DrawTool.TEXT) {
                            onToolSelected(DrawTool.TEXT)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Color swatches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (color in COLORS) {
                            ColorSwatch(
                                color = color,
                                isSelected = currentColor == color,
                                onClick = { onColorSelected(color) },
                            )
                        }
                    }

                    // Width slider
                    Slider(
                        value = currentWidth,
                        onValueChange = onWidthChanged,
                        valueRange = 1f..12f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButtonCompact(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ColorSwatch(
    color: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else if (color == 0xFFFFFFFFL) {
                    Modifier.border(1.dp, Color.LightGray, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    )
}
