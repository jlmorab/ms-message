package com.jlmorab.ms.message.config;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jlmorab.ms.enums.WebSocketActionEnum;
import com.jlmorab.ms.message.WebSocketMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketMessageHandler extends TextWebSocketHandler {

	private final Map<String, Set<WebSocketSession>> channelSubscriptions = new ConcurrentHashMap<>();
	
	private final Map<String, Set<String>> sessionToChannels = new ConcurrentHashMap<>();
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	@Value("${websocket.message.max-size:65536}")
    private int maxMessageSize;
	
	@Override
	public void afterConnectionEstablished( WebSocketSession session ) throws Exception {
		sessionToChannels.put( session.getId(), ConcurrentHashMap.newKeySet() );
		log.debug("WebSocket connection established: {}", session.getId());
	}//end afterConnectionEstablished()
	
	@Override
	public void afterConnectionClosed( WebSocketSession session, CloseStatus status ) throws Exception {
		Set<String> channels = sessionToChannels.remove( session.getId() );
		if( channels != null ) {
			for( String channel : channels ) {
				Set<WebSocketSession> subscribers = channelSubscriptions.get( channel );
				if( subscribers != null ) {
					subscribers.remove( session );
					if( subscribers.isEmpty() ) {
						channelSubscriptions.remove( channel );
					}//end if
				}//end if
			}//end for
		}//end if
		log.debug("WebSocket connection closed: {}", session.getId());
	}//end afterConnectionClosed()

	@Override
	protected void handleTextMessage( WebSocketSession session, TextMessage message ) throws Exception {
		try {
			String messagePayload = message.getPayload();
			if( messagePayload.length() > maxMessageSize ) {
				String errorMessage = String.format("Message size exceeds maximum limit of %d bytes", maxMessageSize);
				log.warn( errorMessage );
				sendErrorMessage( session, errorMessage );
				return;
			}//end if
			
			WebSocketMessage webSocketMessage = parseMessage( messagePayload );
			WebSocketActionEnum action = webSocketMessage.getAction();
			String channel = webSocketMessage.getChannel();
			String payload = webSocketMessage.getPayload();
			
			if( channel == null || channel.trim().isEmpty() ) {
				log.warn("Channel is required for action: {}", action);
				sendErrorMessage( session, "Channel is required" );
				return;
			}//end if
			
			switch( action ) {
				case SUBSCRIBE -> subscribe( session, channel );
				case UNSUBSCRIBE -> unsubscribe( session, channel );
				case SEND -> sendToChannel( channel, payload );
				default -> {
					log.warn("Unknown action: {}", action);
					sendErrorMessage( session, "Unknown action: " + action );
				}//end default
			}//end switch
		} catch( Exception e ) {
			log.error("Error handling message: {}", e.getMessage(), e);
			sendErrorMessage( session, String.format("Error handling message: %s", e.getMessage()) );
		}//end try
	}//end handleTextMessage()
	
	public void sendToChannel( String channel, String payload ) {
		Set<WebSocketSession> subscribers = channelSubscriptions.getOrDefault( channel, Collections.emptySet() );
		if( subscribers.isEmpty() ) {
			log.debug("No subscribers for channel {}", channel);
			return;
		}//end if
		
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.MESSAGE )
				.channel( channel )
				.payload( payload )
				.timestamp( System.currentTimeMillis() )
				.build();
		
		subscribers.forEach( subscriber -> {
			try {
				sendMessage( subscriber, message );
			} catch( IOException e ) {
				log.error("Error sending message to session {}", subscriber.getId(), e);
			}//end try
		});//end forEach
		
		log.debug("Published message to channel {}", channel);
	}//end sendToChannel()
	
	@Scheduled(fixedDelayString = "${websocket.cleanup.interval-ms:300000}")
	public void cleanupInactiveSessions() {
		int cleaned = 0;
		Set<String> inactiveSessions = new HashSet<>();
		
		for( Map.Entry<String, Set<WebSocketSession>> entry : channelSubscriptions.entrySet() ) {
			Set<WebSocketSession> sessions = entry.getValue();
			int before = sessions.size();
			
			sessions.removeIf( session -> {
				boolean inactive = !session.isOpen();
				if( inactive ) inactiveSessions.add( session.getId() );
				return inactive;
			});
			
			if( sessions.isEmpty() ) {
				channelSubscriptions.remove( entry.getKey() );
			}//end if
			
			cleaned += ( before - sessions.size() );
		}//end for
		
		inactiveSessions.forEach( sessionToChannels::remove );
		
		if(  cleaned > 0 )
			log.debug("Cleaned up {} inactive WebSocket sessions", cleaned);
	}//end cleanupInactiveSessions()
	
	
	private void subscribe( WebSocketSession session, String channel ) throws IOException {
		if( sessionToChannels.getOrDefault( session.getId(), Collections.emptySet() ).contains( channel ) ) {
			log.debug("Session {} is already subscribed to {}", session.getId(), channel);
			return;
		}//end if
		
		channelSubscriptions.computeIfAbsent( channel, k -> ConcurrentHashMap.newKeySet() ).add( session );
		sessionToChannels.computeIfAbsent( session.getId(), k -> ConcurrentHashMap.newKeySet() ).add( channel );
		log.debug("WebSocket session {} subscribed to channel {}", session.getId(), channel);
		
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.SUBSCRIBED )
				.channel( channel )
				.payload( "Subscribed to channel: " + channel )
				.timestamp( System.currentTimeMillis() )
				.build();
		
		sendMessage( session, message );
	}//end subscribe()
	
	private void unsubscribe( WebSocketSession session, String channel ) throws IOException {
		Set<WebSocketSession> subscribers = channelSubscriptions.get( channel );
		if( subscribers != null ) {
			subscribers.remove( session );
			if( subscribers.isEmpty() ) {
				channelSubscriptions.remove( channel );
			}//end if
		}//end if
		
		Set<String> channels = sessionToChannels.get( session.getId() );
		if( channels != null ) channels.remove( channel );
		log.debug("WebSocket session {} unsubscribed from channel {}", session.getId(), channel);
		
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.UNSUBSCRIBED )
				.channel( channel )
				.payload("Unsubscribed from channel: " + channel)
				.timestamp( System.currentTimeMillis() )
				.build();
		
		sendMessage( session, message );
	}//end unsubscribe()
	
	private WebSocketMessage parseMessage( String message ) throws IOException {
		return mapper.readValue( message, WebSocketMessage.class );
	}//end parseMessage()
	
	private void sendMessage( WebSocketSession session, WebSocketMessage message ) throws IOException {
		if( session.isOpen() ) {
			String json = mapper.writeValueAsString( message );
			session.sendMessage( new TextMessage( json ) );
		}//end if
	}//end sendMessage()
	
	private void sendErrorMessage( WebSocketSession session, String errorMessage ) {
		try {
			WebSocketMessage message = WebSocketMessage.builder()
					.action( WebSocketActionEnum.ERROR )
					.payload( errorMessage )
					.timestamp( System.currentTimeMillis() )
					.build();
			
			sendMessage( session, message );
		} catch( Exception e ) {
			log.error("The error message couldn't be sent: {}", e.getMessage(), e);
		}//end try
	}//end sendErrorMessage()

}
