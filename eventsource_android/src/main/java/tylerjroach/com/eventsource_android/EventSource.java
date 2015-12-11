package tylerjroach.com.eventsource_android;

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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLEngine;

import tylerjroach.com.eventsource_android.impl.AsyncEventSourceHandler;
import tylerjroach.com.eventsource_android.impl.netty.EventSourceChannelHandler;

public class EventSource implements EventSourceHandler {
    public static final long DEFAULT_RECONNECTION_TIME_MILLIS = 2000;

    public static final int CONNECTING = 0;
    public static final int OPEN = 1;
    public static final int CLOSED = 2;

    private boolean exposeComments = false;

    private final ClientBootstrap bootstrap;
    private final EventSourceChannelHandler clientHandler;
    private final EventSourceHandler eventSourceHandler;

    private URI uri;
    private Map<String, String> headers;
    private int readyState;

    /**
     * Creates a new <a href="http://dev.w3.org/html5/eventsource/">EventSource</a> client. The client will reconnect on 
     * lost connections automatically, unless the connection is closed explicitly by a call to 
     * {@link tylerjroach.com.eventsource_android.EventSource#close()}.
     *
     * For sample usage, see examples at <a href="https://github.com/aslakhellesoy/eventsource-java/tree/master/src/test/java/com/github/eventsource/client">GitHub</a>.
     * 
     * @param executor the executor that will receive events
     * @param reconnectionTimeMillis delay before a reconnect is made - in the event of a lost connection
     * @param pURI where to connect
     * @param eventSourceHandler receives events
     * @param headers Map of additional headers, such as passing auth tokens
     * @see #close()
     */
    public EventSource(Executor executor, long reconnectionTimeMillis, final URI pURI, EventSourceHandler eventSourceHandler, Map<String, String> headers, boolean exposeComments) {
        this(executor, reconnectionTimeMillis, pURI, null, eventSourceHandler, headers, exposeComments);
    }

    public EventSource(Executor executor, long reconnectionTimeMillis, final URI pURI, SSLEngineFactory fSSLEngine, EventSourceHandler eventSourceHandler, Map<String, String> headers, Boolean expose) {
        this.eventSourceHandler = eventSourceHandler;

        if (expose != null) {
            this.exposeComments = expose;
        }

        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newSingleThreadExecutor(),
                        Executors.newSingleThreadExecutor()));
        if (pURI.getScheme().equals("https") && fSSLEngine == null) {
        	fSSLEngine = new SSLEngineFactory();
        } else {
        	//If we don't do this then the pipeline still attempts to use SSL
        	fSSLEngine = null;
        }
        final SSLEngineFactory SSLFactory = fSSLEngine;
        
        uri = pURI;
        int port = uri.getPort();
        if (port==-1)
        {
        	port = (uri.getScheme().equals("https"))?443:80;
        }
        bootstrap.setOption("remoteAddress", new InetSocketAddress(uri.getHost(), port));

        // add this class as the event source handler so the connect() call can be intercepted
        AsyncEventSourceHandler asyncHandler = new AsyncEventSourceHandler(executor, this, exposeComments);

        clientHandler = new EventSourceChannelHandler(asyncHandler, reconnectionTimeMillis, bootstrap, uri, headers);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();

                if (SSLFactory != null) {
                	SSLEngine sslEngine = SSLFactory.GetNewSSLEngine();
                	sslEngine.setUseClientMode(true);
                    pipeline.addLast("ssl", new SslHandler(sslEngine));
                }

                pipeline.addLast("line", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
                pipeline.addLast("string", new StringDecoder());

                pipeline.addLast("encoder", new HttpRequestEncoder());
                pipeline.addLast("es-handler", clientHandler);
                return pipeline;
            }
        });
    }

    public EventSource(String uri, EventSourceHandler eventSourceHandler, boolean exposeComments) {
        this(uri, null, eventSourceHandler, exposeComments);
    }

    public EventSource(String uri, SSLEngineFactory sslEngineFactory, EventSourceHandler eventSourceHandler, boolean exposeComments) {
        this(URI.create(uri), sslEngineFactory, eventSourceHandler, exposeComments);
    }

    public EventSource(URI uri, EventSourceHandler eventSourceHandler, boolean exposeComments) {
        this(uri, null, eventSourceHandler, exposeComments);
    }

    public EventSource(URI uri, EventSourceHandler eventSourceHandler, Map<String, String> headers, boolean exposeComments) {
        this(uri, null, eventSourceHandler, headers, exposeComments);
    }



    public EventSource(URI uri, SSLEngineFactory sslEngineFactory, EventSourceHandler eventSourceHandler, boolean exposeComments) {
        this(Executors.newSingleThreadExecutor(), DEFAULT_RECONNECTION_TIME_MILLIS, uri, sslEngineFactory, eventSourceHandler, null, exposeComments);
    }

    public EventSource(URI uri, SSLEngineFactory sslEngineFactory, EventSourceHandler eventSourceHandler, Map<String, String> headers, boolean exposeComments) {
        this(Executors.newSingleThreadExecutor(), DEFAULT_RECONNECTION_TIME_MILLIS, uri, sslEngineFactory, eventSourceHandler, headers, exposeComments);
    }

    public ChannelFuture connect() {
        readyState = CONNECTING;
        
        //To avoid perpetual "SocketUnresolvedException"
        int port = uri.getPort();
        if (port==-1)
        {
        	port = (uri.getScheme().equals("https"))?443:80;
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

    @Override
    public void onConnect() throws Exception {
        // flag the connection as open
        readyState = OPEN;

        // pass event to the proper handler
        eventSourceHandler.onConnect();
    }

    @Override
    public void onMessage(String event, MessageEvent message) throws Exception {
        // pass event to the proper handler
        eventSourceHandler.onMessage(event, message);
    }

    @Override
    public void onComment(String comment) throws Exception {
        // pass event to the proper handler
        eventSourceHandler.onComment(comment);
    }

    @Override
    public void onError(Throwable t) {
        // pass event to the proper handler
        eventSourceHandler.onError(t);
    }
    
    @Override
    public void onClosed(boolean willReconnect) {
        // pass event to the proper handler
        eventSourceHandler.onClosed(willReconnect);
    }
}
