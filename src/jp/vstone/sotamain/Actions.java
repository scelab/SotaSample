package jp.vstone.sotamain;

import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotatalk.SpeechRecog.RecogResult;

public class Actions {

	public static void say(String s) {
//		Byte[] servoIds = new Byte[] { CSotaMotion.SV_BODY_Y,     CSotaMotion.SV_HEAD_R,  CSotaMotion.SV_L_ELBOW,
//	                                   CSotaMotion.SV_L_SHOULDER, CSotaMotion.SV_R_ELBOW, CSotaMotion.SV_R_SHOULDER };
		Byte[] servoIds = new Byte[] { CSotaMotion.SV_L_ELBOW, CSotaMotion.SV_L_SHOULDER, CSotaMotion.SV_R_ELBOW, CSotaMotion.SV_R_SHOULDER };
		Byte[] ledIds = new Byte[16];
		for(byte i = 0; i < 16; ++i) ledIds[i] = new Byte(i);
		Globals.RobotVars.motion.LockServoHandle(servoIds);
		Globals.RobotVars.motion.LockLEDHandle(ledIds);
		Globals.RobotVars.sotawish.Say(s);
		Globals.RobotVars.motion.UnLockServoHandle(servoIds);
		Globals.RobotVars.motion.UnLockLEDHandle(ledIds);
	}

	public static RecogResult speechRecognize(int msec) {
		RecogResult result = Globals.RobotVars.recog.getRecognition(msec);
		return result;
	}

}
