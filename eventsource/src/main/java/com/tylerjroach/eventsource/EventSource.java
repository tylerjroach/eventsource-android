package com.tylerjroach.eventsource;

import android.util.Log;
import com.tylerjroach.eventsource.impl.AsyncEventSourceHandler;
import com.tylerjroach.eventsource.impl.netty.EventSourceChannelHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLEngine;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.ssl.SslHandler;

public class EventSource implements EventSourceHandler {
  public static final String TAG = "EventSource";
  public static final long DEFAULT_RECONNECTION_TIME_MILLIS = 2000;

  public static final int CONNECTING = 0;
  public static final int OPEN = 1;
  public static final int CLOSED = 2;
  private final ClientBootstrap bootstrap;
  private final EventSourceChannelHandler clientHandler;
  private URI uri;
  private final EventSourceHandler eventSourceHandler;
  private int readyState;

  private EventSource(Builder builder) {
    this.uri = builder.uri;
    this.eventSourceHandler = builder.eventSourceHandler;
    boolean exposeComments = builder.exposeComments;
    long reconnectInterval = builder.reconnectInterval;
    Executor executor = builder.executor;
    Map<String, String> headers = builder.headers;
    SSLEngineFactory sslEngineFactory = builder.sslEngineFactory;

    if (eventSourceHandler == null)
      Log.d(TAG, "No handler attached");

    bootstrap = new ClientBootstrap(
        new NioClientSocketChannelFactory(Executors.newSingleThreadExecutor(),
            Executors.newSingleThreadExecutor()));
    if (uri.getScheme().equals("https") && sslEngineFactory == null) {
      sslEngineFactory = new SSLEngineFactory();
    } else {
      //If we don't do this then the pipeline still attempts to use SSL
      sslEngineFactory = null;
    }
    final SSLEngineFactory SSLFactory = sslEngineFactory;

    int port = uri.getPort();
    if (port == -1) {
      port = (uri.getScheme().equals("https")) ? 443 : 80;
    }
    bootstrap.setOption("remoteAddress", new InetSocketAddress(uri.getHost(), port));

    // add this class as the event source handler so the connect() call can be intercepted
    AsyncEventSourceHandler asyncHandler =
        new AsyncEventSourceHandler(executor, this, exposeComments);

    clientHandler =
        new EventSourceChannelHandler(asyncHandler, reconnectInterval, bootstrap, uri,
            headers);

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        if (SSLFactory != null) {
          SSLEngine sslEngine = SSLFactory.GetNewSSLEngine();
          sslEngine.setUseClientMode(true);
          pipeline.addLast("ssl", new SslHandler(sslEngine));
        }

        pipeline.addLast("line",
            new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
        pipeline.addLast("string", new StringDecoder());

        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("es-handler", clientHandler);
        return pipeline;
      }
    });
  }

  /**
   * Builder used to build EventSource
   */

  public static final class Builder {
    private long reconnectInterval = DEFAULT_RECONNECTION_TIME_MILLIS;
    private URI uri;
    private boolean exposeComments;
    private Map<String, String> headers;
    private SSLEngineFactory sslEngineFactory;
    private Executor executor;
    private EventSourceHandler eventSourceHandler;

    /**
     * @param uri to connect
     */
    public Builder(URI uri) {
      this.uri = uri;
    }

    /**
     * @param reconnectInterval delay (in milliseconds) before a reconnect is made - in the event of a lost
     */

    public Builder reconnectInterval(long reconnectInterval) {
      this.reconnectInterval = reconnectInterval;
      return this;
    }

    /**
     * @param exposeComments Pass comments through handler
     */
    public Builder exposeComments(boolean exposeComments) {
      this.exposeComments = exposeComments;
      return this;
    }

    /**
     * @param headers Map of headers to pass
     */
    public Builder headers(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    /**
     * @param sslEngineFactory custom factory
     */

    public Builder sslEngineFactory(SSLEngineFactory sslEngineFactory) {
      this.sslEngineFactory = sslEngineFactory;
      return this;
    }

    /**
     * @param executor the executor that will receive events
     */
    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    /**
     * @param eventSourceHandler receives events
     */
    public Builder eventHandler(EventSourceHandler eventSourceHandler) {
      this.eventSourceHandler = eventSourceHandler;
      return this;
    }

    /**
     * @return new EventSource
     */

    public EventSource build() {
      return new EventSource(this);
    }
  }

  public ChannelFuture connect() {
    readyState = CONNECTING;

    //To avoid perpetual "SocketUnresolvedException"
    int port = uri.getPort();
    if (port == -1) {
      port = (uri.getScheme().equals("https")) ? 443 : 80;
    }
    bootstrap.setOption("remoteAddress", new InetSocketAddress(uri.getHost(), port));
    return bootstrap.connect();
  }

  public boolean isConnected() {
    return (readyState == OPEN);
  }

  /**
   * Close the connection
   *
   * @return self
   */
  public EventSource close() {
    readyState = CLOSED;
    clientHandler.close();
    return this;
  }

  /**
   * Wait until the connection is closed
   *
   * @return self
   * @throws InterruptedException if waiting was interrupted
   */
  public EventSource join() throws InterruptedException {
    clientHandler.join();
    return this;
  }

  @Override public void onConnect() throws Exception {
    // flag the connection as open
    readyState = OPEN;

    if (eventSourceHandler != null)
      eventSourceHandler.onConnect();
  }

  @Override public void onMessage(String event, MessageEvent message) throws Exception {
    if (eventSourceHandler != null)
      eventSourceHandler.onMessage(event, message);
  }

  @Override public void onComment(String comment) throws Exception {
    if (eventSourceHandler != null)
      eventSourceHandler.onComment(comment);
  }

  @Override public void onError(Throwable t) {
    if (eventSourceHandler != null)
      eventSourceHandler.onError(t);
  }

  @Override public void onClosed(boolean willReconnect) {
    if (eventSourceHandler != null)
      eventSourceHandler.onClosed(willReconnect);
  }
}
