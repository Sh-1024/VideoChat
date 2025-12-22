package org.example.videochat.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum MessageType {
    @JsonProperty("register")
    REGISTER,

    @JsonProperty("reconnect")
    RECONNECT,

    @JsonProperty("offer")
    OFFER,

    @JsonProperty("answer")
    ANSWER,

    @JsonProperty("network_path")
    NETWORK_PATH,

    @JsonProperty("id_assigned")
    ID_ASSIGNED,

    @JsonProperty("hangup") // end call
    HANGUP,

    @JsonProperty("reject") //reject
    REJECT,

    @JsonProperty("ping") // connection check
    PING,

    @JsonProperty("pong") // server response for ping
    PONG,

    @JsonProperty("error")
    ERROR,

    @JsonEnumDefaultValue
    UNKNOWN
}
