package com.spotify.heroic.metric.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.spotify.heroic.async.Future;
import com.spotify.heroic.async.CancelReason;
import com.spotify.heroic.async.StreamReducer;
import com.spotify.heroic.metric.model.FetchData;
import com.spotify.heroic.metric.model.MetricGroup;
import com.spotify.heroic.metric.model.MetricGroups;
import com.spotify.heroic.model.DataPoint;
import com.spotify.heroic.model.Statistics;

@Slf4j
@RequiredArgsConstructor
public final class SimpleCallbackStream implements StreamReducer<FetchData, MetricGroups> {
    private final Map<String, String> group;

    private final Queue<FetchData> results = new ConcurrentLinkedQueue<FetchData>();

    @Override
    public void resolved(Future<FetchData> callback, FetchData result) throws Exception {
        results.add(result);
    }

    @Override
    public void failed(Future<FetchData> callback, Exception error) throws Exception {
        log.error("Error encountered when processing request", error);
    }

    @Override
    public void cancelled(Future<FetchData> callback, CancelReason reason) throws Exception {
        log.error("Cancel encountered when processing request", reason);
    }

    @Override
    public MetricGroups resolved(int successful, int failed, int cancelled) throws Exception {
        if (failed > 0)
            throw new Exception("Some time series could not be fetched from the database");

        final List<DataPoint> datapoints = joinRawResults();

        final Statistics statistics = Statistics.builder().row(new Statistics.Row(successful, failed, cancelled))
                .aggregator(new Statistics.Aggregator(datapoints.size(), 0, 0)).build();

        final List<MetricGroup> groups = new ArrayList<MetricGroup>();
        groups.add(new MetricGroup(group, datapoints));

        return MetricGroups.fromResult(groups, statistics);
    }

    private List<DataPoint> joinRawResults() {
        final List<DataPoint> datapoints = new ArrayList<DataPoint>();

        for (final FetchData result : results) {
            datapoints.addAll(result.getDatapoints());
        }

        Collections.sort(datapoints);
        return datapoints;
    }
}