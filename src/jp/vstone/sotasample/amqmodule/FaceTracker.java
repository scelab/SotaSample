package jp.vstone.sotasample.amqmodule;

import java.awt.Color;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.FaceDetectResult;

//顔追従するクラス
public final class FaceTracker extends AmqModule {
	private static final String TAG = "FaceTracker";
	private CRobotMem mem;
	private CSotaMotion motion;
	private CRoboCamera cam;
	private CRobotPose pose;

	public FaceTracker(String ip, String port, String robotId) {
		super(ip, port, robotId);
		mem = new CRobotMem();
		motion = new CSotaMotion(mem);
		pose = new CRobotPose();
	}

	public boolean initDev() {
		if (mem.Connect()) {
			motion.InitRobot_Sota();
			motion.ServoOn();
			cam = new CRoboCamera("/dev/video0", motion);
			cam.setEnableFaceSearch(true);
			pose.setLED_Sota(Color.BLUE, Color.BLUE, 255, Color.GREEN);
			return true;
		} else {
			CRobotUtil.Log(TAG, "CSotaMotion object connection failed.");
			return false;
		}
	}

	public void procedure(JSONObject obj) {
		String topic = obj.getString("topic");
		if (topic.equals("FaceTrackingStartOrdered")) {
			cam.StartFaceTraking();
			obj.put("topic", "FaceTracking");
			try {
				publish(obj);
			} catch (JMSException e) { e.printStackTrace(); }
			return;
		}

		if (topic.equals("FaceTracking")) {
			FaceDetectResult result = cam.getDetectResult();
			if (result.isDetect()) {
				CRobotUtil.Log(TAG, "[Detect] x = " + result.getCenterPoint(0).x + ", y = " + result.getCenterPoint(0).y);
				pose.setLED_Sota(Color.GREEN, Color.GREEN, 255, Color.GREEN);
				motion.play(pose, 500);
			}
			else {
				CRobotUtil.Log(TAG, "[Not Detect]");
				pose.setLED_Sota(Color.BLUE, Color.BLUE, 255, Color.GREEN);
				motion.play(pose, 500);
			}
			CRobotUtil.wait(500);
			try {
				publish(obj);
			} catch (JMSException e) { e.printStackTrace(); }
			return;
		}

		if (topic.equals("FaceTrackingStopOrdered")) {
			cam.StopFaceTraking();
			return;
		}
	}

	public void closeCam() {
		System.out.println(">>>1");
		if (cam.isAliveDetectTask()) cam.StopFaceTraking();
		System.out.println(">>>2");
		cam.closeCapture();
		System.out.println(">>>3");
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		FaceTracker m = new FaceTracker(ip, port, robotId);
		if (!m.initDev()) return;
		m.connect();
		m.subscribe("FaceTrackingStartOrdered");
		m.subscribe("FaceTrackingStopOrdered");
		m.subscribe("FaceTracking");
		Runnable p = () -> { m.closeCam(); };
		m.setShutdownProcedure(p);
		m.run();
	}
}
