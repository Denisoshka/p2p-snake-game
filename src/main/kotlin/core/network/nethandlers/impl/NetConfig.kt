package d.zhdanov.ccfit.nsu.core.network.nethandlers.impl

import java.net.InetSocketAddress
import java.net.NetworkInterface

data class NetConfig(
  val destAddr : InetSocketAddress?,
  val localAddr : InetSocketAddress?,
  val netInterface: NetworkInterface?,
)