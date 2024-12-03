package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface GameContext {
  fun shutdownNow()
  fun launch()
  fun addSideEntity(entity: Entity)
  fun initGameFromState(
    config: InternalGameConfig, state: StateMsg
  ): List<ActiveEntity>

  fun initNewGame(config: InternalGameConfig): List<ActiveEntity>
}