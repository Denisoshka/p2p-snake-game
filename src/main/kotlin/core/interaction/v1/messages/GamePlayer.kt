package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

class GamePlayer(
  var name: String,
  var id: Int,
  var nodeRole: d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole,
  var playerType: d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType,
  var score: Int
) {
  var ipAddress: String? = null
  var port: Int? = null
}