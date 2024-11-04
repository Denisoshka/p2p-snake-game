package core.network.nethandlers

import com.google.protobuf.InvalidProtocolBufferException
import d.zhdanov.ccfit.nsu.core.network.P2PContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.UnicastNetworkHandler
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
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

class UnicastNetHandler<
    MessageT,
    MessageDescriptor,
    InboundMessageTranslator : MessageTranslatorT<MessageT>
    >(
  private val msgUtils: MessageUtilsT<MessageT, MessageDescriptor>,
  private val context: P2PContext<MessageT, InboundMessageTranslator>
) : UnicastNetworkHandler<MessageT> {
  private lateinit var channel: DatagramChannel
  private val group = NioEventLoopGroup()
  private val bootstrap: Bootstrap = Bootstrap().apply {
    group(group)
    channel(NioDatagramChannel::class.java)
    handler(object : ChannelInitializer<DatagramChannel>() {
      override fun initChannel(ch: DatagramChannel) {
        ch.pipeline().addLast(Handler(msgUtils, context))
      }
    })
  }

  override fun launch() {
    channel = bootstrap.bind().sync().channel() as DatagramChannel
  }

  override fun sendMessage(message: MessageT, address: InetSocketAddress) {
    val data = Unpooled.wrappedBuffer(msgUtils.toBytes(message))
    val packet = DatagramPacket(data, address)
    channel.writeAndFlush(packet)
  }

  override fun close() {
    group.shutdownGracefully()
  }

  class Handler<
      MessageT,
      MessageDescriptor,
      InboundMessageTranslator : MessageTranslatorT<MessageT>
      >(
    private val msgUtils: MessageUtilsT<MessageT, MessageDescriptor>,
    private val context: P2PContext<MessageT, InboundMessageTranslator>
  ) : SimpleChannelInboundHandler<DatagramPacket>() {
    override fun channelRead0(
      ctx: ChannelHandlerContext, packet: DatagramPacket
    ) {
      try {
        val message = msgUtils.fromBytes(packet.content().array())
        context.handleUnicastMessage(message, packet.sender())
      } catch (e: IOException) {
        logger.error(e) { "invalid packet from " + packet.sender().toString() }
      }
    }
  }
}