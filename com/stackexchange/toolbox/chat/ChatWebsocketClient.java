package com.stackexchange.toolbox.chat;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// TODO: extend notifier.WebsocketClient
@WebSocket
public class ChatWebsocketClient {
	public ChatWebsocketClient(Gson gson, int roomID) {
		this.gson = gson;
		this.roomID = roomID;
		type = new TypeToken<Map<String, ChatWebsocketRoomMessage>>() {
		}.getType();
	}

	public void addNewMessageListener(NewMessageListener listener) {
		listeners.add(listener);
	}

	private final Gson gson;
	private final int roomID;
	private final Type type;
	private final List<NewMessageListener> listeners = new ArrayList<>();

	@SuppressWarnings("unused")
	private Session session;

	@OnWebSocketConnect
	public void onConnect(Session session) {
		System.out.println("Session: " + session);
		this.session = session;
	}

	@OnWebSocketMessage
	public void onMessage(String message) {
		System.out.println("Message: " + message);
		Map<String, ChatWebsocketRoomMessage> messages = gson.fromJson(message, type);
		ChatWebsocketRoomMessage roomMessage = messages.get("r" + roomID);
		if (roomMessage == null || roomMessage.events == null)
			return;
		for (Event event : roomMessage.events) {
			if (event.eventType != 1) // 1 = new message
				continue;

			for (NewMessageListener listener : listeners) {
				listener.onNewMessage(event);
			}
		}
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		System.out.println("Connection closed: " + statusCode + " - " + reason);
		session = null;
	}
}