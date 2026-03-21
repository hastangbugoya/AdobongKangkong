package com.example.adobongkangkong.ui.common.editoraction

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.example.adobongkangkong.R

@Composable
fun EditorActionMenu(
    showDelete: Boolean,
    onDelete: () -> Unit,
    deleteLabel: String = "Delete"
) {
    if (!showDelete) return

    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            painter = painterResource(R.drawable.settings),
            contentDescription = "Actions"
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(deleteLabel) },
            onClick = {
                expanded = false
                onDelete()
            }
        )
    }
}
