package com.stackexchange.toolbox.chat;

public class MessagePoster {
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Usage: MessagePoster <room ID> <message>");
			return;
		}
		int roomID = Integer.parseInt(args[0]);
		String message = args[1];

		ChatClient client = new ChatClient("login.properties");
		client.joinRoom(roomID, null);
		client.postChatMessage(message);
		client.close();
	}
}
