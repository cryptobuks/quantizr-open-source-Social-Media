package quanta.instrument;

import java.util.LinkedList;
import java.util.List;
import quanta.util.ThreadLocals;

public class PerfMonEvent {

    public PerfMonEvent root;
    public List<PerfMonEvent> subEvents;

    // NO GETTERS/SETTERS. Not needed or wanted.
    public long duration;
    public String event;
    public String user;
    public long lastTime;

    // Pass event as 'null' to start a chaining set of event timings, where this constructor
    // doesn't represent processing done, but the beginning of a set of operations
    public PerfMonEvent(long duration, String event, String user) {
        this.user = user;
        lastTime = System.currentTimeMillis();

        if (event != null) {
            this.duration = duration;
            this.event = event;

            Instrument.add(this);
            PerfMonEvent rootEvent = ThreadLocals.getRootEvent();

            // if there's no root event for this thread make this one the root.
            if (rootEvent == null) {
                ThreadLocals.setRootEvent(this);
            }
            // else add this even to the subevents.
            else {
                // create lazily
                if (rootEvent.subEvents == null) {
                    rootEvent.subEvents = new LinkedList<>();
                }
                rootEvent.subEvents.add(this);
                root = rootEvent;
            }
        }
    }

    public void chain(String event) {
        long nowTime = System.currentTimeMillis();
        new PerfMonEvent(nowTime - lastTime, event, this.user);
        lastTime = nowTime;
    }
}
