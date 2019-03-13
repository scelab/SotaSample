package jp.vstone.sotamain;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import jp.vstone.sotatalk.SpeechRecog.RecogResult;

public class Main {

	public static void main(final String[] args) {
		if (!setParams("conf.yaml")) return;
		if (!Globals.RobotVars.init()) return;
		List<Connection> conns = new ArrayList<>();
		try {
			conns.add(subscribe("BehaviorSelected",   new BehaviorSelectedListener()));
			conns.add(subscribe("UtteranceStarted",   new UtteranceStartedListener()));
			conns.add(subscribe("UtteranceCompleted", new UtteranceCompletedListener()));
			conns.add(subscribe("LookCompleted",      new LookCompletedListener()));
			conns.add(subscribe("PositionUpdated",    new PositionUpdatedListener()));
			waitQuit();
		} catch (JMSException e) {
			e.printStackTrace();
		}
		FaceTracker.stop();
		conns.forEach(c -> { try { c.close(); } catch (JMSException e) { e.printStackTrace(); } });
		Globals.RobotVars.close();
	}

	private static void waitQuit() throws JMSException {
		final ConnectionFactory connFactory = new ActiveMQConnectionFactory(String.format("tcp://%s:%s", Globals.AmqVars.ip, Globals.AmqVars.port));
		final Connection conn = connFactory.createConnection();
		conn.start();
		final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		final Destination dest = sess.createTopic("Quit");
		final MessageConsumer cons = sess.createConsumer(dest);
		cons.receive();
		conn.close();
	}

	private static Connection subscribe(String topic, MessageListener listener) throws JMSException {
		final ConnectionFactory connFactory = new ActiveMQConnectionFactory(String.format("tcp://%s:%s", Globals.AmqVars.ip, Globals.AmqVars.port));
		final Connection conn = connFactory.createConnection();
		conn.start();
		final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		final Destination dest = sess.createTopic(topic);
		final MessageConsumer cons = sess.createConsumer(dest);
		cons.setMessageListener(listener);
		return conn;
	}

	private static void publish(String topic, JSONObject obj) throws JMSException {
		final ConnectionFactory connFactory = new ActiveMQConnectionFactory(String.format("tcp://%s:%s", Globals.AmqVars.ip, Globals.AmqVars.port));
	    final Connection conn = connFactory.createConnection();
	    conn.start();
	    final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    final Destination dest = sess.createTopic(topic);
	    final MessageProducer prod = sess.createProducer(dest);
	    final Message msg = sess.createTextMessage(obj.toString());
	    prod.send(msg);
	    conn.close();
	}

