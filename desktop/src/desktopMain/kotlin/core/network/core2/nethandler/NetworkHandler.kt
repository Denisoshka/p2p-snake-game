package d.zhdanov.ccfit.nsu.core.network.core2.nethandler

interface NetworkHandler : AutoCloseable {
  fun launch()
}