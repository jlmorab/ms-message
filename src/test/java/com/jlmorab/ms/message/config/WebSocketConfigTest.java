package com.jlmorab.ms.message.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

	private static final String ALLOWED_ORIGINS = "*";
	
	WebSocketConfig webSocketConfig;
	
	@Mock
	WebSocketMessageHandler handler;
	
	@BeforeEach
	void setUp() {
		webSocketConfig = new WebSocketConfig( handler );
		ReflectionTestUtils.setField( webSocketConfig, "brokerAllowed", ALLOWED_ORIGINS );
	}//end setUp()
	
	@Test
	void registerWebSocketHandlers_withValidHandler_shouldBeRegistered() {
		WebSocketHandlerRegistry registry = mock( WebSocketHandlerRegistry.class );
		WebSocketHandlerRegistration registration = mock( WebSocketHandlerRegistration.class );
		when( registry.addHandler( any(WebSocketMessageHandler.class), any() ) ).thenReturn( registration );
		
		webSocketConfig.registerWebSocketHandlers( registry );
		
		verify( registry ).addHandler( handler, "/ws/broker" );
		verify( registration ).setAllowedOrigins( ALLOWED_ORIGINS );
	}//end registerWebSocketHandlers_withValidHandler_shouldBeRegistered()

}
