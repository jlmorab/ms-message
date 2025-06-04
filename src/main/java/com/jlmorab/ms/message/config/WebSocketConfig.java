package com.jlmorab.ms.message.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

	private final WebSocketMessageHandler webSocketMessageHandler;
	
	@Value("${websocket.services.broker:*}")
	private String brokerAllowed;

	@Override
	public void registerWebSocketHandlers( WebSocketHandlerRegistry registry ) {
		registry.addHandler( webSocketMessageHandler, "/ws/broker" )
			.setAllowedOrigins( brokerAllowed );
	}//end registerWebSocketHandlers()
	
}
