package d.zhdanov.ccfit.nsu.core.game.engine.impl

import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType

class ArrayGameMap(override var width: Int, override var height: Int) :
  GameMap {
  
  private var field: Array<Array<GameType>> = Array(height) {
    Array(width) { GameType.None }
  }
  
  override fun setCell(x: Int, y: Int, value: GameType) {
    val wrappedX = wrapX(x)
    val wrappedY = wrapY(y)
    
    field[wrappedY][wrappedX] = value
  }
  
  override fun getCell(x: Int, y: Int): GameType {
    val wrappedX = wrapX(x)
    val wrappedY = wrapY(y)
    
    return field[wrappedY][wrappedX]
  }
  
  private fun wrapX(x: Int): Int {
    return x % width
  }
  
  private fun wrapY(y: Int): Int {
    return y % height
  }
  
  override fun getFixedX(x: Int): Int {
    return (x + width).mod(width)
  }
  
  override fun getFixedY(y: Int): Int {
    return (y + height).mod(height)
  }
  
  override fun findFreeSquare(size: Int): Pair<Int, Int>? {
    for(y in 0 until height) {
      for(x in 0 until width) {
        if(isSquareFree(x, y, size)) {
          return x to y
        }
      }
    }
    return null
  }
  
  private fun isSquareFree(x: Int, y: Int, size: Int = 5): Boolean {
    for(yi in y until y + size) {
      for(xi in x until x + size) {
        val wrappedYi = wrapY(yi)
        val wrappedXi = wrapX(xi)
        if(field[wrappedYi][wrappedXi] != GameType.Snake) {
          return false
        }
      }
    }
    return true
  }
}
