package d.zhdanov.ccfit.nsu.core.game.engine.map

import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType

class EntityOnMapInfo(
  var x: Int,
  var y: Int,
  var gameType: GameType = GameType.None
) {
  var ownerId: Int = 0

  override fun equals(other: Any?): Boolean {
    if(this === other) return true
    if(javaClass != other?.javaClass) return false

    other as EntityOnMapInfo

    if(x != other.x) return false
    if(y != other.y) return false
    if(gameType != other.gameType) return false
    if(ownerId != other.ownerId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = x
    result = 31 * result + y
    result = 31 * result + gameType.hashCode()
    result = 31 * result + ownerId
    return result
  }
}