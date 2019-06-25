# flink-cep 
- 基于flink,siddhi,aviator的一个动态规则引擎

# 使用方法
- 启动参数 -jd "" ,如下json：
{
  "jobId": "test_1",
  "runType": "new",
  "timeType": "ProcessingTime",
  "cql": "from inputStream select timestamp, id, aviator(name,'([\\w0-8]+)@\\w+[\\.\\w+]+') as name, aviator(price+price) as price insert into  outputStream",
  "outputStreamId": "outputStream",
  "sources": [
    {
      "id": "inputStream",
      "inputFields": "id String,name String,price Double,timestamp Long",
      "type": "kafka",
      "host": "192.168.204.181:9092,192.168.204.182:9092,192.168.204.183:9092",
      "auth": "",
      "resource": "siddhi02"
    }
  ],
  "sinks": [
    {
      "id": "1",
      "type": "hbase",
      "host": "192.168.204.181:2181,192.168.204.182:2181,192.168.204.183:2181",
      "auth": "",
      "store": "flink"
    }
  ]
}

