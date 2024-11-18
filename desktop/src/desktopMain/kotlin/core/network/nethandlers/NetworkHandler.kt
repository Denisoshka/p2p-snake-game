package d.zhdanov.ccfit.nsu.core.network.nethandlers

interface NetworkHandler : AutoCloseable {
  fun launch()
}