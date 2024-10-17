package d.zhdanov.ccfit.nsu.core.messages

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