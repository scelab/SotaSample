package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.RobotLib.CPlayWave;
import jp.vstone.sotatalk.TextToSpeechSota;

//音声を出力するクラス
public final class WavePlayer extends AmqModule {
	private static final String TAG = "WavePlayer";

	public WavePlayer(String ip, String port, String robotId) {
		super(ip, port, robotId);
	}

	public void procedure(JSONObject obj) {
		String topic = obj.getString("topic");
		if (topic.equals("WavePlayOrdered")) {
			if (obj.has("content")) {
				String c = obj.getString("content");
				obj.put("topic", "WavePlayStarted");
				try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
				CPlayWave.PlayWave(TextToSpeechSota.getTTS(c), true);
				obj.put("topic", "WavePlayCompleted");
				try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }

				String a = obj.getJSONObject("behavior").getJSONObject("content").getString("addressee");
				if (a.contains("H") && (c.endsWith("?") || c.endsWith("？"))) {
					obj.put("topic", "SpeechRecognitionOrdered");
					try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
				}
			}
		}
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		WavePlayer m = new WavePlayer(ip, port, robotId);
		m.connect();
		m.subscribe("WavePlayOrdered");
		m.run();
	}
}
