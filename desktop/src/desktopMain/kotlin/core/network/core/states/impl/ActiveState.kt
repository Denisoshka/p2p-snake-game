package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.states.abstr.ConnectedActor
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.node.connected.ActiveState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

