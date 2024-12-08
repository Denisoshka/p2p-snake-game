package d.zhdanov.ccfit.nsu.core.network.core.exceptions

class IllegalChangeStateAttempt : IllegalArgumentException {
  constructor(
    fromState: String, toState: String
  ) : super("illegal attempt to change state from $fromState to $toState")

  constructor(
    fromState: String, toState: String, msg: String
  ) : super("illegal attempt to change state from $fromState to $toState : $msg")

  constructor(msg: String) : super(msg)
}