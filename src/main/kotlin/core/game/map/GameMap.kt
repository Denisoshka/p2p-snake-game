package d.zhdanov.ccfit.nsu.core.game.map

import d.zhdanov.ccfit.nsu.core.game.entity.Entity

class GameMap(val width: Int, val height: Int) {
  private val objects = mutableSetOf<MapPoint>()

  private fun isSquareFree(point: MapPoint): Boolean {
    val x = point.x
    val y = point.y
    var isFree = true
    outer@ for (i in -2..2) {
      point.x = x + i
      for (j in -2..2) {
        point.y = y + j
        fixPoint(point)
        if (isOccupied(point)) {
          isFree = false
          break@outer
        }
      }
    }
    point.x = x
    point.y = y
    return isFree
  }

  fun movePoint(point: MapPoint, newX: Int, newY: Int) {
    objects.remove(point)
    point.x = newX
    point.y = newY
    fixPoint(point)
    objects.add(point)
  }

  fun findFreeSquare(): MapPoint? {
    val center = MapPoint(0, 0)
    for (x in 0 until width) {
      center.x = x;
      for (y in 0 until height) {
        center.y = y
        if (isSquareFree(center)) {
          return center
        }
      }
    }
    return null
  }

  fun addEntity(entity: Entity) {
    entity.getHitBox().forEach { point -> fixPoint(point); objects.add(point) }
  }

  fun removeEntity(entity: Entity) {
    entity.getHitBox().forEach(objects::remove)
  }

  private fun isOccupied(point: MapPoint): Boolean {
    return objects.contains(point)
  }

  private fun fixPoint(point: MapPoint) {
    point.x %= width
    point.y %= height
  }
}
