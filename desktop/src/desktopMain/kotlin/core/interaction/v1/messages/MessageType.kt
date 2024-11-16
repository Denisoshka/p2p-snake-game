package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

enum class MessageType {
    PingMsg,
    SteerMsg,
    AckMsg,
    StateMsg,
    AnnouncementMsg,
    JoinMsg,
    ErrorMsg,
    RoleChangeMsg,
    DiscoverMsg,
    UnrecognisedMsg;
}