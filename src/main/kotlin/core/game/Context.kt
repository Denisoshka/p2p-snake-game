package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.game.entity.Entity

class Context {
  private val gameObjects: MutableList<Entity> = mutableListOf()

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
  }

}