package jp.vstone.sotasample.amqmodule;

import javax.jms.JMSException;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;

public final class LookExecutor extends AmqModule {
	private Byte[] servoIds;
	public LookExecutor (String ip, String port, String robotId) {
		super(ip, port, robotId);
		servoIds = new Byte[] {CSotaMotion.SV_HEAD_Y, CSotaMotion.SV_HEAD_P};
	}

	protected void procedure(JSONObject obj) {
		if (!obj.getString("looker").equals(robotId)) return;
		if (!playPose(obj)) return;
		obj.put("topic", "LookCompleted");
		try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
	}

	private boolean playPose(JSONObject obj) {
		JSONArray lookerPos = obj.getJSONArray("looker_pos");
		JSONArray targetPos = obj.getJSONArray("target_pos");
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
		CRobotUtil.Log("LookExecutor", obj.getString("timestamp") + " yaw=" + yaw + " pitch=" + pitch + " msec=" + msec);
		GlobalVariable.motion.play(pose, msec);
		CRobotUtil.wait(msec);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
		return true;
	}

	public void start() throws JMSException, InterruptedException {
		connect();
		subscribe("LookOrdered");
		run();
	}

	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		LookExecutor m = new LookExecutor(ip, port, robotId);
		m.connect();
		m.subscribe("LookOrdered");
		m.run();
	}
}

