package com.jlmorab.ms.message.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jlmorab.ms.data.TestData;
import com.jlmorab.ms.enums.WebSocketActionEnum;
import com.jlmorab.ms.message.WebSocketMessage;
import com.jlmorab.ms.utils.LoggerHelper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class WebSocketMessageHandlerTest {
	
	private static final String ANY_TEXT = UUID.randomUUID().toString();
	private static final String CHANNEL_ONE = "channel-one";
	private static final String CHANNEL_TWO = "channel-two";
	private static final int MAX_MESSAGE_SIZE = 65536;
	
	LoggerHelper loggerHelper = LoggerHelper.getInstance();
	
	WebSocketMessageHandler handler;
	
	Map<String, Set<WebSocketSession>> channelSubscriptions;
	
	Map<String, Set<String>> sessionToChannels;
	
	@Mock
	WebSocketSession session;
	
	@Mock
	CloseStatus status;
	
	@Mock
	TextMessage textMessage;
	
	@Captor
	ArgumentCaptor<TextMessage> textMessageCaptor;
	
	static ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	static void beforeAll() {
		objectMapper.registerModule( new JavaTimeModule() );
	}//end beforeAll()
	
	@BeforeEach
	void setUp() {
		lenient().when( session.getId() ).thenReturn( ANY_TEXT );
		handler = new WebSocketMessageHandler();
		channelSubscriptions = (Map<String, Set<WebSocketSession>>) 
				ReflectionTestUtils.getField( handler, "channelSubscriptions" );
		sessionToChannels = (Map<String, Set<String>>) 
				ReflectionTestUtils.getField( handler, "sessionToChannels" );
		ReflectionTestUtils.setField( handler, "maxMessageSize", MAX_MESSAGE_SIZE );
		loggerHelper.initCapture();
	}//end setUp()
	
	@AfterEach
	void tearDown() {
		loggerHelper.release();
	}//end tearDown()
	
	@Test
	void afterConnectionEstablished_shouldBeAddSession() throws Exception {
		handler.afterConnectionEstablished( session );
		
		assertThat( sessionToChannels ).containsKey( ANY_TEXT );
		assertThat( loggerHelper.getOutContent() )
			.contains( "WebSocket connection established: " + ANY_TEXT );
	}//end afterConnectionEstablished()
	
	@Test
	void afterConnectionClosed_withAssignedChannels_shouldBeUnsuscribeAndRemoveChannel() throws Exception {
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		channelSubscriptions.put( CHANNEL_TWO, new HashSet<>(List.of(session)) );
		sessionToChannels.put( ANY_TEXT, Set.of( CHANNEL_ONE, CHANNEL_TWO ) );
		
		handler.afterConnectionClosed( session, status );
		
		assertThat( sessionToChannels ).isEmpty();
		assertThat( channelSubscriptions ).isEmpty();
		assertThat( loggerHelper.getOutContent() )
			.contains( "WebSocket connection closed: " + ANY_TEXT );
	}//end afterConnectionClosed_withAssignedChannels_shouldBeUnsuscribeAndRemoveChannel()
	
	@Test
	void afterConnectionClosed_withAssignedChannelsAndOtherSession_shouldBeRemoveSession() throws Exception {
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session, mock(WebSocketSession.class))) );
		sessionToChannels.put( ANY_TEXT, new HashSet<>(List.of(CHANNEL_ONE)) );
		sessionToChannels.put( UUID.randomUUID().toString(), new HashSet<>(List.of(CHANNEL_ONE)) );
		
		handler.afterConnectionClosed( session, status );
		
		assertThat( sessionToChannels ).doesNotContainKey( ANY_TEXT );
		assertThat( channelSubscriptions )
			.containsKey( CHANNEL_ONE )
			.extractingByKey( CHANNEL_ONE ).asInstanceOf(set(WebSocketSession.class))
			.hasSize( 1 );
		assertThat( loggerHelper.getOutContent() )
			.contains( "WebSocket connection closed: " + ANY_TEXT );
	}//end afterConnectionClosed_withoutAssignedChannels_shouldBeRemoveSession()
	
	@Test
	void afterConnectionClosed_withoutAssignedChannels_shouldBeSkipRemoveSession() throws Exception {
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(mock(WebSocketSession.class))) );
		sessionToChannels.put( UUID.randomUUID().toString(), new HashSet<>(List.of(CHANNEL_ONE)) );
		
		handler.afterConnectionClosed( session, status );
		
		assertThat( sessionToChannels ).isNotEmpty();
		assertThat( channelSubscriptions ).isNotEmpty();
		assertThat( loggerHelper.getOutContent() )
			.contains( "WebSocket connection closed: " + ANY_TEXT );
	}//end afterConnectionClosed_withoutAssignedChannels_shouldBeRemoveSession()
	
	@Test
	void afterConnectionClosed_withAssignedChannelsButWithoutSubscribers_shouldBeRemoveSession() throws Exception {
		sessionToChannels.put( ANY_TEXT, new HashSet<>(List.of(CHANNEL_ONE)) );
		
		handler.afterConnectionClosed( session, status );
		
		assertThat( sessionToChannels ).isEmpty();
		assertThat( channelSubscriptions ).isEmpty();
		assertThat( loggerHelper.getOutContent() )
			.contains( "WebSocket connection closed: " + ANY_TEXT );
	}//end afterConnectionClosed_withoutAssignedChannelsAndOtherSession_shouldBeSkipRemoveSession()
	
	@Test
	void handleTextMessage_withInvalidMessage_shouldBeSendErrorMessage() throws Exception {
		int maxMessageSize = TestData.getRandom(100, 150);
		ReflectionTestUtils.setField( handler, "maxMessageSize", maxMessageSize );
		String payload = "x".repeat( maxMessageSize + 1 );
		when( session.isOpen() ).thenReturn( true );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		WebSocketMessage actual = recoverSentMessage( session );
		assertEquals( WebSocketActionEnum.ERROR, actual.getAction() );
		assertEquals( "Message size exceeds maximum limit of " + maxMessageSize + " bytes", actual.getPayload() );
	}//end handleTextMessage_withInvalidMessage_shouldBeSendErrorMessage()
	
	@Test
	void handleTextMessage_withInvalidJson_shouldBeSendErrorMessage() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		when( textMessage.getPayload() ).thenReturn( ANY_TEXT );
		
		handler.handleTextMessage( session, textMessage );
		
		WebSocketMessage actual = recoverSentMessage( session );
		assertEquals( WebSocketActionEnum.ERROR, actual.getAction() );
		assertThat(  actual.getPayload() )
			.contains("Error handling message");
	}//end handleTextMessage_withInvalidJson_shouldBeSendErrorMessage()
	
	@ParameterizedTest
	@NullAndEmptySource
	void handleTextMessage_withInvalidChannel_shouldBeSendErrorMessage( String channel ) throws Exception {
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.SUBSCRIBE )
				.channel( channel )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		WebSocketMessage actual = recoverSentMessage( session );
		assertEquals( WebSocketActionEnum.ERROR, actual.getAction() );
		assertEquals( "Channel is required", actual.getPayload() );
	}//end handleTextMessage_withInvalidChannel_shouldBeSendErrorMessage()
	
	@Test
	void handleTextMessage_withSubscribeAction_shouldBeSubscribe() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.SUBSCRIBE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat( channelSubscriptions )
			.containsKey( CHANNEL_ONE )
			.extractingByKey( CHANNEL_ONE ).asInstanceOf(set(WebSocketSession.class))
			.hasSize( 1 );
		assertThat( loggerHelper.getOutContent() )
			.contains("WebSocket session " + ANY_TEXT + " subscribed to channel " + CHANNEL_ONE);
	}//end handleTextMessage_withSubscribeAction_shouldBeSubscribe()
	
	@Test
	void handleTextMessage_withUnsubscribeAction_shouldBeUnsubscribeAndRemoveChannel() throws Exception {
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		sessionToChannels.put( ANY_TEXT, new HashSet<>(List.of(CHANNEL_ONE)) );
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.UNSUBSCRIBE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat( channelSubscriptions )
			.doesNotContainKey( CHANNEL_ONE );
		assertThat( loggerHelper.getOutContent() )
			.contains("WebSocket session " + ANY_TEXT + " unsubscribed from channel " + CHANNEL_ONE);
	}//end handleTextMessage_withUnsubscribeAction_shouldBeUnsubscribeAndRemoveChannel()
	
	@Test
	void handleTextMessage_withUnsubscribeActionManySubscribers_shouldBeUnsubscribe() throws Exception {
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session, mock( WebSocketSession.class ))) );
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.UNSUBSCRIBE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat( channelSubscriptions )
			.containsKey( CHANNEL_ONE )
			.extractingByKey( CHANNEL_ONE ).asInstanceOf(set(WebSocketSession.class))
			.hasSize( 1 );
		assertThat( loggerHelper.getOutContent() )
			.contains("WebSocket session " + ANY_TEXT + " unsubscribed from channel " + CHANNEL_ONE);
	}//end handleTextMessage_withUnsubscribeActionManySubscribers_shouldBeUnsubscribe()
	
	@Test
	void handleTextMessage_withUnsubscribeActionWithoutSubscribers_shouldBeCompleteCorrectly() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.UNSUBSCRIBE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat( channelSubscriptions )
			.doesNotContainKey( CHANNEL_ONE );
		assertThat( loggerHelper.getOutContent() )
			.contains("WebSocket session " + ANY_TEXT + " unsubscribed from channel " + CHANNEL_ONE);
	}//end handleTextMessage_withUnsubscribeActionWithoutSubscribers_shouldBeCompleteCorrectly()
	
	@Test
	void handleTextMessage_withSendActionWithoutSuscribers_doesNotSendMessage() throws Exception {
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.SEND )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat( loggerHelper.getOutContent() )
			.contains("No subscribers for channel " + CHANNEL_ONE);
	}//end handleTextMessage_withSendActionWithoutSuscribers_doesNotSendMessage()
	
	@Test
	void handleTextMessage_withSendActionWithSuscribers_shouldBeSendMessage() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.SEND )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		WebSocketMessage actual = recoverSentMessage( session );
		assertEquals( WebSocketActionEnum.MESSAGE, actual.getAction() );
		assertThat(  actual.getPayload() )
			.contains( ANY_TEXT );
		assertThat( loggerHelper.getOutContent() )
			.contains("Published message to channel " + CHANNEL_ONE);
	}//end handleTextMessage_withSendActionWithSuscribers_shouldBeSendMessage()
	
	@Test
	void handleTextMessage_withUnknownActionForProcess_shouldBeSendErrorMessage() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.MESSAGE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		
		handler.handleTextMessage( session, textMessage );
		
		WebSocketMessage actual = recoverSentMessage( session );
		assertEquals( WebSocketActionEnum.ERROR, actual.getAction() );
		assertThat(  actual.getPayload() )
			.contains("Unknown action: " + WebSocketActionEnum.MESSAGE );
	}//end handleTextMessage_withUnknownActionForProcess_shouldBeSendErrorMessage()
	
	@Test
	void sendToCHannel_whenSendMessageThrowException_shouldBeSendErrorMessage() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		doThrow( new IOException("Error sending message") )
			.when( session ).sendMessage( any(TextMessage.class) );
		
		handler.sendToChannel( CHANNEL_ONE, ANY_TEXT );
		
		assertThat(  loggerHelper.getOutContent() )
			.contains("Error sending message to session " + ANY_TEXT )
			.contains("Published message to channel " + CHANNEL_ONE);
	}//end sendToCHannel_whenSendMessageThrowException_shouldBeSendErrorMessage()
	
	@Test
	void handleTextMessage_whenSendErrorMessageThrowException_shouldBeSentLoggerOutput() throws Exception {
		when( session.isOpen() ).thenReturn( true );
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		WebSocketMessage message = WebSocketMessage.builder()
				.action( WebSocketActionEnum.MESSAGE )
				.channel( CHANNEL_ONE )
				.payload( ANY_TEXT )
				.build();
		String payload = objectMapper.writeValueAsString( message );
		when( textMessage.getPayload() ).thenReturn( payload );
		doThrow( new IOException("Error sending message") )
			.when( session ).sendMessage( any(TextMessage.class) );
		
		handler.handleTextMessage( session, textMessage );
		
		assertThat(  loggerHelper.getOutContent() )
			.contains("The error message couldn't be sent");
	}//end handleTextMessage_whenSendErrorMessageThrowException_shouldBeSentLoggerOutput()
	
	@Test
	void cleanUpInactiveSession_withInactiveSession_shouldBeRemoveSessionAndRemoveChannel() {
		when( session.isOpen() ).thenReturn( false );
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		
		handler.cleanupInactiveSessions();
		
		assertThat( sessionToChannels ).isEmpty();
		assertThat( channelSubscriptions ).isEmpty();
		assertThat( loggerHelper.getOutContent() )
			.contains("Cleaned up 1 inactive WebSocket sessions");
	}//end cleanUpInactiveSession_withInactiveSession_shouldBeRemoveSessionAndRemoveChannel()	
	
	@Test
	void cleanUpInactiveSession_withoutInactiveSession_doesNotRemoveSession() {
		when( session.isOpen() ).thenReturn( true );
		channelSubscriptions.put( CHANNEL_ONE, new HashSet<>(List.of(session)) );
		sessionToChannels.put( ANY_TEXT, new HashSet<>(List.of(CHANNEL_ONE)) );
		
		handler.cleanupInactiveSessions();
		
		assertThat( sessionToChannels ).isNotEmpty();
		assertThat( channelSubscriptions ).isNotEmpty();
		assertThat( loggerHelper.getOutContent() ).isEmpty();
	}//end cleanUpInactiveSession_withoutInactiveSession_doesNotRemoveSession()
	
	
	private WebSocketMessage recoverSentMessage( WebSocketSession session ) {
		try {
			verify( session ).sendMessage( textMessageCaptor.capture() );
			return objectMapper.readValue(
					textMessageCaptor.getValue().getPayload(), WebSocketMessage.class );
		} catch( Exception e ) {
			return null;
		}//end try
	}

}
