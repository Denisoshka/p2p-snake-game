package d.zhdanov.ccfit.nsu.core.interaction.messages

class GamePlayer(
  var name: String,
  var id: Int,
  var nodeRole: NodeRole,
  var playerType: PlayerType,
  var score: Int
) {
  var ipAddress: String? = null
  var port: Int? = null
}