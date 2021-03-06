/*
 * Copyright (c) 2018 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.heroic;

import static junit.framework.TestCase.assertEquals;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.spotify.heroic.common.Duration;
import com.spotify.heroic.common.Series;
import com.spotify.heroic.consumer.pubsub.EmulatorHelper;
import com.spotify.heroic.consumer.pubsub.PubSubConsumerModule;
import com.spotify.heroic.consumer.schemas.Spotify100;
import com.spotify.heroic.consumer.schemas.spotify100.Version;
import com.spotify.heroic.ingestion.IngestionModule;
import com.spotify.heroic.instrumentation.OperationsLogImpl;
import com.spotify.heroic.metric.MetricCollection;
import com.spotify.heroic.metric.MetricManagerModule;
import com.spotify.heroic.metric.MetricModule;
import com.spotify.heroic.metric.MetricType;
import com.spotify.heroic.metric.WriteMetric;
import com.spotify.heroic.metric.memory.MemoryMetricModule;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.jcip.annotations.NotThreadSafe;
import org.junit.After;
import org.junit.Rule;
import org.testcontainers.containers.GenericContainer;

@NotThreadSafe
public class PubSubConsumerIT extends AbstractConsumerIT {
    @Rule
    public GenericContainer container = new GenericContainer("bigtruedata/gcloud-pubsub-emulator")
        .withExposedPorts(8085)
        .withCommand("start", "--host-port", "0.0.0.0:8085");

    private final String topic = "topic1";
    private final String subscription = "sub1";
    private final String project = "heroic-it-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OperationsLogImpl opLog;
    private Publisher publisher;

    private String emulatorEndpoint() {
        return container.getContainerIpAddress() + ":" + container.getFirstMappedPort();
    }

    @Override
    protected HeroicConfig.Builder setupConfig() {
        opLog = new OperationsLogImpl();
        final MetricModule backingStore = MemoryMetricModule.builder().build();

        try {
            publisher = EmulatorHelper.publisher(project, topic, emulatorEndpoint());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MetricModule metricModule = new LoggingMetricModule(backingStore, opLog);
        return HeroicConfig
            .builder()
            .stopTimeout(Duration.of(5, TimeUnit.SECONDS))
            .consumers(ImmutableList.of(PubSubConsumerModule
                .builder()
                .topicId(topic)
                .schema(Spotify100.class)
                .subscriptionId(subscription)
                .projectId(project)
                .endpoint(emulatorEndpoint())
            ))
            .ingestion(IngestionModule.builder().updateMetrics(true))
            .metrics(MetricManagerModule.builder().backends(ImmutableList.of(metricModule)));
    }


    @Override
    protected Consumer<WriteMetric.Request> setupConsumer(Version version) {
        return request -> {
            final MetricCollection mc = request.getData();

            final List<MetricType> list = List.of(MetricType.POINT, MetricType.DISTRIBUTION_POINTS);

            if (!list.contains(mc.getType())) {
                throw new RuntimeException("Unsupported metric type: " + mc.getType());
            }

            final Series series = request.getSeries();

            final List<TMetric> metrics = createTestMetricCollection(request);

            for (TMetric metric : metrics){
                Object jsonMetric = createJsonMetric(metric, series);
                publish(jsonMetric);
            }
        };
    }

    private void publish(Object jsonMetric){
        final PubsubMessage pubsubMessage;

        try {
            String message = objectMapper.writeValueAsString(jsonMetric);
            ByteString data = ByteString.copyFromUtf8(message);
            pubsubMessage = PubsubMessage.newBuilder().setData(data).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        publisher.publish(pubsubMessage);
    }

    @Override
    public void abstractTeardown() throws Exception {
        super.abstractTeardown();
        EmulatorHelper.deleteSubscription(project, subscription, emulatorEndpoint());
    }

    @After
    public void verifyTransactionality() {
        long writeRequests = 0;
        long writeCompletions = 0;

        for (OperationsLogImpl.OpType op : opLog.getLog()) {
            if (op == OperationsLogImpl.OpType.WRITE_REQUEST) {
                writeRequests++;
            }

            if (op == OperationsLogImpl.OpType.WRITE_COMPLETE) {
                writeCompletions++;
            }
        }

        assertEquals(writeRequests, writeCompletions);
    }
}
