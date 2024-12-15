package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType

interface GameMap {
  val width: Int
  val height: Int
  fun getFixedX(x: Int): Int
  fun getFixedY(y: Int): Int
  fun findFreeSquare(size: Int = 5): Pair<Int, Int>?
  fun setCell(x: Int, y: Int, value: GameType)
  fun getCell(x: Int, y: Int): GameType
  
  data class Cell(var x: Int, var y: Int)
}