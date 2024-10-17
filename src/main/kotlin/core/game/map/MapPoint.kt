package d.zhdanov.ccfit.nsu.core.game.map

import d.zhdanov.ccfit.nsu.core.game.entity.GameType

class MapPoint(
  var x: Int,
  var y: Int,
  var gameType: GameType = GameType.Entity
) {
  var ownerId: Int = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MapPoint

    if (x != other.x) return false
    if (y != other.y) return false

    return true
  }

  override fun hashCode(): Int {
    var result = x
    result = 31 * result + y
    return result
  }
}
