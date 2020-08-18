package com.stackexchange.toolbox.chat;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.stackexchange.toolbox.GenericClient;

public class ChatClient extends GenericClient {
	private static final String CHAT_SERVER_URL = "https://chat.stackexchange.com";

	public ChatClient(String propertiesFile) throws Exception {
		super(propertiesFile);

		// Login
		login("https://meta.stackexchange.com/users/login");

		// Homepage
		HttpGet getRequest = new HttpGet(CHAT_SERVER_URL);
		setCookies(getRequest);
		try (CloseableHttpResponse httpResponse = client.execute(getRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			Document document = Jsoup.parse(inputStream, "UTF-8", CHAT_SERVER_URL);
			// Chat fkey is different ...
			Element element = document.selectFirst("input[name='fkey']");
			this.fkey = element.val();
			element = document.selectFirst("span.topbar-menu-links a");
			String myProfileURL = element.attr("href");
			if (!myProfileURL.startsWith("/users/")) {
				throw new Exception("Chat login failed!");
			}
			System.out.println("Logged in as " + element.ownText());
		}
	}

	private final String fkey;
	private Integer currentRoomID;
	private WebSocketClient webSocketClient;

	private static final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {
		@Override
		public Date deserialize(JsonElement element, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			return new Date(1000 * element.getAsJsonPrimitive().getAsLong());
		}
	}).setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	public void joinRoom(int roomID, NewMessageListener newMessageListener) throws Exception {
		if (webSocketClient != null) {
			webSocketClient.stop();
			webSocketClient = null;
		}

		// Join room
		HttpPost postRequest = new HttpPost(CHAT_SERVER_URL + "/chats/" + roomID + "/events");
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		params.add(new BasicNameValuePair("since", "0"));
		params.add(new BasicNameValuePair("mode", "Messages"));
		params.add(new BasicNameValuePair("msgCount", "10"));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		setCookies(postRequest);
		EventsResponse response;
		try (CloseableHttpResponse httpResponse = client.execute(postRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			currentRoomID = roomID;

			// Retrieve recent messages
			String json = IOUtils.toString(inputStream, "UTF-8");
			System.out.println(json);
			response = gson.fromJson(json, EventsResponse.class);
			if (newMessageListener == null)
				return;
		}

		// Watch for new messages via WebSocket
		postRequest = new HttpPost(CHAT_SERVER_URL + "/ws-auth");
		params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		params.add(new BasicNameValuePair("roomid", String.valueOf(roomID)));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		setCookies(postRequest);
		try (CloseableHttpResponse httpResponse = client.execute(postRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			JSONObject jRoot = new JSONObject(IOUtils.toString(inputStream, "UTF-8"));
			String websocketURL = jRoot.getString("url") + "?l=" + response.time;
			System.out.println("Websocket URL: " + websocketURL);
			webSocketClient = new WebSocketClient();
			ChatWebsocketClient socket = new ChatWebsocketClient(gson, roomID);
			socket.addNewMessageListener(newMessageListener);
			webSocketClient.start();
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			request.setHeader("Origin", CHAT_SERVER_URL);
			webSocketClient.connect(socket, new URI(websocketURL), request);
		}
	}

	public void postChatMessage(String message) throws Exception {
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost postRequest = new HttpPost(CHAT_SERVER_URL + "/chats/" + currentRoomID + "/messages/new");
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		params.add(new BasicNameValuePair("text", message));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		setCookies(postRequest);
		try (CloseableHttpResponse httpResponse = client.execute(postRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			System.out.println(IOUtils.toString(inputStream, "UTF-8"));
		}
	}

	private static final Pattern CHAT_ROOM_USERS_PATTERN = Pattern
			.compile("CHAT.RoomUsers.initPresent\\((\\[.*?\\])\\);", Pattern.DOTALL | Pattern.MULTILINE),
			CHAT_ROOM_USER_ID_PATTERN = Pattern.compile("\\{id: (\\d+),");

	public Set<String> getUsersInRoom() throws Exception {
		HttpGet getRequest = new HttpGet(CHAT_SERVER_URL + "/rooms/" + currentRoomID);
		setCookies(getRequest);
		HttpResponse httpResponse = client.execute(getRequest);
		Matcher matcher = CHAT_ROOM_USERS_PATTERN
				.matcher(IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8"));
		matcher.find();
		// It's not a valid JSON, so we can't parse it that way ...
		Set<String> chatUsers = new HashSet<>();
		matcher = CHAT_ROOM_USER_ID_PATTERN.matcher(matcher.group(1));
		while (matcher.find()) {
			chatUsers.add(CHAT_SERVER_URL + "/users/" + matcher.group(1));
		}
		return chatUsers;
	}

	public void leaveRoom() throws Exception {
		if (webSocketClient != null) {
			webSocketClient.stop();
			webSocketClient = null;
		}

		// Leave room
		HttpPost postRequest = new HttpPost(CHAT_SERVER_URL + "/chats/leave/" + currentRoomID);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		params.add(new BasicNameValuePair("quiet", "true"));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		setCookies(postRequest);
		try (CloseableHttpResponse httpResponse = client.execute(postRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			System.out.println(IOUtils.toString(inputStream, "UTF-8"));
		}
	}

	public void logout() throws Exception {
		// Logout
		String logoutURL = "https://stackexchange.com/users/logout";
		HttpGet getRequest = new HttpGet(logoutURL);
		setCookies(getRequest);
		String fkey;
		try (CloseableHttpResponse httpResponse = client.execute(getRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			Document document = Jsoup.parse(inputStream, "UTF-8", logoutURL);
			Element element = document.selectFirst("input[name='fkey']");
			fkey = element.val();
			System.out.println("fkey: " + fkey);
		}
		HttpPost postRequest = new HttpPost(logoutURL);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		setCookies(postRequest);
		try (CloseableHttpResponse httpResponse = client.execute(postRequest)) {
			getCookies(httpResponse);
		}

		// Homepage
		getRequest = new HttpGet(CHAT_SERVER_URL);
		setCookies(getRequest);
		try (CloseableHttpResponse httpResponse = client.execute(getRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			Document document = Jsoup.parse(inputStream, "UTF-8", CHAT_SERVER_URL);
			Element element = document.selectFirst("span.topbar-menu-links a");
			String loginURL = element.attr("href");
			if (loginURL.startsWith("/users/")) {
				System.err.println("Logout failed!");
			}
		}
	}
}
