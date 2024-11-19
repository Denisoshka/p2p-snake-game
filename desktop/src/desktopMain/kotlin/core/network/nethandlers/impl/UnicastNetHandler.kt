package d.zhdanov.ccfit.nsu.core.network.nethandlers.impl

import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.nethandlers.UnicastNetworkHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.io.IOException
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}

class UnicastNetHandler(
  context: NetworkController,
) : UnicastNetworkHandler {
  private lateinit var channel: DatagramChannel
  private var group: NioEventLoopGroup? = null
  private val bootstrap: Bootstrap = Bootstrap()
  private val msgUtils = context.messageUtils

  init {
    bootstrap.apply {
      group(group)
      channel(NioDatagramChannel::class.java)
      handler(object : ChannelInitializer<DatagramChannel>() {
        override fun initChannel(ch: DatagramChannel) {
          ch.pipeline().addLast(Handler(context))
        }
      })
    }
  }

  override fun launch() {
    group = NioEventLoopGroup()
    bootstrap.group(group)
    channel = bootstrap.bind().sync().channel() as DatagramChannel
  }

  override fun close() {
    group?.shutdownGracefully()
  }

  override fun sendUnicastMessage(
    message: MessageT, address: InetSocketAddress
  ) {
    val data = Unpooled.wrappedBuffer(msgUtils.toBytes(message))
    val packet = DatagramPacket(data, address)
    channel.writeAndFlush(packet)
  }

  class Handler(
    private val context: NetworkController
  ) : SimpleChannelInboundHandler<DatagramPacket>() {
    private val msgUtils = context.messageUtils

    override fun channelRead0(
      ctx: ChannelHandlerContext, packet: DatagramPacket
    ) {
      try {
        val message = msgUtils.fromBytes(packet.content().array())
        context.handleUnicastMessage(message, packet.sender())
      } catch(e: IOException) {
        logger.error(e) { "invalid packet from " + packet.sender().toString() }
      }
    }
  }
}