package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun QuantityBadge(
    quantity: Int,
    isSelected: Boolean = false
) {
    val backgroundColor = MaterialTheme.colorScheme.secondary
    val contentColor = MaterialTheme.colorScheme.onSecondary

    Surface(
        shape = CircleShape,
        color = if (isSelected) {
            backgroundColor.copy(alpha = 0.9f)
        } else {
            backgroundColor
        },
        modifier = Modifier.clip(CircleShape)
    ) {
        Text(
            text = quantity.toString(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
