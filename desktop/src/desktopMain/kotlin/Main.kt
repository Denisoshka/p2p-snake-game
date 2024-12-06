package d.zhdanov.ccfit.nsu

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.view.app

fun main() = application {
  val gameController = remember { GameController() }

  Window(onCloseRequest = ::exitApplication) {
    app(gameController = gameController)
  }
}