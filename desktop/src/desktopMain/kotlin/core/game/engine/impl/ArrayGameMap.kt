package d.zhdanov.ccfit.nsu.core.game.engine.impl

import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import kotlin.random.Random

class ArrayGameMap(override var width: Int, override var height: Int) :
  GameMap {
  
  private var field: Array<Array<GameType>> = Array(height) {
    Array(width) { GameType.None }
  }
  
  override fun setCell(x: Int, y: Int, value: GameType) {
    val wrappedX = getFixedX(x)
    val wrappedY = getFixedY(y)
    
    field[wrappedY][wrappedX] = value
  }
  
  
  override fun updateEntitiesInfo(entities: List<Entity>) {
    field.forEach { it.fill(GameType.None) }
    entities.forEach { entity ->
      entity.hitBoxTravel { x, y -> field[x][y] = entity.type }
      field[entity.head.x][entity.head.y] = entity.type
    }
  }
  
  override fun getCell(x: Int, y: Int): GameType {
    val wrappedX = getFixedX(x)
    val wrappedY = getFixedY(y)
    
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
  
  override fun findFreeSquare(
    size: Int, occupiedBy: GameType?
  ): Pair<Int, Int>? {
    val startX = Random.nextInt(0, height)
    val startY = Random.nextInt(0, width)
    
    for(y in 0 until height) {
      for(x in 0 until width) {
        val reqx = x + startX
        val reqy = y + startY
        if(isSquareFree(reqx, reqy, size)) {
          occupiedBy?.let { setCell(reqx, reqy, occupiedBy) }
          return x to y
        }
      }
    }
    return null
  }
  
  override fun findFreeCells(
    cellsQ: Int,
    occupiedBy: GameType?
  ): List<GameMap.Cell>? {
    val freeCells = mutableListOf<GameMap.Cell>()
    var attempts = 0
    
    while(attempts < 20) {
      val startX = Random.nextInt(0, width)
      val startY = Random.nextInt(0, height)
      
      // Ищем блок клеток
      var foundCells = 0
      for(dx in 0 until width) {
        for(dy in 0 until height) {
          val x = (startX + dx) % width
          val y = (startY + dy) % height
          
          if(isSquareFree(x, y, 1)) {
            foundCells++
            freeCells.add(GameMap.Cell(x, y))
            
            if(foundCells == cellsQ) {
              occupiedBy?.let { setCell(x, y, occupiedBy) }
              return freeCells
            }
          }
        }
      }
      
      attempts++
    }
    return null
  }
  
  private fun isSquareFree(x: Int, y: Int, size: Int = 5): Boolean {
    for(yi in y until y + size) {
      for(xi in x until x + size) {
        val wrappedYi = getFixedY(yi)
        val wrappedXi = getFixedY(xi)
        if(field[wrappedYi][wrappedXi] != GameType.Snake) {
          return false
        }
      }
    }
    return true
  }
}
