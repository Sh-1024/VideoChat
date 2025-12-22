package org.example.videochat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VideoChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoChatApplication.class, args);
    }

}
