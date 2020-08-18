package com.stackexchange.toolbox.chat;

import java.util.Date;

public class Event {
	// See https://stackapps.com/a/8269/34061 for possible values
	public int eventType;

	public Date timeStamp;
	public String content, userName, roomName;
	public int userId, roomId, messageId;
	public Integer targetUserId;
}
