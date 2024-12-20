package d.zhdanov.ccfit.nsu.core.game.core.engine.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.core.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.core.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.core.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import io.github.oshai.kotlinlogging.KotlinLogging

private val Logger = KotlinLogging.logger(GameContextImpl::class.java.name)

class GameContextImpl(
  override val gameConfig: GameConfig,
) : GameContext {
  override val gameMap: GameMap = ArrayGameMap(
    gameConfig.width, gameConfig.height
  )
  
  override val sideEffectEntity: MutableList<Entity> = ArrayList()
  override val entities: MutableList<Entity> = ArrayList()
  
  override fun countNextStep() {
    update()
    checkCollision()
    
    postProcess()
  }
  
  private fun update() {
    for(entity in entities) {
      entity.update()
    }
  }
  
  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        x.checkCollisions(y);
      }
    }
    
    val entrIt = entities.iterator()
    while(entrIt.hasNext()) {
      val ent = entrIt.next()
      if(!ent.alive) {
        entrIt.remove()
        ent.atDead()
      }
    }
  }
  
  private fun postProcess() {
    sideEffectEntity()
    checkUpdateApples()
    addEntitiesFootPrint()
  }
  
  private fun addEntitiesFootPrint() {
    entities.forEach(this::addFootPrint)
  }
  
  private fun addFootPrint(entity: Entity) {
    entity.hitBox.forEach { (x, y) ->
      gameMap.setCell(x, y, entity.type)
    }
  }
  
  override fun addSnake(id: Int, x: Int, y: Int): SnakeEntity {
    return SnakeEntity(id, this, x, y).let { addSnake(it) }
  }
  
  override fun addSnake(snakeEntity: SnakeEntity): SnakeEntity {
    entities.add(snakeEntity)
    addFootPrint(snakeEntity)
    return snakeEntity
  }
  
  override fun initGame(
    config: GameConfig,
    foodInfo: List<SnakesProto.GameState.Coord>?,
  ) {
    foodInfo?.forEach {
      restoreApple(it)
    }
  }
  
  override fun addSnake(
    snakeInfo: SnakesProto.GameState.Snake, score: Int
  ): SnakeEntity {
    SnakeEntity(snakeInfo, score, this).let { addSnake(it) }
  }
  
  
  private fun restoreApple(foodInfo: SnakesProto.GameState.Coord): AppleEntity {
    val apple = AppleEntity(foodInfo.x, foodInfo.y, this)
    entities.add(apple);
    return apple
  }
  
  private fun checkUpdateApples() {
    val applesQ = entities.count { it.type == GameType.Apple }
    val playersQ = entities.count { it.type == GameType.Snake && it.alive }
    val targetQ = gameConfig.foodStatic + playersQ
    val diff = targetQ - applesQ
    if(diff > 0) {
      gameMap.findFreeCells(diff, GameType.Apple)?.forEach {
        sideEffectEntity.add(AppleEntity(it.x, it.y, this))
      }
    }
  }
  
  private fun sideEffectEntity() {
    entities.addAll(sideEffectEntity)
    sideEffectEntity.clear()
  }
}