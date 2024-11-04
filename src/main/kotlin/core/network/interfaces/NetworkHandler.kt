package d.zhdanov.ccfit.nsu.core.network.interfaces

interface NetworkHandler : AutoCloseable {
  fun launch()
}