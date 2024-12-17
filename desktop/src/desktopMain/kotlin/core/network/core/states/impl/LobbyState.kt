package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.lobby.impl.NetNode
import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.ActiveStateUtils
import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.PassiveStateUtils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.node.connected.LobbyState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(LobbyState::class.java.name)

