package jp.vstone.sotasample.amqmodule;

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;
import jp.vstone.sotatalk.SpeechRecog.RecogResult;

public final class UtteranceExecutor extends AmqModule {
	private Byte[] servoIds;
	private Byte[] ledIds;
	private Map<String, JSONArray> positions;
	public UtteranceExecutor(String ip, String port, String robotId) {
		super(ip, port, robotId);
		servoIds = new Byte[] {CSotaMotion.SV_BODY_Y,     CSotaMotion.SV_HEAD_R,  CSotaMotion.SV_L_ELBOW,
				               CSotaMotion.SV_L_SHOULDER, CSotaMotion.SV_R_ELBOW, CSotaMotion.SV_R_SHOULDER};
		ledIds = new Byte[16];
		for(byte i = 0; i < 16; ++i) ledIds[i] = new Byte(i);
		positions = new HashMap<>();
	}

	protected void procedure(JSONObject obj) {
		String topic = obj.getString("topic");
		if (topic.equals("BehaviorSelected")) {
			if (!obj.has("speaker"))   return;
			if (!obj.has("addressee")) return;
			if (!obj.has("utterance")) return;
			if (!robotId.equals(obj.getString("speaker"))) return;
			look(obj);
			say(obj);
			if (obj.has("tags")) {
				boolean isQuestion = obj.getJSONArray("tags").toList().stream().anyMatch(t -> t.equals("question"));
				if (isQuestion) speechRecognize(obj);
			}
		} else if (topic.equals("PositionUpdated")) {
			updatePositions(obj);
		}
	}

	private void look(JSONObject obj) {
		JSONArray lookerPos = obj.getJSONArray(robotId);
		JSONArray targetPos = obj.getJSONArray(obj.getString("addressee"));
		double vec_x = targetPos.getDouble(0) - lookerPos.getDouble(0);
		double vec_y = targetPos.getDouble(1) - lookerPos.getDouble(1);
		double vec_z = targetPos.getDouble(2) - lookerPos.getDouble(2);
		short yaw  = (short)((Math.toDegrees(Math.atan2(vec_y, vec_z)) * 10) - lookerPos.getDouble(3));
		short pitch = (short)((Math.toDegrees(Math.atan2(vec_x, Math.sqrt(vec_z*vec_z + vec_y*vec_y))) * 10) - lookerPos.getDouble(4));
		Short[] current_degs = GlobalVariable.motion.getReadpos();
		// 1sec per 180degree
		short msec = (short)(Math.max(Math.abs(yaw - current_degs[5]), Math.abs(pitch - current_degs[6])) * 10 / 18);
		CRobotPose pose = new CRobotPose();
		pose.SetPose(servoIds, new Short[]{yaw , pitch});
		GlobalVariable.motion.LockServoHandle(servoIds);
		CRobotUtil.Log("UtteranceExecutor[look]", System.currentTimeMillis()/1000.0 + " yaw=" + yaw + " pitch=" + pitch + " msec=" + msec);
		GlobalVariable.motion.play(pose, msec);
		CRobotUtil.wait(msec);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
		obj.put("topic", "LookCompleted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	private void say(JSONObject obj) {
		obj.put("topic", "UtteranceStarted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
		GlobalVariable.motion.LockServoHandle(servoIds);
		GlobalVariable.motion.LockLEDHandle(ledIds);
		String s = obj.getString("utterance");
		CRobotUtil.Log("UtteranceExecutor[say]", System.currentTimeMillis()/1000.0 + " " + s);
		GlobalVariable.sotawish.Say(s);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
		GlobalVariable.motion.UnLockLEDHandle(ledIds);
		obj.put("topic", "UtteranceCompleted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	private void speechRecognize(JSONObject obj) {
		CRobotUtil.Log("UtteranceExecutor[speechRecognize]", System.currentTimeMillis()/1000.0 + " started.");
		RecogResult result = GlobalVariable.recog.getRecognition(5000);
		obj.put("topic", "SpeechRecognitionCompleted");
		if (result.recognized) obj.put("basic_result", result.basicresult);
		else                   obj.put("basic_result", "timeout");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	private void updatePositions(JSONObject obj) {
		for (String key : obj.keySet()) {
			positions.put(key, obj.getJSONArray(key));
		}
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
		UtteranceExecutor m = new UtteranceExecutor(ip, port, robotId);
		m.connect();
		m.subscribe("UtteranceOrdered");
		m.run();
	}
}
