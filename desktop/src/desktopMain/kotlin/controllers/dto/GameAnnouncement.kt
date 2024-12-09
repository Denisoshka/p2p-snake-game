package d.zhdanov.ccfit.nsu.controllers.dto

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg

data class GameAnnouncement(
  val host: String,
  val port: Int,
  val announcement: AnnouncementMsg,
)
