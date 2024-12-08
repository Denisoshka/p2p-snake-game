package d.zhdanov.ccfit.nsu.core.network.nethandlers.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.nethandlers.NetworkHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.io.IOException

private val logger = KotlinLogging.logger {}

class MulticastNetHandler(
  private val config: NetConfig,
  context: NetworkController,
) : NetworkHandler {
  private var group: NioEventLoopGroup? = null
  private var bootstrap: Bootstrap = Bootstrap()

  init {
    bootstrap.apply {
      channel(NioDatagramChannel::class.java)
      handler(object : ChannelInitializer<NioDatagramChannel>() {
        override fun initChannel(ch: NioDatagramChannel) {
          ch.pipeline().addLast(MulticastHandler(context))
        }
      })
    }
  }

  override fun launch() {
    group = NioEventLoopGroup()
    bootstrap.group(group)
    val channel = bootstrap.bind(config.localAddr).sync().channel()
    (channel as NioDatagramChannel).joinGroup(
      config.destAddr, config.netInterface
    )
  }

  override fun close() {
    group?.shutdownGracefully()
  }

  class MulticastHandler(
    private val context: NetworkController
  ) : SimpleChannelInboundHandler<DatagramPacket>() {
    override fun channelRead0(
      ctx: ChannelHandlerContext, packet: DatagramPacket
    ) {
      try {
        val msg = SnakesProto.GameMessage.parseFrom(packet.content().array())
        context.handleMulticastMessage(msg, packet.sender())
      } catch(e: IOException) {
        logger.error(e) { "invalid packet from ${packet.sender()}" }
      }
    }
  }
}