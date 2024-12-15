package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine

interface Entity {
  val hitBox: List<GameMap.Cell>
  val head: GameMap.Cell
  val type: GameType
  val alive: Boolean
  fun update(context: GameEngine, sideEffects: List<Entity>)
  fun hitBoxTravel(function: (x: Int, y: Int) -> Unit)
  fun checkCollisions(entity: Entity, context: GameEngine)
  
  //  fun restoreState(offsets: List<SnakesProto.GameState.Coord>)
  fun atDead(context: GameEngine)
  fun shootState(state: SnakesProto.GameState.Builder)
}