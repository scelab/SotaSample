package jp.vstone.sotamain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.FaceDetectResult;
import jp.vstone.sotatalk.MotionAsSotaWish;
import jp.vstone.sotatalk.SpeechRecog;

public class Globals {
	/* Note: GCにアンロードされるとstatic変数が初期化される可能性あり */
	public static final Object lock = new Object();
	public static Map<String, List<Double>> fixedPositions = new HashMap<>();
	public static Map<String, List<Double>> positions = new HashMap<>();
	public static Map<String, List<Double>> convertPositions(JSONObject obj) {
		Map<String, List<Double>> m = new HashMap<>();
		for (String key : obj.keySet()) {
			JSONArray array = obj.getJSONArray(key);
			List<Double> values = array.toList().stream().map(v -> (Double)v).collect(Collectors.toList());
			m.put(key, values);
		}
		return m;
	}


	public static class RobotVars {
		public static String robotId;
		public static boolean isUsing3DSensor;
		public static short lastHeadYawToHuman;
		public static short lastHeadPitchToHuman;
		public static final CRobotMem mem = new CRobotMem();
		public static final CSotaMotion motion = new CSotaMotion(mem);
		public static final SpeechRecog recog = new SpeechRecog(motion);
		public static final MotionAsSotaWish sotawish = new MotionAsSotaWish(motion);
		public static final FaceDetectResult faceresult = new FaceDetectResult();
		public static final CRoboCamera robocam = new CRoboCamera("/dev/video0",motion);
		public static boolean init() {
			if (mem.Connect()) {
				motion.InitRobot_Sota();
				robocam.setEnableFaceSearch(true);
				robocam.StartFaceDetect();
				motion.ServoOn();
				CRobotPose pose = new CRobotPose();
				pose.SetPose(  new  Byte[]{    1,    2,    3,    4,    5,    6,    7,    8},
						       new Short[]{    0, -900,    0,  900,    0,    0,    0,    0});
				pose.SetTorque(new  Byte[]{    1,    2,    3,    4,    5,    6,    7,    8},
						       new Short[]{  100,  100,  100,  100,  100,  100,  100,  100});
				pose.SetLed(   new  Byte[]{    0,    1,    2,    8,    9,   10,   11,   12,   13},
						       new Short[]{    0, -255,    0,  180,   80,    0,  180,   80,    0});
				motion.play(pose,1500);
				CRobotUtil.wait(1500);
				return true;
			}
			return false;
		}
		public static void close() {
			if (Globals.RobotVars.robocam.isAliveFaceDetectTask()) Globals.RobotVars.robocam.StopFaceDetect();
			Globals.RobotVars.motion.ServoOff();
		}
	}

	public static class AmqVars {
		public static String ip;
		public static String port;
	}


}
