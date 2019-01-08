package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;
import jp.vstone.sotatalk.SpeechRecog.RecogResult;

public final class SpeechRecognizer extends AmqModule {
	public SpeechRecognizer(String ip, String port, String robotId) {
		super(ip, port, robotId);
	}

	protected void procedure(JSONObject obj) {
		if (!obj.getString("listener").equals(robotId)) return;
		CRobotUtil.Log("[SpeechRecognizer]", obj.getString("timestamp") + " SpeechRecognitionOrdered");
		RecogResult result = GlobalVariable.recog.getRecognition(5000);
		obj.put("topic", "SpeechRecognitionCompleted");
		if (result.recognized) obj.put("basic_result", result.basicresult);
		else                   obj.put("basic_result", "timeout");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	public void start() throws JMSException, InterruptedException {
		connect();
		subscribe("SpeechRecognitionOrdered");
		run();
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		SpeechRecognizer m = new SpeechRecognizer(ip, port, robotId);
		m.connect();
		m.subscribe("SpeechRecognitionOrdered");
		m.run();
	}
}
