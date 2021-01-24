package com.jelly.flink;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.jelly.flink.entity.DataStreamDetail;
import com.jelly.flink.entity.JobDetail;
import com.jelly.flink.functions.AviatorRegexFunctionExtension;
import com.jelly.flink.util.AbstractStreamEnv;
import com.jelly.flink.util.SourceSinkConstructor;
import org.apache.commons.cli.*;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.siddhi.SiddhiCEP;
import org.apache.flink.streaming.siddhi.SiddhiStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CepMain
 * <p>
 *
 * @author jelly.wang
 * @create 2019/03/26
 */
public class CepMain extends AbstractStreamEnv {
    private final static String JOB_NAME = CepMain.class.getSimpleName();
    private final static Logger LOG = LoggerFactory.getLogger(CepMain.class.getName());

    {
        init();
    }

    public static void main(String[] args) {
        // 解析参数
        final CommandLineParser parser = new DefaultParser();
        final Options options = new Options();
        options.addOption("jd", "jobDetail", true, "Set the rules for running the flink job.");

        JobDetail jobDetail;
        try {
            CommandLine commandLine = parser.parse(options, args);
            if (commandLine.hasOption("jd")) {
                jobDetail = JSON.parseObject(commandLine.getOptionValue("jd"), JobDetail.class);
            } else {
                new HelpFormatter().printHelp(" ", options);
                return;
            }
        } catch (ParseException e) {
            LOG.error(e.getMessage());
            return;
        }

        final CepMain nm = new CepMain();
        // 获取执行环境
        StreamExecutionEnvironment env = nm.getEnv();
        // 设置数据自定义时间
        switch (jobDetail.getTimeType()) {
            case "EventTime":
                env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
                break;
            case "IngestionTime":
                env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);
                break;
            default:
                env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime);
                break;
        }

        List<DataStreamDetail> dataStreamDetails = createDataStream(jobDetail, env);

        DataStream<Map<String, Object>> output = buildSiddhiStream(dataStreamDetails, env).cql(jobDetail.getCql()).returnAsMap(jobDetail.getOutputStreamId());

        jobDetail.getSinks().forEach((sink) -> output.rebalance().addSink(SourceSinkConstructor.newSinkFunction(sink)));

        // 执行任务
        try {
            env.execute(JOB_NAME);
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }


    @Override
    public void init() {
        super.inited();
    }

    /**
     * 将flink流转成siddhi流
     *
     * @param dataStreamDetailList
     * @return
     */
    public static SiddhiStream.ExecutableStream buildSiddhiStream(List<DataStreamDetail> dataStreamDetailList, StreamExecutionEnvironment env) {
        // 设置siddhi
        SiddhiCEP siddhiCEP = SiddhiCEP.getSiddhiEnvironment(env);
        // 注册AVIATOR 函数库
        siddhiCEP.registerExtension("aviator", AviatorRegexFunctionExtension.class);
        DataStreamDetail dataStreamDetail = dataStreamDetailList.remove(0);
        // 单流
        SiddhiStream.SingleSiddhiStream<LinkedHashMap<String, Object>> singleSiddhiStream = siddhiCEP.from(dataStreamDetail.getInputStreamId(), dataStreamDetail.getDataStream(), getTypeInformation(dataStreamDetail.getTypeList()), dataStreamDetail.getFieldList());
        if (dataStreamDetailList.isEmpty()) {
            return singleSiddhiStream;
        }

        // 多流
        SiddhiStream.UnionSiddhiStream<LinkedHashMap<String, Object>> unionSiddhiStream = null;
        for (DataStreamDetail dd : dataStreamDetailList) {
            if (null == unionSiddhiStream) {
                unionSiddhiStream = singleSiddhiStream.union(dd.getInputStreamId(), dd.getDataStream(), getTypeInformation(dd.getTypeList()), dd.getFieldList());
            } else {
                unionSiddhiStream = unionSiddhiStream.union(dd.getInputStreamId(), dd.getDataStream(), dd.getFieldList());
            }
        }
        return unionSiddhiStream;
    }

    /**
     * 构建数据源
     *
     * @param jobDetail
     * @param env
     * @return
     */
    private static List<DataStreamDetail> createDataStream(JobDetail jobDetail, StreamExecutionEnvironment env) {
        List<DataStreamDetail> dataStreamDetails = new ArrayList<>();
        jobDetail.getSources().forEach((source) -> {
            DataStreamDetail dataStreamDetail = new DataStreamDetail();
            dataStreamDetail.setInputStreamId(source.getId());
            dataStreamDetail.setOutputStreamId(jobDetail.getOutputStreamId());

            DataStream<String> dataStream = env.addSource(SourceSinkConstructor.newSourceFunction(source));
            DataStream<LinkedHashMap<String, Object>> convertDataStream = dataStream.rebalance().map(new MapFunction<String, LinkedHashMap<String, Object>>() {
                @Override
                public LinkedHashMap<String, Object> map(String value) throws Exception {
                    return JSON.parseObject(value, LinkedHashMap.class, Feature.OrderedField);
                }
            });
            dataStreamDetail.setDataStream(convertDataStream);

            // 获取数据源中字段名和类型
            final String inputFields = source.getInputFields();
            final String[] splitInputFields = inputFields.split(",");
            int len = splitInputFields.length;
            final String[] fieldList = new String[len], typeList = new String[len];
            for (int i = 0; i < len; i++) {
                final String[] ft = splitInputFields[i].split(" ");
                fieldList[i] = ft[0];
                typeList[i] = ft[1];
            }
            dataStreamDetail.setFieldList(fieldList);
            dataStreamDetail.setTypeList(typeList);

            dataStreamDetails.add(dataStreamDetail);
        });
        return dataStreamDetails;
    }

    /**
     * 获取数据类型
     *
     * @param javaType
     * @return
     */
    private static TypeInformation[] getTypeInformation(String[] javaType) {
        if (null == javaType) {
            throw new IllegalArgumentException("there is no supported");
        }
        int len = javaType.length;
        TypeInformation[] typeInformations = new TypeInformation[len];
        for (int i = 0; i < len; i++) {
            switch (javaType[i].toUpperCase()) {
                case "STRING":
                    typeInformations[i] = Types.STRING;
                    break;
                case "LONG":
                    typeInformations[i] = Types.LONG;
                    break;
                case "INTEGER":
                    typeInformations[i] = Types.INT;
                    break;
                case "DOUBLE":
                    typeInformations[i] = Types.DOUBLE;
                    break;
                case "CHAR":
                    typeInformations[i] = Types.CHAR;
                    break;
                case "BOOLEAN":
                    typeInformations[i] = Types.BOOLEAN;
                    break;
                case "FLOAT":
                    typeInformations[i] = Types.FLOAT;
                    break;
                default:
                    throw new IllegalArgumentException("the " + javaType[i].toUpperCase() + "is no supported");
            }
        }
        return typeInformations;
    }
}
