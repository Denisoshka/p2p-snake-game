package d.zhdanov.ccfit.nsu.core.network.nethandlers.impl

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.controller.NetworkController
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
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

class MulticastNetHandler<MessageT, InboundMessageTranslator :
MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val config: NetConfig,
  context: NetworkController<MessageT, InboundMessageTranslator, Payload>,
) : NetworkHandler<MessageT, InboundMessageTranslator, Payload> {
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

  class MulticastHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    private val context: NetworkController<MessageT, InboundMessageTranslator, Payload>
  ) : SimpleChannelInboundHandler<DatagramPacket>() {
    private val msgUtils = context.messageUtils
    override fun channelRead0(
      ctx: ChannelHandlerContext, packet: DatagramPacket
    ) {
      try {
        val message = msgUtils.fromBytes(packet.content().array())
        context.handleMulticastMessage(message, packet.sender())
      } catch(e: IOException) {
        logger.error(e) { "invalid packet from " + packet.sender().toString() }
      }
    }
  }
}