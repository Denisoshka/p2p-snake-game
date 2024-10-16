package d.zhdanov.ccfit.nsu.core.network.messages

enum class MessageType {
    PingMsg,
    SteerMsg,
    AckMsg,
    StateMsg,
    AnnouncementMsg,
    JoinMsg,
    ErrorMsg,
    RoleChangeMsg,
    DiscoverMsg;
}