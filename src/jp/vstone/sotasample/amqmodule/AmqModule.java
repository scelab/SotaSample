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

// ActiveMQを利用するモジュールの抽象クラス
public abstract class AmqModule {

	public String ip;
	public String port;
	public String robotId;
	private BlockingQueue<JSONObject> subQ;
	private Connection conn;
	private Map<String, MessageProducer> pubs;
	private Thread runThread;
	private Runnable shutdownProcedure;

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
				subQ.put(obj);
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

	public void run() throws InterruptedException, JMSException {
		subscribe("Quit");
		addShutdownHook();
		Runnable _run = () -> {
			while (true) {
				try {
					JSONObject obj = subQ.take();
					String topic = obj.getString("topic");
					if (topic.equals("Quit")) break;
					if (!obj.has("robot_id")) continue;
					if (!obj.getString("robot_id").equals(robotId)) continue;
					else procedure(obj);
				} catch (InterruptedException e) { e.printStackTrace(); }
			}
			try {
				if (shutdownProcedure != null) shutdownProcedure.run();
				conn.close();
			} catch (JMSException e) { e.printStackTrace(); }
		};
		runThread = new Thread(_run);
		runThread.start();
		runThread.join();
	}

	public void setShutdownProcedure(Runnable procedure) {
		this.shutdownProcedure = procedure;
	}

	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				try {
					JSONObject obj = new JSONObject();
					obj.put("topic", "Quit");
					subQ.put(obj);
					runThread.join();
				} catch (InterruptedException e) { e.printStackTrace(); }
	        }
	    }));
	}

	abstract protected void procedure(JSONObject obj);
}
