package org.example.videochat.dto;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class SignalMessage { // change types to actual
    // message type?
    private String messageSender;
    private String messageReceiver;
    private Object messageData;
}
