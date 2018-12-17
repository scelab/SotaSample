package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotatalk.SpeechRecog;
import jp.vstone.sotatalk.SpeechRecog.RecogResult;

//音声認識するクラス
public final class SpeechRecognizer extends AmqModule {
	private static final String TAG = "SpeechRecognizer";
	private CRobotMem mem;
	private CSotaMotion motion;
	private SpeechRecog recog;

	public SpeechRecognizer(String ip, String port, String robotId) {
		super(ip, port, robotId);
		mem = new CRobotMem();
		motion = new CSotaMotion(mem);
		recog = new SpeechRecog(motion);
	}

	public boolean initDev() {
		if (mem.Connect()) {
			motion.InitRobot_Sota();
			motion.ServoOn();
			return true;
		} else {
			CRobotUtil.Log(TAG, "CSotaMotion object connection failed.");
			return false;
		}
	}

	public void procedure(JSONObject obj) {
		String topic = obj.getString("topic");
		if (topic.equals("SpeechRecognitionOrdered")) {
			RecogResult result = recog.getRecognition(5000);
			if (result.recognized) obj.put("basic_result", result.basicresult);
			else                   obj.put("basic_result", "timeout");
			obj.put("topic", "SpeechRecognitionCompleted");
			try {
				publish(obj);
			} catch (JMSException e) { e.printStackTrace(); }
		}
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		SpeechRecognizer m = new SpeechRecognizer(ip, port, robotId);
		if (!m.initDev()) return;
		m.connect();
		m.subscribe("SpeechRecognitionOrdered");
		m.run();
	}
}
