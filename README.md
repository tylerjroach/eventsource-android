# eventsource-android

An Android EventSource (SSE - Server Sent Events) Library

jCenter Gradle import

    compile 'com.tylerjroach:eventsource:1.2.1'

Example implementation:
    
    private SSEHandler sseHandler;
    
    private void startEventSource() {
        eventSource = new EventSource.Builder(new URI(eventUrl))
            .eventHandler(sseHandler)
            .headers(extraHeaderParameters)
            .build();
        eventSource.connect();
    }
           
    private void stopEventSource() {
        if (eventsource!= null)
            eventSource.close();
        sseHandler = null;
    }
    
    /**
    * All callbacks are currently returned on executor thread. 
    * If you want to update the ui from a callback, make sure to post to main thread
    */

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
            //ignore ssl NPE on eventSource.close()
        }

        @Override
        public void onClosed(boolean willReconnect) {
            Log.v("SSE Closed", "reconnect? " + willReconnect);
        }
        
To stop event source, make sure to run eventSource.close()


I'm currently working on a rewrite to remove Netty in favor of OkHttp, as well as using a reactive approach
If you have a pull request, please follow square-android style guides found here: https://github.com/square/java-code-styles


This project is based of off EventSource-Java:
https://github.com/aslakhellesoy/eventsource-java
https://github.com/TomMettam/eventsource-java
