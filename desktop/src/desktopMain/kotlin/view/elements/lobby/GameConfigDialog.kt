package d.zhdanov.ccfit.nsu.view.elements.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
@Composable
fun ConfigDialog(
  onDismiss: () -> Unit,
  onSubmit: (InternalGameConfig) -> Unit
) {
  var playerName by remember { mutableStateOf("") }
  var gameName by remember { mutableStateOf("") }
  var width by remember { mutableStateOf("40") }
  var height by remember { mutableStateOf("30") }
  var foodStatic by remember { mutableStateOf("1") }
  var stateDelayMs by remember { mutableStateOf("3000") }
  
  AlertDialog(
    onDismissRequest = { onDismiss() },
    title = { Text("Настройка игры") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
          value = playerName,
          onValueChange = { playerName = it },
          label = { Text("Имя игрока") },
        )
        TextField(
          value = gameName,
          onValueChange = { gameName = it },
          label = { Text("Название игры") },
        )
        TextField(
          value = width,
          onValueChange = { width = it },
          label = { Text("Ширина поля") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextField(
          value = height,
          onValueChange = { height = it },
          label = { Text("Высота поля") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextField(
          value = foodStatic,
          onValueChange = { foodStatic = it },
          label = { Text("Количество еды") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextField(
          value = stateDelayMs,
          onValueChange = { stateDelayMs = it },
          label = { Text("Задержка состояния (мс)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
      }
    },
    confirmButton = {
      Button(onClick = {
        val config = GameConfig(
          width = width.toIntOrNull() ?: 40,
          height = height.toIntOrNull() ?: 30,
          foodStatic = foodStatic.toIntOrNull() ?: 1,
          stateDelayMs = stateDelayMs.toIntOrNull() ?: 3000
        )
        val internalConfig = InternalGameConfig(
          playerName = playerName,
          gameName = gameName,
          gameSettings = config
        )
        onSubmit(internalConfig)
      }) {
        Text("Начать игру")
      }
    },
    dismissButton = {
      Button(onClick = { onDismiss() }) {
        Text("Отмена")
      }
    }
  )
}
