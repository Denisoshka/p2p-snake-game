package d.zhdanov.ccfit.nsu.view.elements.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GameHeader(
  gameName: String, onExit: () -> Unit, modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(gameName, style = MaterialTheme.typography.h4)
    Button(onClick = onExit) {
      Text("Exit Game")
    }
  }
}
