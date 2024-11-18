package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

data class InternalGameConfig(
  val maxSnakesQuantityAddedPerUpdate: Int,
  val playerName: String,
  val gameSettings: GameConfig
)
