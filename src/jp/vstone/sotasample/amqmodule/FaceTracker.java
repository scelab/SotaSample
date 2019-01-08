package jp.vstone.sotasample.amqmodule;

import java.awt.Point;

import javax.jms.JMSException;

import org.json.JSONObject;

import jp.pux.lib.PFIDLibrary.FacePosition;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.FaceDetectResult;
import jp.vstone.sotasample.amqmodule.Main.GlobalVariable;

public final class FaceTracker extends AmqModule {
	private boolean isTracking = false;
	private Byte[] servoIds;
	private int FaceTrack_YawMax   =  900;
	private int FaceTrack_YawMin   = -900;
	private int FaceTrack_PitchMax =  300;
	private int FaceTrack_PitchMin = -300;
	private double FaceTrack_P_GAIN = 0.25D;
	private double FaceTrack_D_GAIN = 0.55D;
	private double FaceTrack_Y_GAIN = 1.0D;
	private int yaw   =  0;
	private int pitch = -150;
	private int width  = 640;
	private int height = 480;
	private double old_yaw   = 0.0D;
	private double old_pitch = 0.0D;
	private double maxspeed_yaw   = 100.0D;
	private double maxspeed_pitch = 100.0D;
	private Thread trackingThread;

	public FaceTracker(String ip, String port, String robotId) {
		super(ip, port, robotId);
		servoIds = new Byte[] {CSotaMotion.SV_HEAD_Y, CSotaMotion.SV_HEAD_P,};
	}

	protected void procedure(JSONObject obj) {
		if (!obj.getString("looker").equals(robotId)) return;
		String topic = obj.getString("topic");
		if (topic.equals("FaceTrackingStartOrdered")) {
			CRobotUtil.Log("FaceTracker", obj.getString("timestamp") + " FaceTrackingStartOrdered");
			isTracking = true;
			Runnable r = () -> {
				initHeadPos();
				while (isTracking) {
					FaceDetectResult result = GlobalVariable.robocam.getDetectResult();
					if(result.isDetect()) {
						CRobotUtil.Log("FaceTracker", obj.getString("timestamp") + " [Detect] x = " + result.getCenterPoint(0).x + ", y = " + result.getCenterPoint(0).y);
						faceTrack(result);
					}
					else {
						CRobotUtil.Log("FaceTracker", obj.getString("timestamp") + " [Not detected]");
						CRobotUtil.wait(250);
					}
				}
			};
			trackingThread = new Thread(r);
			trackingThread.start();
			obj.put("topic", "FaceTrackingStartCompleted");
			try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }

		}
		else if (topic.equals("FaceTrackingStopOrdered")) {
			CRobotUtil.Log("FaceTracker", obj.getString("timestamp") + " FaceTrackingStopOrdered");
			isTracking = false;
			try { if(trackingThread != null) trackingThread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
			obj.put("topic", "FaceTrackingStopCompleted");
			try { publish(obj); } catch (JMSException e) { e.printStackTrace(); }
		}
	}

	private void initHeadPos() {
		CRobotPose pose = new CRobotPose();
		pose.SetPose(servoIds, new Short[]{0, 0});
		GlobalVariable.motion.LockServoHandle(servoIds);
		GlobalVariable.motion.play(pose, 500);
		CRobotUtil.wait(500);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
	}

	private void faceTrack(FaceDetectResult result) {
	    FacePosition pos = result.getFacePosition(0);
	    Point center = pos.getCenterPoint();
		double d_yaw   = center.getX() - (double)(width  / 2);
	    double d_pitch = center.getY() - (double)(height / 2);
	    double out_yaw   = FaceTrack_P_GAIN * d_yaw   + FaceTrack_D_GAIN * (d_yaw   - old_yaw);
	    double out_pitch = FaceTrack_P_GAIN * d_pitch + FaceTrack_D_GAIN * (d_pitch - old_pitch);
	    if      (out_yaw   >  maxspeed_yaw)   { out_yaw   =  maxspeed_yaw; }
	    else if (out_yaw   < -maxspeed_yaw)   { out_yaw   = -maxspeed_yaw; }
	    if      (out_pitch >  maxspeed_pitch) { out_pitch =  maxspeed_pitch; }
	    else if (out_pitch < -maxspeed_pitch) { out_pitch = -maxspeed_pitch; }
	    yaw   = (short)((int)(((double)yaw   - out_yaw) * FaceTrack_Y_GAIN));
	    pitch = (short)((int)( (double)pitch + out_pitch));
	    old_yaw   = d_yaw;
	    old_pitch = d_pitch;
	    if      (yaw > FaceTrack_YawMax) { yaw = FaceTrack_YawMax; }
	    else if (yaw < FaceTrack_YawMin) { yaw = FaceTrack_YawMin; }
	    if      (pitch > FaceTrack_PitchMax) { pitch = FaceTrack_PitchMax; }
	    else if (pitch < FaceTrack_PitchMin) { pitch = FaceTrack_PitchMin; }
	    if (GlobalVariable.motion.getReadPose() != null &&
	    	GlobalVariable.motion.getReadPose().getPose() != null &&
	    	GlobalVariable.motion.getReadPose().getPose().containsKey(Byte.valueOf(CSotaMotion.SV_HEAD_Y))) {
	        short pose = ((Short)GlobalVariable.motion.getReadPose().getPose().get(Byte.valueOf(CSotaMotion.SV_HEAD_Y))).shortValue();
	        int sub = Math.abs(yaw - pose);
	        CRobotUtil.Log("CRoboCamera", String.format("yaw:%d, nowYaw:%d, abs(yaw - nowYaw):%d", new Object[]{Integer.valueOf(yaw), Short.valueOf(pose), Integer.valueOf(sub)}));
	        int maxSub = FaceTrack_YawMax / 2;
	        if(maxSub < sub) {
	            CRobotUtil.Log("CRoboCamera", "Yaw position difference too large.");
	            if (0 <= yaw) { yaw = maxSub; }
	            else          { yaw = maxSub * -1; }
	        }
	    }
	    CRobotPose pose1 = new CRobotPose();
	    pose1.SetPose(servoIds, new Short[]{(short)yaw, (short)pitch});
		GlobalVariable.motion.LockServoHandle(servoIds);
		GlobalVariable.motion.play(pose1, 250);
		CRobotUtil.wait(250);
		GlobalVariable.motion.UnLockServoHandle(servoIds);
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
