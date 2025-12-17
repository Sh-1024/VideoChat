package org.example.videochat.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum MessageType {
    @JsonProperty("offer")
    OFFER,

    @JsonProperty("answer")
    ANSWER,

    @JsonProperty("network_path") //rename to candidate?
    NETWORK_PATH,

    @JsonEnumDefaultValue
    UNKNOWN
}
