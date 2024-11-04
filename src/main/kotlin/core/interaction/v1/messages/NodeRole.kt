package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalNodeRoleException

/**
 * Role of a node in the topology of connections within the game.
 */
enum class NodeRole {
  /**
   * A regular node, a leaf in a star topology.
   */
  NORMAL,

  /**
   * The main node, the center of a star topology.
   */
  MASTER,

  /**
   * The deputy node, a backup for the main node.
   */
  DEPUTY,

  /**
   * A spectator node, similar to NORMAL, but without an ALIVE snake;
   * only receives status updates.
   */
  VIEWER;

  @Throws(IllegalNodeRoleException::class)
  fun fromProto(role: SnakesProto.NodeRole): NodeRole {
    return when (role) {
      SnakesProto.NodeRole.NORMAL -> NORMAL
      SnakesProto.NodeRole.MASTER -> MASTER
      SnakesProto.NodeRole.DEPUTY -> DEPUTY
      SnakesProto.NodeRole.VIEWER -> VIEWER
      else -> throw IllegalNodeRoleException(role.toString())
    }
  }
}