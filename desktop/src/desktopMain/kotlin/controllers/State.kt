package d.zhdanov.ccfit.nsu.states

interface State {
  fun launch()
  fun launch(state: State)
  fun terminate()
}