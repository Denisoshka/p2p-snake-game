package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalNodeRoleException
import dzhdanov.ccfit.nsu.ru.SnakesProto

enum class NodeRole {
    NORMAL,
    MASTER,
    DEPUTY,
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