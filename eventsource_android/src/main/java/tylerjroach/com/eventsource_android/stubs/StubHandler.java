package tylerjroach.com.eventsource_android.stubs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tylerjroach.com.eventsource_android.EventSourceHandler;
import tylerjroach.com.eventsource_android.MessageEvent;
import tylerjroach.com.eventsource_android.impl.ConnectionHandler;

public class StubHandler implements ConnectionHandler, EventSourceHandler {
    private Long reconnectionTimeMillis;
    private String lastEventId;
    private boolean connected;
    private Map<String, List<MessageEvent>> messagesByEvent = new HashMap<String, List<MessageEvent>>();
    private List<Throwable> errors = new ArrayList<Throwable>();

    @Override
    public void setReconnectionTimeMillis(long reconnectionTimeMillis) {
        this.reconnectionTimeMillis = reconnectionTimeMillis;
    }

    @Override
    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    @Override
    public void onConnect() throws Exception {
        connected = true;
    }
    
    @Override
    public void onClosed(boolean willReconnect){
        connected = false;
    }

    @Override
    public void onMessage(String event, MessageEvent message) throws Exception {
        getMessageEvents(event).add(message);
    }

    @Override
    public void onError(Throwable t) {
        errors.add(t);
    }

    public Long getReconnectionTimeMillis() {
        return reconnectionTimeMillis;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public boolean isConnected() {
        return connected;
    }

    public List<MessageEvent> getMessageEvents(String event) {
        List<MessageEvent> events = messagesByEvent.get(event);
        if(events == null) {
            events = new ArrayList<MessageEvent>();
            messagesByEvent.put(event, events);
        }
        return events;
    }

    public List<MessageEvent> getMessageEvents() {
        List<MessageEvent> results = new ArrayList<MessageEvent>();
        for (Entry<String, List<MessageEvent>> messageEvents : messagesByEvent.entrySet()) {
            results.addAll(messageEvents.getValue());
        }
        return results;
    }

    public List<Throwable> getErrors() {
        return errors;
    }
}
