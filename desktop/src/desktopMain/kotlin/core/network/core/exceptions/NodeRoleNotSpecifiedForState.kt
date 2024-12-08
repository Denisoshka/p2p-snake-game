package d.zhdanov.ccfit.nsu.core.network.core.exceptions

import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT

class NodeRoleNotSpecifiedForState(state: NodeT.NodeState) :
  IllegalArgumentException("node role not specified for state $state")