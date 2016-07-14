# eventsource-android

An Android EventSource (SSE) Library
This is a Java implementation of the EventSource - a client for Server-Sent Events. The implementation is based on Netty

jCenter Gradle import

    compile 'com.tylerjroach:eventsource:1.2.0'

This project is based of off EventSource-Java:
https://github.com/aslakhellesoy/eventsource-java
https://github.com/TomMettam/eventsource-java

One addition made to the original source is that headers can now be passed in the method to include authorization tokens, etc in the request.

Note:
In order to use eventsource, you must create and connect the event source from a separate thread. If you are planning to update a view's ui from the handler, you will need to use runOnUI or create a handler tied to the main thread.

Example implementation:

    Thread eventThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSource = new EventSource.Builder(new URI(eventUrl))
                        .eventHandler(new SSEHandler())
                        .headers(extraHeaderParameters)
                        .build();
                    eventSource.connect();
                } catch(URISyntaxException e) {
                    Log.v("Error starting eventsource", "True");
                }
            }
        });
    }
    eventThread.start();


    private class SSEHandler implements EventSourceHandler {

        public SSEHandler() {
        }
        
        @Override
        public void onConnect() {
            Log.v("SSE Connected", "True");
        }

        @Override
        public void onMessage(String event, MessageEvent message) {
            Log.v("SSE Message", event);
            Log.v("SSE Message: ", message.lastEventId);
            Log.v("SSE Message: ", message.data);
        }

        @Override
        public void onComment(String comment) {
           //comments only received if exposeComments turned on
           Log.v("SSE Comment", comment);
        }

        @Override
        public void onError(Throwable t) {
            Log.v("SSE Error", "True");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            Log.v("SSE Stacktrace", sw.toString());

        }

        @Override
        public void onClosed(boolean willReconnect) {
            Log.v("SSE Closed", "reconnect? " + willReconnect);
        }
        
To stop event source, make sure to run eventSource.close(), as well as remove the handler and thread instance.


I'm currently working on a rewrite to remove Netty in favor of OkHttp, as well as using a reactive approach

If you have a pull request, please follow square-android style guides found here: https://github.com/square/java-code-styles
