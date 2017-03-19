/* Copyright 2017 Innovyt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */

package com.innovyt.transit.topology;

import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.hdfs.bolt.HdfsBolt;
import org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat;
import org.apache.storm.hdfs.bolt.format.DelimitedRecordFormat;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.hdfs.bolt.format.RecordFormat;
import org.apache.storm.hdfs.bolt.sync.CountSyncPolicy;
import org.apache.storm.hdfs.bolt.sync.SyncPolicy;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.innovyt.transit.bolt.FileTimeRotationPolicyImpl;
import com.innovyt.transit.bolt.ObdWebSocketBolt;
import com.innovyt.transit.util.TopologyConfig;

public class TransitEventTopology {

	private TopologyConfig topologyConfig;
	private static final Logger LOG = LoggerFactory
			.getLogger(TransitEventTopology.class);

	public TransitEventTopology(String propertiesFile, Configuration conf) {
		topologyConfig = new TopologyConfig(propertiesFile);
	}

	@SuppressWarnings("deprecation")
	public void submitTopology() throws AlreadyAliveException,
			InvalidTopologyException,
			org.apache.storm.generated.AuthorizationException {

		StormTopology topology = buildTopology();

		Config config = new Config();
		String topologyName = topologyConfig.getValue("storm.topology.name");

		// provide the list of zookeeper servers as comma separated value
		String zkServers = topologyConfig.getValue("storm.zk.servers");

		// get the nimbus host
		String nimbusHost = topologyConfig.getValue("storm.nimbus.host");

		// get the nimbus porpt
		Integer nimbusThriftPort = topologyConfig
				.getValueAsInt("storm.nimbus.port");

		// get the zoo keeper port
		Integer zkPort = topologyConfig.getValueAsInt("storm.zk.port");

		// get the number of workers
		int numWorkers = topologyConfig.getValueAsInt("storm.num.workers");

		// get max parallelism
		int maxParallelism = topologyConfig
				.getValueAsInt("storm.topology.max.parallelism");

		config.setNumWorkers(numWorkers);
		config.setMaxTaskParallelism(maxParallelism);
		config.put(Config.NIMBUS_HOST, nimbusHost);
		config.put(Config.NIMBUS_THRIFT_PORT, nimbusThriftPort);
		config.put(Config.STORM_ZOOKEEPER_PORT, zkPort);
		config.put(Config.STORM_ZOOKEEPER_SERVERS, Arrays.asList(zkServers));
		config.setMaxSpoutPending(15000);
		config.setMessageTimeoutSecs(120);

		// Submitting topology
		StormSubmitter.submitTopology(topologyName, config, topology);

		LOG.info("successfully submitted topology");
	}

	private StormTopology buildTopology() {

		LOG.info("configuring topology parameter");
		// zookeeper properties
		String brokerZkStr = topologyConfig.getValue("storm.zk.servers");
		String zkRoot = topologyConfig.getValue("storm.zk.root");
		String zkPort = topologyConfig.getValue("storm.zk.port");

		// storm topology property
		String id = topologyConfig.getValue("storm.topology.id");

		// obd kafka properties

		String obdEventkafkaTopicName = topologyConfig
				.getValue("driver.bus.event.topic.name");
		String obdTopicConsumerId = topologyConfig
				.getValue("driver.bus.event.topic.consumer.id");
		Number obdSpoutParallelismHint = topologyConfig
				.getValueAsInt("driver.bus.storm.kafka.spout.parallelism.hint");

		// activemq properties
		String user = topologyConfig.getValue("driver.bus.activemq.user");
		String password = topologyConfig.getValue("driver.bus.activemq.password");
		String activeMQConnectionString = topologyConfig
				.getValue("driver.bus.activemq.connection.url");
		String obdTopicName = topologyConfig
				.getValue("driver.bus.activemq.topic.name");

		// hdfs proerties

		String rootPath = topologyConfig.getValue("driver.bus.hdfs.rootpath");
		String prefix = topologyConfig.getValue("driver.bus.hdfs.prefix");
		String fsUrl = topologyConfig.getValue("driver.bus.hdfs.fsUrl");
		String stagingDir = topologyConfig.getValue("driver.bus.hdfs.staging.dir");
		Float rotationTimeInMinutes = Float.valueOf(topologyConfig
				.getValue("driver.bus.hdfs.rotation.time.minutes"));
		
		// configuring obd kafka spout
		BrokerHosts brokerHosts = new ZkHosts(brokerZkStr);
		SpoutConfig obdKafkaSpoutConfig = new SpoutConfig(brokerHosts,
				obdEventkafkaTopicName, zkRoot, obdTopicConsumerId);
		obdKafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
		obdKafkaSpoutConfig.startOffsetTime = kafka.api.OffsetRequest
				.LatestTime();

		// Configuring hdfs bolt

		RecordFormat format = new DelimitedRecordFormat()
				.withFieldDelimiter(",");

		// Synchronize data buffer with the filesystem every 1000 tuples
		SyncPolicy syncPolicy = new CountSyncPolicy(1000);
		FileTimeRotationPolicyImpl rotationPolicy = new FileTimeRotationPolicyImpl(
				rotationTimeInMinutes, FileTimeRotationPolicyImpl.Units.MINUTES);

		FileNameFormat fileNameFormat = new DefaultFileNameFormat().withPath(
				rootPath + stagingDir).withPrefix(prefix);

		// Instantiate the HdfsBolt
		HdfsBolt hdfsBolt = new HdfsBolt().withFsUrl(fsUrl)
				.withFileNameFormat(fileNameFormat).withRecordFormat(format)
				.withRotationPolicy(rotationPolicy).withSyncPolicy(syncPolicy);
		// Building transit storm topology

		TopologyBuilder builder = new TopologyBuilder();

		builder.setSpout("Obd-Kakfa-Spout",
				new KafkaSpout(obdKafkaSpoutConfig), obdSpoutParallelismHint);

		builder.setBolt("hdfs_bolt", hdfsBolt, 4).shuffleGrouping(
				"Obd-Kakfa-Spout");

		ObdWebSocketBolt ObdActiveMqBolt = new ObdWebSocketBolt(user, password,
				activeMQConnectionString, obdTopicName);

		builder.setBolt("Obd-Active-Mq-Socket-Sink", ObdActiveMqBolt, 4)
				.shuffleGrouping("Obd-Kakfa-Spout");

		LOG.info("successfully built topology");
		return builder.createTopology();
	}

}
