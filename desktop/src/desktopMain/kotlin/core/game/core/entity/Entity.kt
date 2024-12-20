package d.zhdanov.ccfit.nsu.core.game.core.entity

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameMap

interface Entity  {
  val hitBox: Iterable<Pair<Int, Int>>
  val head: GameMap.Cell
  val type: GameType
  val alive: Boolean
  val gameContext: GameContext
  fun update()
  fun checkCollisions(entity: Entity)
  fun atDead()
  fun shootState(state: SnakesProto.GameState.Builder)
}