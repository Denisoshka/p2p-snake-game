package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType

interface GameMap {
  var width: Int
  var height: Int
  fun findFreeSquare(size: Int = 5): Pair<Int, Int>?
  fun resize(newWidth: Int, newHeight: Int)
  fun setCell(x: Int, y: Int, value: GameType)
  fun getCell(x: Int, y: Int): GameType

  data class Cell(var x: Int, var y: Int)
}