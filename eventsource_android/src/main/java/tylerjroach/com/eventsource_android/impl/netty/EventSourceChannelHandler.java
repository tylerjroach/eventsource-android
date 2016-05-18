package tylerjroach.com.eventsource_android.impl.netty;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import tylerjroach.com.eventsource_android.EventSourceException;
import tylerjroach.com.eventsource_android.EventSourceHandler;
import tylerjroach.com.eventsource_android.impl.ConnectionHandler;
import tylerjroach.com.eventsource_android.impl.EventStreamParser;

public class EventSourceChannelHandler extends SimpleChannelUpstreamHandler
    implements ConnectionHandler {
  private static final Pattern STATUS_PATTERN = Pattern.compile("HTTP/1.1 (\\d+) (.*)");
  private static final Pattern CONTENT_TYPE_PATTERN =
      Pattern.compile("Content-Type: text/event-stream", Pattern.CASE_INSENSITIVE);

  private final EventSourceHandler eventSourceHandler;
  private final ClientBootstrap bootstrap;
  private final URI uri;
  private final Map<String, String> headers;
  private final EventStreamParser messageDispatcher;

  private final Timer timer = new HashedWheelTimer();
  private Channel channel;
  private boolean reconnectOnClose = true;
  private long reconnectionTimeMillis;
  private String lastEventId;
  private boolean eventStreamOk;
  private boolean headerDone;
  private Integer status;
  private AtomicBoolean reconnecting = new AtomicBoolean(false);

  public EventSourceChannelHandler(EventSourceHandler eventSourceHandler,
      long reconnectionTimeMillis, ClientBootstrap bootstrap, URI uri,
      Map<String, String> headers) {
    this.eventSourceHandler = eventSourceHandler;
    this.reconnectionTimeMillis = reconnectionTimeMillis;
    this.bootstrap = bootstrap;
    this.uri = uri;
    this.headers = headers;
    this.messageDispatcher = new EventStreamParser(uri.toString(), eventSourceHandler, this);
  }

  @Override public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
    super.handleUpstream(ctx, e);
  }

  @Override public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception {
    HttpRequest request =
        new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toString());
    request.addHeader(Names.ACCEPT, "text/event-stream");

    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        request.addHeader(entry.getKey(), entry.getValue());
      }
    }

    request.addHeader(Names.HOST, uri.getHost());
    request.addHeader(Names.ORIGIN, uri.getScheme() + "://" + uri.getHost());
    request.addHeader(Names.CACHE_CONTROL, "no-cache");
    if (lastEventId != null) {
      request.addHeader("Last-Event-ID", lastEventId);
    }
    e.getChannel().write(request);
    channel = e.getChannel();
  }

  @Override public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception {
    channel = null;
  }

  @Override public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception {
    eventSourceHandler.onClosed(reconnectOnClose);
    if (reconnectOnClose) {
      reconnect();
    }
  }

  @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
      throws Exception {
    String line = (String) e.getMessage();
    if (status == null) {
      Matcher statusMatcher = STATUS_PATTERN.matcher(line);
      if (statusMatcher.matches()) {
        status = Integer.parseInt(statusMatcher.group(1));
        if (status != 200) {
          eventSourceHandler.onError(
              new EventSourceException("Bad status from " + uri + ": " + status));
          reconnect();
        }
        return;
      } else {
        eventSourceHandler.onError(new EventSourceException("Not HTTP? " + uri + ": " + line));
        reconnect();
      }
    }
    if (!headerDone) {
      if (CONTENT_TYPE_PATTERN.matcher(line).find()) {
        eventStreamOk = true;
      }
      if (line.isEmpty()) {
        headerDone = true;
        if (eventStreamOk) {
          eventSourceHandler.onConnect();
        } else {
          eventSourceHandler.onError(new EventSourceException(
              "Not event stream: " + uri + " (expected Content-Type: text/event-stream"));
          reconnect();
        }
      }
    } else {
      messageDispatcher.line(line);
    }
  }

  @Override public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception {
    Throwable error = e.getCause();
    if (error instanceof ConnectException) {
      error = new EventSourceException("Failed to connect to " + uri, error);
    }
    eventSourceHandler.onError(error);
    ctx.getChannel().close();
  }

  public void setReconnectionTimeMillis(long reconnectionTimeMillis) {
    this.reconnectionTimeMillis = reconnectionTimeMillis;
  }

  @Override public void setLastEventId(String lastEventId) {
    this.lastEventId = lastEventId;
  }

  public EventSourceChannelHandler close() {
    reconnectOnClose = false;
    if (channel != null) {
      channel.close();
    }
    return this;
  }

  public EventSourceChannelHandler join() throws InterruptedException {
    if (channel != null) {
      channel.getCloseFuture().await();
    }
    return this;
  }

  private void reconnect() {
    if (!reconnecting.get()) {
      reconnecting.set(true);
      timer.newTimeout(new TimerTask() {
        @Override public void run(Timeout timeout) throws Exception {
          reconnecting.set(false);
          int port = uri.getPort();
          if (port == -1) {
            port = (uri.getScheme().equals("https")) ? 443 : 80;
          }
          bootstrap.setOption("remoteAddress", new InetSocketAddress(uri.getHost(), port));
          bootstrap.connect().await();
        }
      }, reconnectionTimeMillis, TimeUnit.MILLISECONDS);
    }
  }
}