package com.junlanli.zipkin.brave.mongo;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import zipkin.Constants;
import zipkin.Endpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Copyright (C) 2015 - 2017 JUNLAN LI All Rights Reserved.
 * <p>
 * Created on 23/06/2017.
 *
 * @author: lijunlan888@gmail.com
 */
public class MongoTracingListener implements CommandListener {

    private static final Logger logger = Logger.getLogger(MongoTracingListener.class);

    private Tracing tracing;

    private Map<ConnectionId, Span> spanCache;

    @Autowired
    MongoTracingListener(Tracing tracing) {
        this.tracing = tracing;
        this.spanCache = new ConcurrentHashMap<>();
    }

    public static CommandListener create(Tracing tracing) {
        return new MongoTracingListener(tracing);
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        Tracer tracer = tracing.tracer();
        Span span = tracer.nextSpan();
        if (!span.isNoop()) {
            span.kind(Span.Kind.CLIENT).name(event.getCommandName());
            span.tag("mongo.query", event.getCommand().toJson());
            ServerAddress serverAddress = event.getConnectionDescription().getServerAddress();
            Endpoint.Builder builder = Endpoint.builder().serviceName("mongo-" + event.getDatabaseName()).port(serverAddress.getPort());
            builder.parseIp(serverAddress.getHost());
            span.remoteEndpoint(builder.build());
            span.start();
        }
        spanCache.put(event.getConnectionDescription().getConnectionId(), span);
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        ConnectionId connectionId = event.getConnectionDescription().getConnectionId();
        Span span = spanCache.get(connectionId);
        spanCache.remove(connectionId);
        if (span == null) {
            String msg = String.format("Successfully executed command '%s' with id %s "
                            + "on connection '%s' to server '%s'",
                    event.getCommandName(),
                    event.getRequestId(),
                    event.getConnectionDescription()
                            .getConnectionId(),
                    event.getConnectionDescription().getServerAddress());
            logger.info("span is null, ignore tracing. " + msg);
            return;
        }
        span.finish();
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        ConnectionId connectionId = event.getConnectionDescription().getConnectionId();
        Span span = spanCache.get(connectionId);
        spanCache.remove(connectionId);
        if (span == null) {
            String msg = String.format("Failed execution of command '%s' with id %s "
                            + "on connection '%s' to server '%s' with exception '%s'",
                    event.getCommandName(),
                    event.getRequestId(),
                    event.getConnectionDescription()
                            .getConnectionId(),
                    event.getConnectionDescription().getServerAddress(),
                    event.getThrowable());
            logger.info("span is null, ignore tracing. " + msg);
            return;
        }
        span.tag(Constants.ERROR, event.getThrowable().getMessage());
        span.finish();
    }
}
