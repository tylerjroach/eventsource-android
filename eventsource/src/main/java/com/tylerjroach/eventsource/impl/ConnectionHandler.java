package com.tylerjroach.eventsource.impl;

public interface ConnectionHandler {
  void setReconnectionTimeMillis(long reconnectionTimeMillis);

  void setLastEventId(String lastEventId);
}
