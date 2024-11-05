package d.zhdanov.ccfit.nsu.core.network.exceptions

import d.zhdanov.ccfit.nsu.core.network.NodeState


class IllegalUnacknowledgedMessagesGetAttempt(state: NodeState) :
  RuntimeException(state.toString()) {
}