package d.zhdanov.ccfit.nsu.core.network.core.exceptions

import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node

class NodeRoleNotSpecifiedForState(state: Node.NodeState) :
  IllegalArgumentException("node role not specified for state $state")