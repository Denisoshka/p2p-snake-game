package d.zhdanov.ccfit.nsu.core.network.exceptions

import d.zhdanov.ccfit.nsu.core.network.Node

class IllegalUnacknowledgedMessagesGetAttempt(state: Node.NodeState) :
  RuntimeException(state.toString()) {
}