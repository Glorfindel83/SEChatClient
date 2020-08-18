package com.stackexchange.toolbox.chat;

import com.google.gson.annotations.SerializedName;

public class ChatWebsocketRoomMessage {
	@SerializedName("e")
	public Event[] events;
	@SerializedName("t")
	public Long time;
	public Integer d; // unsure what this is
}
