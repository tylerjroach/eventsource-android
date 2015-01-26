package tylerjroach.com.eventsource_android.impl;

import java.util.regex.Pattern;

import tylerjroach.com.eventsource_android.EventSourceHandler;
import tylerjroach.com.eventsource_android.MessageEvent;

/**
 * <a href="http://dev.w3.org/html5/eventsource/#event-stream-interpretation">Interprets an event stream</a>
 * and dispatches messages to the {@link tylerjroach.com.eventsource_android.EventSourceHandler}.
 */
public class EventStreamParser {
    private static final String DATA = "data";
    private static final String ID = "id";
    private static final String EVENT = "event";
    private static final String RETRY = "retry";

    private static final String DEFAULT_EVENT = "message";
    private static final String EMPTY_STRING = "";
    private static final Pattern DIGITS_ONLY = Pattern.compile("^[\\d]+$");

    private final EventSourceHandler eventSourceHandler;
    private final ConnectionHandler connectionHandler;
    private final String origin;

    private StringBuffer data = new StringBuffer();
    private String lastEventId;
    private String eventName = DEFAULT_EVENT;

    public EventStreamParser(String origin, EventSourceHandler eventSourceHandler, ConnectionHandler connectionHandler) {
        this.eventSourceHandler = eventSourceHandler;
        this.origin = origin;
        this.connectionHandler = connectionHandler;
    }

    public void line(String line) {
        int colonIndex;
        if (line.trim().isEmpty()) {
            dispatchEvent();
        } else if (line.startsWith(":")) {
            // ignore
        } else if ((colonIndex = line.indexOf(":")) != -1) {
            String field = line.substring(0, colonIndex);
            String value = line.substring(colonIndex + 1).replaceFirst(" ", EMPTY_STRING);
            processField(field, value);
        } else {
            processField(line.trim(), EMPTY_STRING); // The spec doesn't say we need to trim the line, but I assume that's an oversight.
        }
    }

    private void processField(String field, String value) {
        if (DATA.equals(field)) {
            data.append(value).append("\n");
        } else if (ID.equals(field)) {
            lastEventId = value;
        } else if (EVENT.equals(field)) {
            eventName = value;
        } else if (RETRY.equals(field) && isNumber(value)) {
            connectionHandler.setReconnectionTimeMillis(Long.parseLong(value));
        }
    }

    private boolean isNumber(String value) {
        return DIGITS_ONLY.matcher(value).matches();
    }

    private void dispatchEvent() {
        if (data.length() == 0) {
            return;
        }
        String dataString = data.toString();
        if (dataString.endsWith("\n")) {
            dataString = dataString.substring(0, dataString.length() - 1);
        }
        MessageEvent message = new MessageEvent(dataString, lastEventId, origin);
        connectionHandler.setLastEventId(lastEventId);
        try {
            eventSourceHandler.onMessage(eventName, message);
        } catch (Exception e) {
            eventSourceHandler.onError(e);
        }
        data = new StringBuffer();
        eventName = DEFAULT_EVENT;
    }

    public void lines(String lines) {
        String[] lineArray = lines.split("\n", -1);
        for (String line : lineArray) {
            line(line);
        }
    }

}
