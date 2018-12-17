package jp.vstone.sotasample.amqmodule;

import java.util.Arrays;

import javax.jms.JMSException;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;

//軸を動かすためのクラス
public final class AxesController extends AmqModule {
	private static final String TAG = "AxesController";
	private CRobotMem mem;
	private CSotaMotion motion;

	public AxesController (String ip, String port, String robotId) {
		super(ip, port, robotId);
		mem = new CRobotMem();
		motion = new CSotaMotion(mem);
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
		if (topic.equals("AxesMoveOrdered")) {
			if ( obj.has("axis_ids") && obj.has("degrees")) {
				if (!playPose(motion, obj)) return;
				try {
					publish(makeObj(motion, robotId));
				} catch (JMSException e) { e.printStackTrace(); }
			}
		}
	}

	private JSONObject makeObj(CSotaMotion motion, String robotId) throws JMSException {
		Byte[] ids = motion.getDefaultIDs();
		Short[] degs = motion.getReadpos();
		JSONObject obj = new JSONObject();
		obj.put("topic", "AxesMoveCompleted");
		obj.put("robot_id", robotId);
		obj.put("axis_ids", new JSONArray(Arrays.asList(ids)));
		obj.put("degrees", new JSONArray(Arrays.asList(degs)));
		return obj;
	}

	private boolean playPose(CSotaMotion motion, JSONObject obj) {
		Byte[] ids = getAxisIds(obj);
		Short[] degs = getDegrees(obj);
		if (ids.length != degs.length) return false;
		int msec = obj.has("msec") ? obj.getInt("msec") : 1000;
		CRobotPose pose = new CRobotPose();
		pose.SetPose(ids, degs);
		motion.play(pose, msec);
		CRobotUtil.wait(msec);
		return true;
	}

	private Byte[] getAxisIds(JSONObject obj) {
		JSONArray j = obj.getJSONArray("axis_ids");
		Byte[] ids = new Byte[j.length()];
		for (int i = 0; i < j.length(); i++) {
			ids[i] = ((Integer) j.get(i)).byteValue();
		}
		return ids;
	}

	private Short[] getDegrees(JSONObject obj) {
		JSONArray j = obj.getJSONArray("degrees");
		Short[] degs = new Short[j.length()];
		for (int i = 0; i < j.length(); i++) {
			degs[i] = ((Integer) j.get(i)).shortValue();
		}
		return degs;
	}

// 	private String bytes2str(Byte[] bytes) {
// 		StringJoiner sj = new StringJoiner(",");
// 		for (Byte b : bytes) { sj.add(b.toString()); }
// 		return sj.toString();
// 	}
// 	private String shorts2str(Short[] shorts) {
// 		StringJoiner sj = new StringJoiner(",");
// 		for (Short s : shorts) { sj.add(s.toString()); }
// 		return sj.toString();
// 	}


	public static void main(final String[] args) throws Exception {
		String ip      = args.length > 0 ? args[0] : "localhost";
		String port    = args.length > 1 ? args[1] : "61616";
		String robotId = args.length > 2 ? args[2] : "R00";
		AxesController m = new AxesController(ip, port, robotId);
		if (!m.initDev()) return;
		m.connect();
		m.subscribe("AxesMoveOrdered");
		m.run();
	}
}

