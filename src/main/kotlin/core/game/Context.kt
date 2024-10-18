package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.game.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.map.GameMap
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.SteerMsg

class Context {
  private val gameObjects: MutableList<Entity> = mutableListOf()
  private val snakes: Map<Int, Snake> = HashMap()

  val map: GameMap = TODO()

  fun update() {
    for (entity in gameObjects) {
      entity.update(this)
    }
  }

  fun checkCollision() {
    for (x in gameObjects) {
      for (y in gameObjects) {
        x.checkCollisions(y, this);
      }
    }
    gameObjects.removeIf { ent -> ent.isDead() }
  }

  fun takeSnapshot(): StateMsg {
    val snakeSnapshot = mutableListOf<Snake>()
    val foodSnapshot = mutableListOf<Snake>
    for (entity in gameObjects) {
      when (entity.getType()) {
        GameType.Snake -> {

        }

        GameType.Apple -> {

        }

        else -> {}
      }
    }
  }

  fun getSnapshot() {}

  fun updateSnake(snakeId: Int, steerMsg: SteerMsg) {
    snakes[snakeId]?.apply { changeState(steerMsg.direction) }
  }

  fun addNewSnake(snakeId: Int) {
  }

  fun getGameState() {
  }

  private fun onSnakeDead() {
  }
}