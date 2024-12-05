package d.zhdanov.ccfit.nsu

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.view.AppContent

fun main() = application {
  fun main() = application {
    val gameController = remember { GameController() }

    Window(onCloseRequest = ::exitApplication) {
      AppContent(gameController = gameController)
    }
  }
}