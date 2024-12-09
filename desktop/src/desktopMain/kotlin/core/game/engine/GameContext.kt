package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface GameContext {
  fun launch()
  fun shutdown()

  fun addSideEntity(entity: Entity)

  fun initGame(
    config: GameConfig, playerInfo: GamePlayerInfo
  ): List<ActiveEntity>

  fun initGameFromState(
    config: GameConfig, state: StateMsg, playerInfo: GamePlayer
  ): List<ActiveEntity>
}