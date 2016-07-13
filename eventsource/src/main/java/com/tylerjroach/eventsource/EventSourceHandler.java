package com.tylerjroach.eventsource;

public interface EventSourceHandler {
  void onConnect() throws Exception;

  void onMessage(String event, MessageEvent message) throws Exception;

  void onComment(String comment) throws Exception;

  void onError(Throwable t);

  void onClosed(boolean willReconnect);
}
