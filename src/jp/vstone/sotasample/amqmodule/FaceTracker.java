package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;

//顔追従するクラス
public final class FaceTracker extends AmqModule {

	public FaceTracker(String ip, String port, String robotId) {
		super(ip, port, robotId);
	}

	protected void procedure(JSONObject obj) {
		if (!obj.getString("looker").equals(robotId)) return;
		String topic = obj.getString("topic");
		if (topic.equals("FaceTrackingStartOrdered")) {
			GlobalVariable.robocam.StartFaceTraking();
			obj.put("topic", "FaceTrackingStartCompleted");
			try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
		}
		else if (topic.equals("FaceTrackingStopOrdered")) {
			if (GlobalVariable.robocam.isAliveFaceDetectTask()) GlobalVariable.robocam.StopFaceTraking();
			obj.put("topic", "FaceTrackingStopCompleted");
			try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
		}
	}

	public void start() throws JMSException, InterruptedException {
		connect();
		subscribe("FaceTrackingStartOrdered");
		subscribe("FaceTrackingStopOrdered");
		run();
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		FaceTracker m = new FaceTracker(ip, port, robotId);
		m.start();
	}
}
