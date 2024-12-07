package d.zhdanov.ccfit.nsu.core.network.core.exceptions

class IllegalChangeStateAttempt(fromState: String, toState: String) :
  IllegalArgumentException(
    "illegal attempt to change state from $fromState to $toState"
  )