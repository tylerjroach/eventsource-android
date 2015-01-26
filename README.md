# eventsource-android

An Android EventSource (SSE) Library
This is a Java implementation of the EventSource - a client for Server-Sent Events. The implementation is based on Netty

This project is based of off EventSource-Java:
https://github.com/aslakhellesoy/eventsource-java
https://github.com/TomMettam/eventsource-java

One addition made to the original source is that headers can now be passed in the method to include authorization tokens, etc in the request.

Note:
In order to use eventsource, you must create and connect the event source from a separate thread.

I've packaged the code into an android library. Just assembleRelease in gradle to create the neccessary AAR file. In the near future, I will upload the library to jcenter.

Example implementation:

    Thread eventThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSource = new EventSource(Uri, new SSEHandler(), extraHeaderParameters);
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
