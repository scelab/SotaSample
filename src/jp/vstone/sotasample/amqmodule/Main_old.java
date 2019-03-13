package jp.vstone.sotasample.amqmodule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.FaceDetectResult;
import jp.vstone.sotatalk.MotionAsSotaWish;
import jp.vstone.sotatalk.SpeechRecog;

public class Main_old {
	public static class GlobalVariable {
		public static Object lock = new Object();
		public static CRobotMem mem = new CRobotMem();
		public static CSotaMotion motion = new CSotaMotion(mem);
		public static boolean InitServoOn = true;
		public static boolean TRUE = true;
		public static SpeechRecog recog = new SpeechRecog(motion);
		public static MotionAsSotaWish sotawish = new MotionAsSotaWish(motion);
		public static FaceDetectResult faceresult = new FaceDetectResult();
		public static CRoboCamera robocam = new CRoboCamera("/dev/video0",motion);
	}

	public static void main(final String[] args) {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		if (GlobalVariable.mem.Connect()) {
			GlobalVariable.motion.InitRobot_Sota();
			GlobalVariable.robocam.setEnableFaceSearch(true);
			GlobalVariable.robocam.StartFaceDetect();
			if (GlobalVariable.InitServoOn) {
				GlobalVariable.motion.ServoOn();
				CRobotPose pose = new CRobotPose();
				pose.SetPose(  new  Byte[]{    1,    2,    3,    4,    5,    6,    7,    8},
						       new Short[]{    0, -900,    0,  900,    0,    0,    0,    0});
				pose.SetTorque(new  Byte[]{    1,    2,    3,    4,    5,    6,    7,    8},
						       new Short[]{  100,  100,  100,  100,  100,  100,  100,  100});
				pose.SetLed(   new  Byte[]{    0,    1,    2,    8,    9,   10,   11,   12,   13},
						       new Short[]{    0, -255,    0,  180,   80,    0,  180,   80,    0});
				GlobalVariable.motion.play(pose,1500);
				CRobotUtil.wait(1500);
			}
			FaceTracker       ft = new FaceTracker(ip, port, robotId);
			LookExecutor      le = new LookExecutor(ip, port, robotId);
			SpeechRecognizer  sr = new SpeechRecognizer(ip, port, robotId);
			UtteranceExecutor ue = new UtteranceExecutor(ip, port, robotId);
			List<AmqModule> l = new ArrayList<>(Arrays.asList(ft, le, sr, ue));
			try {
				ft.start();
				le.start();
				sr.start();
				ue.start();
				for (AmqModule m : l) m.join();
			} catch(Exception e) { e.printStackTrace();
			} finally {
				if (GlobalVariable.robocam.isAliveFaceDetectTask()) GlobalVariable.robocam.StopFaceDetect();
				GlobalVariable.motion.ServoOff();
				System.exit(0);
			}
		}
	}
}
