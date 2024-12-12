package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT

interface GameStateT {
   fun handleNodeDetach(node: ClusterNodeT<Node.MsgInfo>, changeAccessToken: Any)
}