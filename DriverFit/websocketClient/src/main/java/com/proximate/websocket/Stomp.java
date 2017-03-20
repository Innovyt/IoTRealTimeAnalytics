package com.proximate.websocket;

import android.net.Uri;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.WebSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

public class Stomp {
    Logger logger = LoggerFactory.getLogger(Stomp.class);

    public static final int CONNECTED = 1;// Connection completely established
    public static final int NOT_AGAIN_CONNECTED = 2;// Connection process is
    // ongoing
    public static final int DECONNECTED_FROM_OTHER = 3;// Error, no more
    // internet connection,
    // etc.
    public static final int DECONNECTED_FROM_APP = 4;// application explicitely
    // ask for shut down the
    // connection

    private static final String PREFIX_ID_SUBSCIPTION = "sub-";
    private static final String ACCEPT_VERSION_NAME = "accept-version";
    private static final String ACCEPT_VERSION = "1.2,1.1,1.0";
    private static final String COMMAND_CONNECT = "CONNECT";
    private static final String COMMAND_CONNECTED = "CONNECTED";
    private static final String COMMAND_MESSAGE = "MESSAGE";
    private static final String COMMAND_RECEIPT = "RECEIPT";
    private static final String COMMAND_ERROR = "ERROR";
    private static final String COMMAND_DISCONNECT = "DISCONNECT";
    private static final String COMMAND_SEND = "SEND";
    private static final String COMMAND_SUBSCRIBE = "SUBSCRIBE";
    private static final String COMMAND_UNSUBSCRIBE = "UNSUBSCRIBE";
    private static final String SUBSCRIPTION_ID = "id";
    private static final String SUBSCRIPTION_DESTINATION = "destination";
    private static final String SUBSCRIPTION_SUBSCRIPTION = "subscription";
    private static final String RECEIPT_ID_DISCONNECTED = "DISCONNECTED";

    private static final Set<String> VERSIONS = new HashSet<String>();

    private ListenerWebsocketConnection websocketConnectionListener = null;

    static {
        VERSIONS.add("V1.0");
        VERSIONS.add("V1.1");
        VERSIONS.add("V1.2");
    }

    private WebSocket websocket;

    private int counter;

    private int connection;

    private Map<String, String> headers;

    private int maxWebSocketFrameSize;

    private Map<String, Subscription> subscriptions;

    @SuppressWarnings("unused")
    //private Session session;

    private ListenerWSNetwork networkListener;

    private String url;


    public Stomp(ListenerWSNetwork stompStates, ListenerWebsocketConnection websocketListener) {
        //websocket = new WebSocketClient();
        this.maxWebSocketFrameSize = 16 * 1024;
        this.connection = NOT_AGAIN_CONNECTED;
        this.networkListener = stompStates;
        this.subscriptions = new HashMap<String, Subscription>();
        this.headers = new HashMap<String, String>();
        this.websocketConnectionListener = websocketListener;
    }

    public void connect(String uri, Map<String, String> headersSetup, Map<String,Subscription> subscriptions) {
        this.subscriptions.clear();
        if(subscriptions != null)
            this.subscriptions.putAll(subscriptions);
        this.connect(uri,headersSetup);
    }

    public int incrementCounter() {
        return counter;
    }


    public void connect(String uri, Map<String, String> headersSetup) {
        Uri echoUri = Uri.parse(uri);
        Headers headers = new Headers();
        for (Entry<String, String> entry : headersSetup.entrySet()) {
            if (entry.getKey().equals("Authorization")) {
                headers.add("Authorization", "Bearer " + entry.getValue());
            } else {
                headers.add(entry.getKey(), entry.getValue());
            }
        }
        AsyncHttpRequest request = new AsyncHttpRequest(echoUri, AsyncHttpGet.METHOD, headers);
        request.setLogging(Stomp.class.getName(), Log.VERBOSE);
        request.setTimeout(5000);
        AsyncHttpClient.getDefaultInstance().websocket(request, "STOMP", new WebSocketCallback());
    }

