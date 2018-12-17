package jp.vstone.sotasample.amqmodule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jms.JMSException;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.FaceDetectResult;

//顔認識をするクラス
public final class FaceDetector extends AmqModule {
	private static final String TAG = "FaceDetector";
	private CRobotMem mem;
	private CSotaMotion motion;
	private CRoboCamera cam;

	public FaceDetector(String ip, String port, String robotId) {
		super(ip, port, robotId);
		mem = new CRobotMem();
		motion = new CSotaMotion(mem);
	}

	public boolean initDev() {
		if (mem.Connect()) {
			motion.InitRobot_Sota();
			motion.ServoOn();
			cam = new CRoboCamera("/dev/video0", motion);
			cam.setEnableFaceSearch(true);
			cam.StartFaceDetect();
			return true;
		} else {
			CRobotUtil.Log(TAG, "CSotaMotion object connection failed.");
			return false;
		}
	}

	public void procedure(JSONObject obj) {
		String topic = obj.getString("topic");
		if (topic.equals("FaceDetectionOrdered")) {
			try {
				publish(makeObj());
			} catch (JMSException e) { e.printStackTrace(); }
		}
	}

	public void closeCam() {
		cam.StopFaceDetect();
		cam.closeCapture();
	}

	private JSONObject makeObj() throws JMSException {
		Byte[] ids = motion.getDefaultIDs();
		Short[] degs = motion.getReadpos();
		FaceDetectResult result = cam.getDetectResult();
		JSONObject obj = new JSONObject();
		obj.put("topic", "FaceDetectionCompleted");
		obj.put("robot_id", robotId);
		if (result.isDetect()) {
//			obj.put("age", result.getAge());
//			obj.put("angle_pitch", result.getAnglePitch());
//			obj.put("angle_pitch_move_score", result.getAnglePitchMoveScore());
//			obj.put("angle_roll", result.getAngleRoll());
//			obj.put("angle_roll_move_score", result.getAngleRollMoveScore());
//			obj.put("anglresult.getAngleYaw());
//			obj.put("angle_yaw_move_score", result.getAngleYawMoveScore());
//			obj.put("blink_left", result.getBlinkLeft());
//			obj.put("blink_right", result.getBlinkRight());
//			obj.put("face_num", result.getFaceNum());
//			obj.put("fps", result.getFPS());
//			obj.put("smile", result.getSmile());
//			obj.put("face_vector", result.getFaceVectoer());
//			obj.put("gaze_vector", result.getGazeVectoer());
//			obj.put("age_sex_detect", result.isAgeSexDetect());
//			obj.put("blink_ditect", result.isBlinkDetect());
//			obj.put("detect", result.isDetect());
//			obj.put("female", result.isFemale());
//			obj.put("male", result.isMale());
			obj.put("height", cam.getcap().getHeight());
			obj.put("width",  cam.getcap().getWidth());
			List<JSONObject> list = new ArrayList<JSONObject>();
			for (int i = 0; i < result.getFaceNum(); i++) {
				JSONObject obj2 = new JSONObject();
				obj2.put("height", result.getHeight(i));
				obj2.put("width",  result.getWidth(i));
				JSONObject pos  = new JSONObject();
				pos.put("x", result.getCenterPoint(i).x);
				pos.put("y", result.getCenterPoint(i).y);
				obj2.put("center_point", pos);
				list.add(obj2);
			}
			obj.put("basic_results", new JSONArray(list));
		}
		obj.put("axis_ids", new JSONArray(Arrays.asList(ids)));
		obj.put("degrees", new JSONArray(Arrays.asList(degs)));
		return obj;
	}



	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		FaceDetector m = new FaceDetector(ip, port, robotId);
		if (!m.initDev()) return;
		m.connect();
		m.subscribe("FaceDetectionOrdered");
		Runnable p = () -> { m.closeCam(); };
		m.setShutdownProcedure(p);
		m.run();

	}
}
