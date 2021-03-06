package com.roc.util;

import com.roc.entity.JobDetail;
import com.roc.sink.*;
import com.roc.source.FlinkSimpleElasticsearchSource;
import com.roc.source.FlinkSimpleHBaseSource;
import com.roc.source.FlinkSimpleKafkaSource;
import com.roc.source.FlinkSimpleMysqlSource;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.joor.Reflect;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : jelly.wang
 * @date : Created in 2021-01-26 下午10:15
 * @description: source 和 sink 构建工具类
 */
public final class SourceSinkConstructor {
    // source
    private final static Map<String, String> SOURCES = new HashMap<String, String>() {{
        put("HBASE", "createHbaseSource");
        put("KAFKA", "createKafkaSource");
        put("MYSQL", "createMysqlSource");
        put("ELASTICSEARCH", "createElasticsearchSource");
    }};
    // sink
    private final static Map<String, String> SINKS = new HashMap<String, String>() {{
        put("HBASE", "createHbaseSink");
        put("KAFKA", "createKafkaSink");
        put("ELASTICSEARCH", "createElasticsearchSink");
        put("MYSQL", "createMysqlSink");
        put("REDIS", "createRedisSink");
    }};

    private SourceSinkConstructor() {
    }

    private static JobDetail.SourceDetail sourceDetail;
    private static JobDetail.SinkDetail sinkDetail;


    public static SourceFunction newSourceFunction(JobDetail.SourceDetail sd) {
        sourceDetail = sd;
        return Reflect.on(SourceSinkConstructor.class).call(SOURCES.get(sd.getType().toUpperCase())).get();
    }

    public static SinkFunction newSinkFunction(JobDetail.SinkDetail sd) {
        sinkDetail = sd;
        return Reflect.on(SourceSinkConstructor.class).call(SINKS.get(sd.getType().toUpperCase())).get();
    }

    /**
     * source instance
     *
     * @return
     */
    private static SourceFunction createHbaseSource() {
        return new FlinkSimpleHBaseSource(sourceDetail.getHost(), sourceDetail.getStorage());
    }

    private static SourceFunction createKafkaSource() {
        return new FlinkSimpleKafkaSource(sourceDetail.getHost(), sourceDetail.getId(), sourceDetail.getStorage()).build();
    }

    private static SourceFunction createMysqlSource() {
        String[] userAndPass = sourceDetail.getAuth().split(":");
        return new FlinkSimpleMysqlSource(sourceDetail.getHost(), userAndPass[0], userAndPass[1], sourceDetail.getStorage());
    }

    private static SourceFunction createElasticsearchSource() {
        return new FlinkSimpleElasticsearchSource(sourceDetail.getId(), sourceDetail.getAuth(), sourceDetail.getHost(), sourceDetail.getStorage());
    }

    /**
     * sink instance
     *
     * @return
     */
    private static SinkFunction createElasticsearchSink() {
        return new FlinkSimpleElasticsearchSink(sinkDetail.getId(), sinkDetail.getAuth(), sinkDetail.getHost(), sinkDetail.getStorage()).build();
    }

    private static SinkFunction createHbaseSink() {
        return new FlinkSimpleHbaseSink(sinkDetail.getHost(), sinkDetail.getStorage()).build();
    }

    private static SinkFunction createKafkaSink() {
        return new FlinkSimpleKafkaSink(sinkDetail.getHost(), sinkDetail.getStorage()).build();
    }

    private static SinkFunction createMysqlSink() {
        String[] userAndPass = sinkDetail.getAuth().split(":");
        return new FlinkSimpleMysqlSink(sinkDetail.getHost(), userAndPass[0], userAndPass[1], sinkDetail.getStorage());
    }

    private static SinkFunction createRedisSink() {
        return new FlinkSimpleRedisSink(sinkDetail.getHost()).build();
    }
}
