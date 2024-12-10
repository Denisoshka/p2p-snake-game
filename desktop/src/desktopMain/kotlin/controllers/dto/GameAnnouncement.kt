package d.zhdanov.ccfit.nsu.controllers.dto

import d.zhdanov.ccfit.nsu.SnakesProto

data class GameAnnouncement(
  val host: String,
  val port: Int,
  val announcement: SnakesProto.GameAnnouncement,
)
