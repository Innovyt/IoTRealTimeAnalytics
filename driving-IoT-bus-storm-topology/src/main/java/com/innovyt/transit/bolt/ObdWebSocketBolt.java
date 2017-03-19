
/* Copyright 2017 Innovyt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */
package com.innovyt.transit.bolt;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObdWebSocketBolt implements IRichBolt {

	private static final long serialVersionUID = 1L;
	private static final long ACTIVEMQ_MESSAGE_TTL = 50000;

	private static final Logger LOG = LoggerFactory
			.getLogger(ObdWebSocketBolt.class);

	private OutputCollector collector;
	private String user;
	private String password;
	private String activeMQConnectionString;
	private String topicName;
	private Session session = null;
	private Connection connection = null;
	private ActiveMQConnectionFactory connectionFactory = null;

	private HashMap<String, MessageProducer> producers = new HashMap<String, MessageProducer>();

	public ObdWebSocketBolt(String user, String password,
			String activeMQConnectionString, String topicName) {

		this.user = user;
		this.password = password;
		this.activeMQConnectionString = activeMQConnectionString;
		this.topicName = topicName;
	}

	@Override
	public void prepare(Map stormConf, TopologyContext context,
			OutputCollector collector) {
		this.collector = collector;
		try {
			connectionFactory = new ActiveMQConnectionFactory(this.user,
					this.password, this.activeMQConnectionString);
			connection = connectionFactory.createConnection();
			LOG.info("connecting to active mq");
			connection.start();
			LOG.info("active mq connection started");
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			producers.put(this.topicName,
					getActiveMqTopicProducer(session, this.topicName));

		} catch (JMSException e) {
			LOG.error("Error in Active mq connection", e);
			return;
		}

	}

	private MessageProducer getActiveMqTopicProducer(Session session,
			String topic) {
		try {
			Topic topicDestination = session.createTopic(topic);
			MessageProducer topicProducer = session
					.createProducer(topicDestination);
			topicProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			LOG.info("created active mq topic producer");
			return topicProducer;
		} catch (JMSException e) {
			LOG.error("Error creating producer for topic", e);
			throw new RuntimeException("Error creating producer for topic");
		}
	}

	@Override
	public void execute(Tuple input) {

		LOG.info("About to process obd web socket event[" + input + "]");
		String obddata = input.getString(0);

		LOG.info("sending obd event to active mq topic");
		try {
			sendObdEventToActiveMqTopic(obddata);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		collector.ack(input);

	}

	private void sendObdEventToActiveMqTopic(String obddata)
			throws JSONException, JMSException {

		TextMessage message = session.createTextMessage(obddata);
		MessageProducer producer = producers.get(this.topicName);
		producer.send(message, producer.getDeliveryMode(),
				producer.getPriority(), ACTIVEMQ_MESSAGE_TTL);
		LOG.info("sent obd event to topic");

	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void cleanup() {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}

}
