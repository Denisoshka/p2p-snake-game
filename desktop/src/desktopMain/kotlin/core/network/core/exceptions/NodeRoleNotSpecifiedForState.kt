package d.zhdanov.ccfit.nsu.core.network.core.exceptions

import core.network.core.connection.Node

class NodeRoleNotSpecifiedForState(state: Node.NodeState) :
  IllegalArgumentException("node role not specified for state $state")