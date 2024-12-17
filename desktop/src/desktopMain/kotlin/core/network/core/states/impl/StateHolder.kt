package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import core.network.node.interfaces.StateHolder
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


