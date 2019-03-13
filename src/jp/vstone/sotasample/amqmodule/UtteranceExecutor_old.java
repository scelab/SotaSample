package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;

public final class UtteranceExecutor_old extends AmqModule {
	private Byte[] servoIds;
	private Byte[] ledIds;
	public UtteranceExecutor_old(String ip, String port, String robotId) {
		super(ip, port, robotId);
		servoIds = new Byte[] {CSotaMotion.SV_BODY_Y,     CSotaMotion.SV_HEAD_R,  CSotaMotion.SV_L_ELBOW,
				               CSotaMotion.SV_L_SHOULDER, CSotaMotion.SV_R_ELBOW, CSotaMotion.SV_R_SHOULDER};
		ledIds = new Byte[16];
		for(byte i = 0; i < 16; ++i) ledIds[i] = new Byte(i);
	}

	protected void procedure(JSONObject obj) {
		if (!obj.getString("speaker").equals(robotId)) return;
		String c = obj.getString("utterance");
		obj.put("topic", "UtteranceStarted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
		GlobalVariable.motion.LockServoHandle(servoIds);
		GlobalVariable.motion.LockLEDHandle(ledIds);
		CRobotUtil.Log("UtteranceExecutor", obj.getString("timestamp") + " UtteranceOrdered");
		GlobalVariable.sotawish.Say(c);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
		GlobalVariable.motion.UnLockLEDHandle(ledIds);
		obj.put("topic", "UtteranceCompleted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	public void start() throws JMSException, InterruptedException {
		connect();
		subscribe("UtteranceOrdered");
		run();
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		UtteranceExecutor_old m = new UtteranceExecutor_old(ip, port, robotId);
		m.connect();
		m.subscribe("UtteranceOrdered");
		m.run();
	}
}
