package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole

/**
 * Represents a message about a role change in the network.
 *
 * This message is used to notify nodes about changes in their roles or the roles of others
 * within the network. The message can indicate various role transitions:
 *
 * 1. From a deputy to other players, informing them that it is time to
 * consider the deputy as the master (when [senderRole] = [NodeRole.MASTER]).
 * 2. From a player who is consciously exiting the game
 * (when [senderRole] = [NodeRole.VIEWER]).
 * 3. From the master to a node that is considered dead (when [receiverRole]
 * = [NodeRole.VIEWER]).
 * 4. Either in combination with points 1 or 2, or separately, this message can indicate the assignment
 * of someone as a deputy (when [receiverRole] = [NodeRole.DEPUTY]).
 * 5. In combination with point 2, from the master to the deputy, indicating that the deputy is now the master
 * (when [receiverRole] = [NodeRole.MASTER]).
 *
 * @param senderRole The current role of the sender node.
 * @param receiverRole The role that the receiver node will take on after the role change.
 */
class RoleChangeMsg(
  val senderRole: NodeRole? = null,
  val receiverRole: NodeRole? = null
) : Msg(MessageType.RoleChangeMsg)