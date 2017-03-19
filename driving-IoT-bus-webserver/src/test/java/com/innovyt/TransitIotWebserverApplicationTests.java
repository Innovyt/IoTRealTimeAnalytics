package com.innovyt;

import static java.util.Arrays.asList;

import java.lang.reflect.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransitIotWebserverApplicationTests {

	private WebSocketStompClient stompClient;
	CountDownLatch latch = new CountDownLatch(1);
	// private static SockJsClient sockJsClient;
	StandardWebSocketClient wsClient;
	private boolean receivedWebTrackingEvent = false;
	private final static WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

	@Test
	public void connectTest() throws Exception {
		// JettyWebSocketClient client = new JettyWebSocketClient();
		// stompClient = new WebSocketStompClient(wsClient);
		stompClient = new WebSocketStompClient(new SockJsClient(
				asList(new WebSocketTransport(new StandardWebSocketClient()))));
		stompClient.start();
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		StompSessionHandler handler = new AbstractTestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session,
					StompHeaders connectedHeaders) {
				latch.countDown();
			}
		};
		stompClient.connect("ws://10.10.1.141:61613/monitor", this.headers,
				handler);
		latch.await(5000, TimeUnit.MILLISECONDS);
		stompClient.stop();
	}

	@Test
	public void WebsocketTest() throws Exception {
		// stompClient = new WebSocketStompClient(client);
		stompClient = new WebSocketStompClient(new SockJsClient(
				asList(new WebSocketTransport(new StandardWebSocketClient()))));
		stompClient.start();
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		StompSessionHandler handler = new AbstractTestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session,
					StompHeaders connectedHeaders) {
				String message = "MESSAGE TEST";
				session.send("/topic/obdBeadings", message.getBytes());
			}
		};
		stompClient.connect("ws://10.10.1.141:61613/monitor", this.headers,
				handler);
		latch.await(5000, TimeUnit.MILLISECONDS);
		stompClient.stop();
	}

	@Test
	public void webClientTopicSubscribe() throws Exception {
		// stompClient = new WebSocketStompClient(wsClient);
		stompClient = new WebSocketStompClient(new SockJsClient(
				asList(new WebSocketTransport(new StandardWebSocketClient()))));
		stompClient.start();
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		StompSessionHandler handler = new AbstractTestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session,
					StompHeaders connectedHeaders) {
				byte[] payload = null;
				session.subscribe("/topic/obdBeadings",
						new StompFrameHandler() {

							@Override
							public void handleFrame(StompHeaders headers,
									Object payload) {
								receivedWebTrackingEvent = true;

							}

							@Override
							public Type getPayloadType(StompHeaders headers) {
								// TODO Auto-generated method stub
								return null;
							}
						});
			}
		};
		stompClient.connect("ws://10.10.1.141:61613/monitor", this.headers,
				handler);
		latch.await(5000, TimeUnit.MILLISECONDS);
		stompClient.stop();
		Assert.isTrue(receivedWebTrackingEvent);

	}

	private static abstract class AbstractTestSessionHandler extends
			StompSessionHandlerAdapter {
		Logger logger = LoggerFactory
				.getLogger(AbstractTestSessionHandler.class);
		private final AtomicReference<Throwable> failure;

		public AbstractTestSessionHandler(AtomicReference<Throwable> failure) {
			this.failure = failure;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			logger.error("STOMP ERROR frame: " + headers.toString());
			this.failure.set(new Exception(headers.toString()));
		}

		@Override
		public void handleException(StompSession s, StompCommand c,
				StompHeaders h, byte[] p, Throwable ex) {
			logger.error("Handler exception", ex);
			this.failure.set(ex);
		}

		@Override
		public void handleTransportError(StompSession session, Throwable ex) {
			logger.error("Transport failure", ex);
			this.failure.set(ex);
		}
	}
}
