package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.util.concurrent.Executors
import kotlin.random.Random

class GameEngine(
  private val internalGameConfig: InternalGameConfig,
  private val gameController: GameController,
  private var stateOrder: Int = 0
) {
  private val entities: MutableList<Entity> = mutableListOf()
  private val sideEffectEntity: MutableList<Entity> = mutableListOf()
  private val players: MutableMap<Int, Player> = HashMap()
  private val executor = Executors.newSingleThreadExecutor()
  private val delayMillis = 1000L / internalGameConfig.updatesPerSecond
  private val directions = Direction.entries.toTypedArray()
  fun addSideEffect(entity: Entity) {
    sideEffectEntity.add(entity)
  }

  fun addEntity(entity: Entity) {
    map.addEntity(entity)
    entities.add(entity)
  }

  fun addPlayer(player: Player) {
    players[player.snakeEnt.id] = player
    map.addEntity(player)
  }

  fun spawnSnake(id: Int, direction: Direction? = null): SnakeEnt? {
    val coords = map.findFreeSquare() ?: return null
    val dir = direction ?: directions[Random.nextInt(directions.size)]

    return SnakeEnt(dir, id).apply {
      hitBox.add(EntityOnMapInfo(coords.x, coords.y))
      hitBox.add(EntityOnMapInfo(coords.x + dir.dx, coords.y + dir.dy))
    }
  }

  val map: GameMap = TODO()

  private fun gameLoop() {
    while(Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()

      update()
      checkCollision()
      shootState()

      val endTime = System.currentTimeMillis()
      val timeTaken = endTime - startTime
      val sleepTime = delayMillis - timeTaken

      if(sleepTime > 0) Thread.sleep(sleepTime)
    }

    TODO("нужно сделать возможность прервать игру?")
  }

  private fun update() {
    updatePreprocess()

    for(entity in entities) {
      entity.update(this)
    }
    for((_, snake) in players) {
      snake.snakeEnt.update(this)
    }
  }

  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        if(x != y) x.checkCollisions(y, this);
      }
      for((_, snake) in players) {
        snake.snakeEnt.checkCollisions(x, this)
        x.checkCollisions(snake.snakeEnt, this)
      }
    }


    val entrIt = entities.iterator()
    while(entrIt.hasNext()) {
      val ent = entrIt.next()
      if(!ent.alive) {
        entrIt.remove()
        ent.atDead(this)
      }
    }
    val plIt = players.iterator()
    while(plIt.hasNext()) {
      val (_, player) = plIt.next()
      if(!player.alive) {
        plIt.remove()
        player.atDead(this)
      }
    }
  }

  private fun shootState() {
    val snakeSnapshot =
      ArrayList<d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake>(players.size)
    val foodSnapshot = ArrayList<Coord>(entities.size)
    val playersSnapshot = ArrayList<GamePlayer>(players.size)
    val nextOrder = ++stateOrder;
    val state = StateMsg(
      nextOrder, snakeSnapshot, foodSnapshot, playersSnapshot
    )
    for(entity in entities) {
      entity.shootState(this, state)
    }
    for((_, player) in players) {
      player.shootState(this, state)
    }
    gameController.submitGameState(state)
  }

  private fun updatePreprocess() {
    entities.addAll(sideEffectEntity)
    for(se in sideEffectEntity) map.addEntity(se)
    sideEffectEntity.clear()
  }

  fun terminate() {
    executor.shutdownNow()
  }

  fun launch() {
    executor.submit {
      gameLoop()
    }
  }
}