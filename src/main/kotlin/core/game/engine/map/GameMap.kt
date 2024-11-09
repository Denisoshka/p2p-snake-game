package d.zhdanov.ccfit.nsu.core.game.engine.map

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity

class GameMap(val width: Int, val height: Int) {
  private val mapContainer = mutableSetOf<EntityOnMapInfo>()

  private fun isSquareFree(point: EntityOnMapInfo): Boolean {
    val x = point.x
    val y = point.y
    var isFree = true
    outer@ for(i in -2..2) {
      point.x = x + i
      for(j in -2..2) {
        point.y = y + j
        fixPoint(point)
        if(isOccupied(point)) {
          isFree = false
          break@outer
        }
      }
    }
    point.x = x
    point.y = y
    return isFree
  }

  fun movePoint(point: EntityOnMapInfo, newX: Int, newY: Int) {
    mapContainer.remove(point)
    point.x = newX
    point.y = newY
    fixPoint(point)
    mapContainer.add(point)
  }

  fun findFreeSquare(): EntityOnMapInfo? {
    val center = EntityOnMapInfo(0, 0)
    for(x in 0 until width) {
      center.x = x;
      for(y in 0 until height) {
        center.y = y
        if(isSquareFree(center)) {
          return center
        }
      }
    }
    return null
  }

  fun addEntity(entity: Entity) {
    entity.hitBox.forEach { point -> fixPoint(point); mapContainer.add(point) }
  }

  fun removeEntity(entity: Entity) {
    entity.hitBox.forEach(mapContainer::remove)
  }

  private fun isOccupied(point: EntityOnMapInfo): Boolean {
    return mapContainer.contains(point)
  }

  private fun fixPoint(point: EntityOnMapInfo) {
    point.x %= width
    point.y %= height
  }
}