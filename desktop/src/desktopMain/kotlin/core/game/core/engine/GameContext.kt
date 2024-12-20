package d.zhdanov.ccfit.nsu.core.game.core.engine

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.core.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

interface GameContext {
  val gameConfig: GameConfig
  val gameMap: GameMap
  val entities: MutableList<Entity>
  val sideEffectEntity: MutableList<Entity>
  
  fun initGame(
    config: GameConfig, foodInfo: List<SnakesProto.GameState.Coord>?,
  )
  
  fun addSnake(id: Int, x: Int, y: Int): SnakeEntity
  fun addSnake(
    snakeInfo: SnakesProto.GameState.Snake, score: Int = 0
  ): SnakeEntity
  
  fun addSnake(snakeEntity: SnakeEntity): SnakeEntity
  fun countNextStep()
}