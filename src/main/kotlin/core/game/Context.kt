package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.game.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.map.GameMap
import d.zhdanov.ccfit.nsu.core.messages.Direction

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

  fun updateSnake(snakeId: Int, direction: Direction) {
    snakes[snakeId]?.apply { changeState(direction) }
  }

  fun addNewSnake(snakeId: Int) {
  }

  fun getGameState() {
  }

  private fun onSnakeDead() {
  }
}