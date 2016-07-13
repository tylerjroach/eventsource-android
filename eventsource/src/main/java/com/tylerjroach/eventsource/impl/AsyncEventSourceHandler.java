package com.tylerjroach.eventsource.impl;

import com.tylerjroach.eventsource.EventSourceHandler;
import com.tylerjroach.eventsource.MessageEvent;
import java.util.concurrent.Executor;

public class AsyncEventSourceHandler implements EventSourceHandler {
  private final Executor executor;
  private final EventSourceHandler eventSourceHandler;
  private boolean exposeComments;

  public AsyncEventSourceHandler(Executor executor, EventSourceHandler eventSourceHandler,
      boolean exposeComments) {
    this.executor = executor;
    this.eventSourceHandler = eventSourceHandler;
    this.exposeComments = exposeComments;
  }

  @Override public void onConnect() {
    executor.execute(new Runnable() {
      @Override public void run() {
        try {
          eventSourceHandler.onConnect();
        } catch (Exception e) {
          onError(e);
        }
      }
    });
  }

  @Override public void onClosed(final boolean willReconnect) {
    executor.execute(new Runnable() {
      @Override public void run() {
        try {
          eventSourceHandler.onClosed(willReconnect);
        } catch (Exception e) {
          onError(e);
        }
      }
    });
  }

  @Override public void onMessage(final String event, final MessageEvent message) {
    executor.execute(new Runnable() {
      @Override public void run() {
        try {
          eventSourceHandler.onMessage(event, message);
        } catch (Exception e) {
          onError(e);
        }
      }
    });
  }

  @Override public void onComment(final String comment) {
    if (exposeComments) {
      executor.execute(new Runnable() {
        @Override public void run() {
          try {
            eventSourceHandler.onComment(comment);
          } catch (Exception e) {
            onError(e);
          }
        }
      });
    }
  }

  @Override public void onError(final Throwable error) {
    executor.execute(new Runnable() {
      @Override public void run() {
        try {
          eventSourceHandler.onError(error);
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    });
  }
}
