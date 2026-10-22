package com.myroutine.common.outbox;

import com.myroutine.common.web.TraceIds;
import org.slf4j.MDC;

public final class EventContext {

    public static void run(EventEnvelope envelope, Runnable action) {
        MDC.put(TraceIds.MDC_KEY, envelope.traceId() != null ? envelope.traceId() : TraceIds.newId());
        MDC.put("eventId", envelope.eventId().toString());
        try {
            action.run();
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
            MDC.remove("eventId");
        }
    }

}