	private static class BehaviorSelectedListener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				//System.out.println(">>>1 " + obj.toString());
				JSONObject content = obj.getJSONObject("behavior").getJSONObject("content");
				if (!content.has("speaker") || !content.has("addressee") || !content.has("utterance")) return;
				String speaker   = content.getString("speaker");
				String addressee = content.getString("addressee");
				String utterance = content.getString("utterance");
				if (!Globals.RobotVars.robotId.equals(speaker)) return;
				FaceTracker.start(addressee);
				publish("LookCompleted", makeLookCompletedEvent(speaker, addressee));
				publish("UtteranceStarted", makeUtteranceStartedEvent(speaker, addressee, utterance));
				Actions.say(content.getString("utterance"));
				publish("UtteranceCompleted", makeUtteranceCompletedEvent(speaker, addressee, utterance));
				if (content.has("tags") && content.getJSONArray("tags").toList().contains("question")) {
					RecogResult result = Actions.speechRecognize(6000);
					publish("SpeechRecognized", makeSpeechRecognizedEvent(speaker, addressee, utterance, result.basicresult));
				}
			} catch (JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	private static class UtteranceStartedListener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				if (!obj.has("speaker") || !obj.has("addressee") || !obj.has("utterance")) return;
				String speaker   = obj.getString("speaker");
				String addressee = obj.getString("addressee");
				if (Globals.RobotVars.robotId.equals(speaker) || Globals.RobotVars.robotId.equals(addressee)) return;
				FaceTracker.start(speaker);
			} catch (JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	private static class UtteranceCompletedListener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				if (!obj.has("speaker") || !obj.has("addressee") || !obj.has("utterance") || !obj.has("tags")) return;
				String speaker    = obj.getString("speaker");
				String addressee  = obj.getString("addressee");
				List<String> tags = obj.getJSONArray("tags").toList().stream().map(t -> t.toString()).collect(Collectors.toList());
				if (Globals.RobotVars.robotId.equals(speaker) || Globals.RobotVars.robotId.equals(addressee)) return;
				if (!tags.contains("question")) return;
				FaceTracker.start(addressee);
			} catch (JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	private static class LookCompletedListener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				if (!obj.has("looker") || !obj.has("target")) return;
				String looker = obj.getString("looker");
				String target = obj.getString("target");
				if (!Globals.RobotVars.robotId.equals(target)) return;
				FaceTracker.start(looker);
			} catch (JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	private static class PositionUpdatedListener implements MessageListener {
		@Override
		public void onMessage(Message message) {
			try {
				JSONObject obj = new JSONObject(((TextMessage)message).getText());
				Map<String, List<Double>> m = new HashMap<>();
				for (String key : obj.keySet()) {
					List<Object> temp  = obj.getJSONArray(key).toList();
					List<Double> value = temp.stream().map(p -> (Double)p).collect(Collectors.toList());
					m.put(key, value);
				}
				synchronized (Globals.lock) {
					Globals.positions = m;
					Globals.positions.putAll(Globals.fixedPositions);
				}

			} catch (JSONException | JMSException e) { e.printStackTrace(); }
		}
	}

	private static boolean setParams(String path) {
		try {
			InputStream input = new FileInputStream(new File(path));
		    Yaml yaml = new Yaml();
		    Map<String, Object> map = (Map<String, Object>)yaml.load(input);
		    Map<String, String> amq = (Map<String, String>)map.get("AmqVars");
		    Globals.AmqVars.ip   = amq.get("ip");
		    Globals.AmqVars.port = amq.get("port");
		    Map<String, Object> robot = (Map<String, Object>)map.get("RobotVars");
		    Globals.RobotVars.robotId = robot.get("robotId").toString();
		    Globals.RobotVars.isUsing3DSensor = (boolean)robot.get("isUsing3DSensor");
		    Map<String, List<Double>> ps  = (Map<String, List<Double>>)map.get("Positions");
		    for (Map.Entry<String, List<Double>> e : ps.entrySet()) {
		    	Globals.fixedPositions.put(e.getKey(), e.getValue());
		    	Globals.positions.put(e.getKey(), e.getValue());
		    }
		    return true;
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return false;
		}
	}

	private static JSONObject makeLookCompletedEvent(String looker, String target) {
		String msg = String.format("{\"event\":\"LookCompleted\",\"looker\":\"%s\",\"target\":\"%s\"}", looker, target);
		return new JSONObject(msg);
	}

	private static JSONObject makeUtteranceStartedEvent(String speaker, String addressee, String utterance) {
		String msg = String.format("{\"event\":\"UtteranceStarted\",\"speaker\":\"%s\",\"addressee\":\"%s\",\"utterance\":\"%s\"}", speaker, addressee, utterance);
		return new JSONObject(msg);
	}

	private static JSONObject makeUtteranceCompletedEvent(String speaker, String addressee, String utterance) {
		String msg = String.format("{\"event\":\"UtteranceCompleted\",\"speaker\":\"%s\",\"addressee\":\"%s\",\"utterance\":\"%s\"}", speaker, addressee, utterance);
		return new JSONObject(msg);
	}

	private static JSONObject makeSpeechRecognizedEvent(String speaker, String addressee, String utterance, String basicresult) {
		String msg = String.format("{\"event\":\"SpeechRecognitionCompleted\",\"speaker\":\"%s\",\"addressee\":\"%s\",\"utterance\":\"%s\",\"basicresult\":\"%s\"}", speaker, addressee, utterance, basicresult);
		return new JSONObject(msg);
	}

	private static JSONObject makeQuitEvent() {
		String msg = "{\"event\":\"Quit\"}";
		return new JSONObject(msg);
	}


}
