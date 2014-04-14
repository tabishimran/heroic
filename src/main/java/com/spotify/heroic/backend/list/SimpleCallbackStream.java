package com.spotify.heroic.backend.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;

import com.spotify.heroic.async.Callback;
import com.spotify.heroic.async.CallbackStream;
import com.spotify.heroic.async.CancelReason;
import com.spotify.heroic.backend.BackendManager.DataPointGroup;
import com.spotify.heroic.backend.BackendManager.QueryMetricsResult;
import com.spotify.heroic.backend.MetricBackend;
import com.spotify.heroic.backend.RowStatistics;
import com.spotify.heroic.backend.kairosdb.DataPoint;

@Slf4j
public final class SimpleCallbackStream implements
        Callback.StreamReducer<MetricBackend.DataPointsResult, QueryMetricsResult> {
    private final Map<String, String> tags;

    private final Queue<MetricBackend.DataPointsResult> results = new ConcurrentLinkedQueue<MetricBackend.DataPointsResult>();

    SimpleCallbackStream(Map<String, String> tags) {
        this.tags = tags;
    }

    @Override
    public void finish(CallbackStream<MetricBackend.DataPointsResult> stream,
            Callback<MetricBackend.DataPointsResult> callback,
            MetricBackend.DataPointsResult result) throws Exception {
        results.add(result);
    }

    @Override
    public void error(CallbackStream<MetricBackend.DataPointsResult> stream,
            Callback<MetricBackend.DataPointsResult> callback, Throwable error)
            throws Exception {
        log.error("Result failed: " + error, error);
    }

    @Override
    public void cancel(CallbackStream<MetricBackend.DataPointsResult> stream,
            Callback<MetricBackend.DataPointsResult> callback,
            CancelReason reason) throws Exception {
    }

    @Override
    public QueryMetricsResult done(int successful, int failed, int cancelled)
            throws Exception {
        final List<DataPoint> datapoints = joinRawResults();

        final RowStatistics rowStatistics = new RowStatistics(successful,
                failed, cancelled);

        final List<DataPointGroup> groups = new ArrayList<DataPointGroup>();
        groups.add(new DataPointGroup(tags, datapoints));

        return new QueryMetricsResult(groups, datapoints.size(), 0,
                rowStatistics);
    }

    private List<DataPoint> joinRawResults() {
        final List<DataPoint> datapoints = new ArrayList<DataPoint>();

        for (final MetricBackend.DataPointsResult result : results) {
            datapoints.addAll(result.getDatapoints());
        }

        Collections.sort(datapoints);
        return datapoints;
    }
}