    public class WebSocketCallback implements AsyncHttpClient.WebSocketConnectCallback {

        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            boolean connectionFailed = false;
            if (ex != null) {
                ex.printStackTrace();
                connectionFailed = true;
            }
            if(webSocket == null)
            {
                logger.debug("Webcocket connection failed.");
                connectionFailed = true;
            }
            if(connectionFailed == true)
            {
                websocketConnectionListener.onDisconnected();
                return;
            }
            System.out.printf("Got connect: %s%n", webSocket);
            Stomp.this.websocket = webSocket;
            websocketConnectionListener.onConnected();
            websocket.setClosedCallback(new ClosedCallback());
            Stomp.this.websocket.setStringCallback(new StompMessageCallback());
            if (Stomp.this.headers != null) {
                Stomp.this.headers.put(ACCEPT_VERSION_NAME, ACCEPT_VERSION);
                //Stomp.this.headers.put("heart-beat", "1000,0");

                transmit(COMMAND_CONNECT, Stomp.this.headers, null);
            }
        }
    }

    public class ClosedCallback implements CompletedCallback {
        @Override
        public void onCompleted(Exception ex) {
            logger.debug("Problem : Web Socket disconnected whereas Stomp disconnect method has never " + "been called.");
            disconnectFromServer();
            websocketConnectionListener.onDisconnected();
        }
    }

    public class StompMessageCallback implements WebSocket.StringCallback {
        @Override
        public void onStringAvailable(String message) {
            onMessage(message);
        }
    }

    public void onMessage(String message) {
        logger.debug("STOMP message:\n" + message);
        if(message.equals("\n"))
            return;
        Frame frame = Frame.fromString(message);
        boolean isMessageConnected = false;

        if (frame.getCommand().equals(COMMAND_CONNECTED)) {
            Stomp.this.connection = CONNECTED;
            Stomp.this.networkListener.onState(CONNECTED);

            logger.debug("connected to server : " + frame.getHeaders().get("server"));
            isMessageConnected = true;

        } else if (frame.getCommand().equals(COMMAND_MESSAGE)) {
            String subscription = frame.getHeaders().get(SUBSCRIPTION_SUBSCRIPTION);
            ListenerSubscription onReceive = Stomp.this.subscriptions.get(subscription).getCallback();

            if (onReceive != null) {
                onReceive.onMessage(frame.getHeaders(), frame.getBody());
            } else {
                logger.error("Error : Subscription with id = " + subscription + " had not been subscribed");
            }

        } else if (frame.getCommand().equals(COMMAND_RECEIPT)) {
            String receiptValue = frame.getHeaders().get("receipt-id");
            if (receiptValue.equals(RECEIPT_ID_DISCONNECTED)) {
                disconnectFromApp();
            }
        } else if (frame.getCommand().equals(COMMAND_ERROR)) {
            logger.error("Error : Headers = " + frame.getHeaders() + ", Body = " + frame.getBody());

        }

        if (isMessageConnected)
            Stomp.this.subscribe();
    }

    /**
     * Send a message to server thanks to websocket
     *
     * @param command one of a frame property, see {@link Frame} for more details
     * @param headers one of a frame property, see {@link Frame} for more details
     * @param body    one of a frame property, see {@link Frame} for more details
     */
    private void transmit(String command, Map<String, String> headers, String body) {
        String out = Frame.marshall(command, headers, body);
        logger.debug("Sending STOMP message:{}", out);
        websocket.send(out);
    }

    /**
     * disconnection come from the server, without any intervention of client
     * side. Operations order is very important
     */
    private void disconnectFromServer() {
        if (this.connection == CONNECTED) {
            this.connection = DECONNECTED_FROM_OTHER;
            this.websocket.close();
            this.networkListener.onState(this.connection);
        }
    }

    /**
     * disconnection come from the app, because the public method disconnect was
     * called
     */
    private void disconnectFromApp() {
        if (this.connection == DECONNECTED_FROM_APP) {
            this.websocket.close();
            this.networkListener.onState(this.connection);
        }
    }

    /**
     * Close the web socket connection with the server. Operations order is very
     * important
     */
    public void disconnect() {
        if (this.connection == CONNECTED) {
            this.connection = DECONNECTED_FROM_APP;
            Map<String, String> headers = new HashMap<>();
            headers.put("receipt", RECEIPT_ID_DISCONNECTED);
            transmit(COMMAND_DISCONNECT, headers, null);
        }
    }

    /**
     * Send a simple message to the server thanks to the body parameter
     *
     * @param destination The destination through a Stomp message will be send to the
     *                    server
     * @param headers     headers of the message
     * @param body        body of a message
     */
    public void send(String destination, Map<String, String> headers, String body) {
        if (this.connection == CONNECTED) {
            if (headers == null)
                headers = new HashMap<String, String>();

            if (body == null)
                body = "";

            headers.put(SUBSCRIPTION_DESTINATION, destination);

            transmit(COMMAND_SEND, headers, body);
        }
    }

    /**
     * Allow a client to send a subscription message to the server independently
     * of the initialization of the web socket. If connection have not been
     * already done, just save the subscription
     *
     * @param subscription a subscription object
     */
    public void subscribe(Subscription subscription) {
        if(subscription.getId() == null)
            subscription.setId(PREFIX_ID_SUBSCIPTION + UUID.randomUUID().toString());
        else
            subscription.setId(PREFIX_ID_SUBSCIPTION + subscription.getId());
        this.subscriptions.put(subscription.getId(), subscription);
        if (this.connection == CONNECTED ) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(SUBSCRIPTION_ID, subscription.getId());
            headers.put(SUBSCRIPTION_DESTINATION, subscription.getDestination());

            subscribe(headers);
        }
    }

    /**
     * Subscribe to a Stomp channel, through messages will be send and received.
     * A message send from a determine channel can not be receive in an another.
     */
    private void subscribe() {
        if (this.connection == CONNECTED) {
            for (Subscription subscription : this.subscriptions.values()) {
                Map<String, String> headers = new HashMap<String, String>();
                headers.put(SUBSCRIPTION_ID, subscription.getId());
                headers.put(SUBSCRIPTION_DESTINATION, subscription.getDestination());

                subscribe(headers);
            }
        }
    }

    /**
     * Send the subscribe to the server with an header
     *
     * @param headers header of a subscribe STOMP message
     */
    private void subscribe(Map<String, String> headers) {
        transmit(COMMAND_SUBSCRIBE, headers, null);
    }

    /**
     * Destroy a subscription with its id
     *
     * @param id the id of the subscription. This id is automatically setting
     *           up in the subscribe method
     */
    public void unsubscribe(String id) {
        if (this.connection == CONNECTED) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(SUBSCRIPTION_ID, id);

            this.subscriptions.remove(id);
            this.transmit(COMMAND_UNSUBSCRIBE, headers, null);
        }
    }

    public interface ListenerWSNetwork {
        public void onState(int state);
    }

    public interface ListenerWebsocketConnection {
        public void onConnected();
        public void onDisconnected();
    }
}