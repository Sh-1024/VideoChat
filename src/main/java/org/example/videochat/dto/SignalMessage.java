package org.example.videochat.dto;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.example.videochat.model.MessageType;

@Getter
@Setter
@RequiredArgsConstructor
public class SignalMessage {
    private MessageType messageType;
    private String messageSender;
    private String messageReceiver;
    private Object messageData;
}
