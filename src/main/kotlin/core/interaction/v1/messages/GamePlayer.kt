package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

class GamePlayer(
  var name: String,
  var id: Int,
  var ipAddress: String?,
  var port: Int?,
  var nodeRole: NodeRole,
  var playerType: PlayerType,
  var score: Int
)