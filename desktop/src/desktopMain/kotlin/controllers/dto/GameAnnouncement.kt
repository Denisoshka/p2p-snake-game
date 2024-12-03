package d.zhdanov.ccfit.nsu.controllers.dto

data class GameAnnouncement(
  val host: String,
  val port: Int,
  val canJoin: Boolean,
  val gameName: String,
)
