package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

interface GameContext {
  fun launch()
  fun shutdown()

  fun addSideEntity(entity: Entity)

  fun initGame(
    config: GameConfig,
    gamePlayerInfo: GamePlayerInfo
  ): List<ActiveEntity>

  fun initGame(
    config: GameConfig, playerInfo: GamePlayerInfo, state: SnakesProto.GameMessage.StateMsg?
  ): List<ActiveEntity>
}