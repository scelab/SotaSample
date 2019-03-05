package jp.vstone.sotasample.amqmodule;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.json.JSONException;
import org.json.JSONObject;


public abstract class AmqModule {

	public String ip;
	public String port;
	public String robotId;
	private BlockingQueue<JSONObject> subQ;
	private Connection conn;
	private Map<String, MessageProducer> pubs;
	private Thread runThread;

	public AmqModule (String ip, String port, String robotId) {
		this.ip = ip;
		this.port = port;
		this.robotId = robotId;
		subQ = new LinkedBlockingQueue<>();
		pubs = new HashMap<>();
	}

	public void connect() throws JMSException {
		final ConnectionFactory connFactory = new ActiveMQConnectionFactory(String.format("tcp://%s:%s", ip, port));
	    conn = connFactory.createConnection();
	    conn.start();
	}

	private class Listener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				obj.put("timestamp", String.valueOf(message.getJMSTimestamp()));
				AmqModule.this.subQ.put(obj);
			} catch (InterruptedException | JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	public void subscribe(String topic) throws JMSException {
		Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		MessageConsumer cons = sess.createConsumer(sess.createTopic(topic));
		cons.setMessageListener(new Listener());
	}

	public void publish(JSONObject obj) throws JMSException {
		String topic = obj.getString("topic");
		if (!pubs.containsKey("topic")) {
			Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		    MessageProducer prod = sess.createProducer(sess.createTopic(topic));
			pubs.put(topic, prod);
		}
		TextMessage msg = new ActiveMQTextMessage();
		msg.setText(obj.toString());
		pubs.get(topic).send(msg);
	}

	public void publish(String topic, JSONObject obj) throws JMSException {
		obj.put("topic", topic);
		publish(obj);
	}

	protected void run() throws InterruptedException, JMSException {
		subscribe("Quit");
		Runnable _run = () -> {
			while (true) {
				try {
					JSONObject obj = subQ.take();
					String topic = obj.getString("topic");
					if (topic.equals("Quit")) break;
					procedure(obj);
				} catch (InterruptedException e) { e.printStackTrace(); }
			}
			try {
				conn.close();
			} catch (JMSException e) { e.printStackTrace(); }
		};
		runThread = new Thread(_run);
		runThread.start();
	}

	public void join() throws InterruptedException {
		runThread.join();
	}

	abstract protected void procedure(JSONObject obj);
}
