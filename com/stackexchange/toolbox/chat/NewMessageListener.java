package com.stackexchange.toolbox.chat;

@FunctionalInterface
public interface NewMessageListener {
	void onNewMessage(Event event);
}
