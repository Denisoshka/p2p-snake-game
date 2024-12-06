package d.zhdanov.ccfit.nsu.view.elements.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer

@Composable
fun PlayerList(players: List<GamePlayer>, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text("Connected Players", style = MaterialTheme.typography.h6)
    LazyColumn {
      items(players) { player ->
        Text("Player: ${player.name} (Score: ${player.score})", style = MaterialTheme.typography.body1)
      }
    }
  }
}
