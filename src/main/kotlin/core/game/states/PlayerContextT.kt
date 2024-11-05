package d.zhdanov.ccfit.nsu.core.game.states

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake

interface PlayerContextT {
  val playerId: Int
  val snake: Snake
}