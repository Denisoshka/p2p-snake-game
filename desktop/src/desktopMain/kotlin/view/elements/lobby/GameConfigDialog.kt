package d.zhdanov.ccfit.nsu.view.elements.lobby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

@Composable
fun GameConfigDialog(
  onDismiss: () -> Unit,
  onConfirm: (GameConfig) -> Unit
) {
  var width by remember { mutableStateOf(40) }
  var height by remember { mutableStateOf(30) }
  var foodStatic by remember { mutableStateOf(1) }
  var stateDelayMs by remember { mutableStateOf(3000) }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        onConfirm(GameConfig(width, height, foodStatic, stateDelayMs))
      }) {
        Text("Start")
      }
    },
    dismissButton = {
      Button(onClick = onDismiss) {
        Text("Cancel")
      }
    },
    text = {
      Column {
        Text("Configure Your Game", style = MaterialTheme.typography.h6)

        Spacer(Modifier.height(8.dp))
        TextField(
          value = width.toString(),
          onValueChange = { it.toIntOrNull()?.let { newValue -> width = newValue } },
          label = { Text("Width") }
        )

        Spacer(Modifier.height(8.dp))
        TextField(
          value = height.toString(),
          onValueChange = { it.toIntOrNull()?.let { newValue -> height = newValue } },
          label = { Text("Height") }
        )

        Spacer(Modifier.height(8.dp))
        TextField(
          value = foodStatic.toString(),
          onValueChange = { it.toIntOrNull()?.let { newValue -> foodStatic = newValue } },
          label = { Text("Food Static") }
        )

        Spacer(Modifier.height(8.dp))
        TextField(
          value = stateDelayMs.toString(),
          onValueChange = { it.toIntOrNull()?.let { newValue -> stateDelayMs = newValue } },
          label = { Text("State Delay (ms)") }
        )
      }
    }
  )
}
