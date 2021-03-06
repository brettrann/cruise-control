/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.monitor.sampling;

import com.linkedin.cruisecontrol.metricdef.MetricDef;
import com.linkedin.cruisecontrol.monitor.sampling.MetricSample;
import com.linkedin.kafka.cruisecontrol.metricsreporter.exception.UnknownVersionException;
import com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef;
import java.nio.ByteBuffer;
import org.apache.kafka.common.TopicPartition;

import java.util.Date;
import java.util.Map;

import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.CPU_USAGE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.DISK_USAGE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.FETCH_RATE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.LEADER_BYTES_IN;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.LEADER_BYTES_OUT;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.MESSAGE_IN_RATE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.PRODUCE_RATE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.REPLICATION_BYTES_IN_RATE;
import static com.linkedin.kafka.cruisecontrol.monitor.metricdefinition.KafkaCruiseControlMetricDef.REPLICATION_BYTES_OUT_RATE;
import static java.nio.charset.StandardCharsets.*;


/**
 * The class that hosts one the metrics samples of the following resources:
 * CPU, DISK, Network Bytes In, Network Bytes Out.
 */
public class PartitionMetricSample extends MetricSample<String, PartitionEntity> {
  private static final byte CURRENT_VERSION = 1;

  private final int _brokerId;

  public PartitionMetricSample(int brokerId, TopicPartition tp) {
    super(new PartitionEntity(tp));
    _brokerId = brokerId;
  }

  /**
   * The id of the broker from which the metrics are from.
   */
  public int brokerId() {
    return _brokerId;
  }

  /**
   * Give the number or metrics that has been recorded.
   */
  public int numMetrics() {
    return _valuesByMetricId.size();
  }

  /**
   * This method serialize the metric sample using a simple protocol.
   * 1 byte  - version
   * 4 bytes - brokerId
   * 8 bytes - CPU Utilization
   * 8 bytes - DISK Utilization
   * 8 bytes - Network Inbound Utilization
   * 8 bytes - Network Outbound Utilization.
   * 8 bytes - Produce Request Rate
   * 8 bytes - Fetch Request Rate
   * 8 bytes - Messages In Per Sec
   * 8 bytes - Replication Bytes In Per Sec
   * 8 bytes - Replication Bytes Out Per Sec
   * 8 bytes - Sample time
   * 4 bytes - partition id
   * N bytes - topic string bytes
   */
  public byte[] toBytes() {
    MetricDef metricDef = KafkaCruiseControlMetricDef.metricDef();
    byte[] topicStringBytes = entity().group().getBytes(UTF_8);
    // Allocate memory:
    ByteBuffer buffer = ByteBuffer.allocate(89 + topicStringBytes.length);
    buffer.put(CURRENT_VERSION);
    buffer.putInt(_brokerId);
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(CPU_USAGE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(DISK_USAGE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(LEADER_BYTES_IN.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(LEADER_BYTES_OUT.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(PRODUCE_RATE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(FETCH_RATE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(MESSAGE_IN_RATE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(REPLICATION_BYTES_IN_RATE.name()).id()));
    buffer.putDouble(_valuesByMetricId.get(metricDef.metricInfo(REPLICATION_BYTES_OUT_RATE.name()).id()));
    buffer.putLong(_sampleTime);
    buffer.putInt(entity().tp().partition());
    buffer.put(topicStringBytes);
    return buffer.array();
  }

  public static PartitionMetricSample fromBytes(byte[] bytes) throws UnknownVersionException {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    // Not used at this point.
    byte version = buffer.get();
    if (version > CURRENT_VERSION) {
      throw new UnknownVersionException("Metric sample version " + version +
          " is higher than current version " + CURRENT_VERSION);
    }
    switch (version) {
      case 0:
        return readV0(buffer);
      case 1:
        return readV1(buffer);
      default:
        throw new IllegalStateException("Should never be here.");
    }
  }

  @Override
  public String toString() {
    MetricDef metricDef = KafkaCruiseControlMetricDef.metricDef();
    StringBuilder builder = new StringBuilder().append("{");
    for (Map.Entry<Integer, Double> entry : _valuesByMetricId.entrySet()) {
      builder.append(metricDef.metricInfo(entry.getKey()).name())
          .append("=")
          .append(entry.getValue().toString())
          .append(", ");
    }
    builder.delete(builder.length() - 2, builder.length()).append("}");
    return String.format("[brokerId: %d, Partition: %s, time: %s, metrics: %s]", _brokerId, entity().tp(),
        new Date(_sampleTime), builder.toString());
  }

  private static PartitionMetricSample readV0(ByteBuffer buffer) {
    MetricDef metricDef = KafkaCruiseControlMetricDef.metricDef();
    int brokerId = buffer.getInt();
    int partition = buffer.getInt(45);
    String topic = new String(buffer.array(), 49, buffer.array().length - 49, UTF_8);
    PartitionMetricSample sample = new PartitionMetricSample(brokerId, new TopicPartition(topic, partition));
    sample.record(metricDef.metricInfo(CPU_USAGE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(DISK_USAGE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(LEADER_BYTES_IN.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(LEADER_BYTES_OUT.name()), buffer.getDouble());
    sample.close(buffer.getLong());
    return sample;
  }

  private static PartitionMetricSample readV1(ByteBuffer buffer) {
    MetricDef metricDef = KafkaCruiseControlMetricDef.metricDef();
    int brokerId = buffer.getInt();
    int partition = buffer.getInt(85);
    String topic = new String(buffer.array(), 89, buffer.array().length - 89, UTF_8);
    PartitionMetricSample sample = new PartitionMetricSample(brokerId, new TopicPartition(topic, partition));
    sample.record(metricDef.metricInfo(CPU_USAGE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(DISK_USAGE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(LEADER_BYTES_IN.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(LEADER_BYTES_OUT.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(PRODUCE_RATE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(FETCH_RATE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(MESSAGE_IN_RATE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(REPLICATION_BYTES_IN_RATE.name()), buffer.getDouble());
    sample.record(metricDef.metricInfo(REPLICATION_BYTES_OUT_RATE.name()), buffer.getDouble());
    sample.close(buffer.getLong());
    return sample;
  }
}
