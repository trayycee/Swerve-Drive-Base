package frc.robot;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import edu.wpi.first.wpilibj.Notifier;
import frc.robot.Util.DriveCommand;


public class SwerveModule{
    //private AnalogInput SteeringAnalog = new AnalogInput(0);
    private TalonSRX mDrive, mSteering;
    private Notifier pidLoop;           //A notifier is a thread. Basically think of a thread as something running in the background.
    private volatile double sumError, errorChange, lastError, currentError, pidOutput;
    private boolean isReversed;
    private double setpoint;
    private double offset;

    private static final double dt = 0.01;  //this is how fast we run our PID loop.
    private static final int POSITIVE_ROTATION_SENSOR_MIN = 45;  //we measured this
    private static final int POSITIVE_ROTATION_SENSOR_MAX = 870;  //and this

    private static final int NEGATIVE_ROTATION_SENSOR_MIN = 156;  //we measured this
    private static final int NEGATIVE_ROTATION_SENSOR_MAX = 978;  //and this
    
    /**
     * 
     * @param kSteeringID   the ID of the steering motor
     * @param kDriveID      the ID of the drive motor
     * @param isReversed    if the module is physically reversed on the robot
     * @param kP            the steering kP gain
     * @param kI            the steering kI gain
     * @param kD            the steering kD gain
     */
    public SwerveModule(int kSteeringID, int kDriveID, boolean isReversed, double offset, double kP, double kI, double kD){
        mDrive = new TalonSRX(kDriveID);
        mSteering = new TalonSRX(kSteeringID);
        this.offset = offset;

        //Configure steering Talon SRX
        mSteering.configSelectedFeedbackSensor(FeedbackDevice.Analog, Constants.kPIDLoopIdx, Constants.kTimeoutMs);
        mSteering.configOpenloopRamp(0, Constants.kTimeoutMs);      //this is what we were missing!
        mSteering.configPeakCurrentDuration(Constants.kPeakCurrentDuration, Constants.kTimeoutMs);
        mSteering.configPeakCurrentLimit(Constants.kPeakCurrentLimit, Constants.kTimeoutMs);
        mSteering.configContinuousCurrentLimit(Constants.kSustainedCurrentLimit, Constants.kTimeoutMs);
        mSteering.enableCurrentLimit(true);
        mSteering.setStatusFramePeriod(StatusFrame.Status_4_AinTempVbat, 1, 0);
        mSteering.setInverted(true);
        mSteering.setSensorPhase(true);

        //Configure drive Talon SRX
        mDrive.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, Constants.kPIDLoopIdx, Constants.kTimeoutMs);

        /**
        sumError = 0;
        lastError = getModifiedError();
        currentError = lastError;
*/
        pidLoop = new Notifier(() -> {
            currentError = getModifiedError();  //update the current error to the most recent one
            /*
            sumError += currentError * dt;
            errorChange = (currentError-lastError)/dt;
*/
            pidOutput = kP * currentError; //+ kI * sumError + kD * errorChange; //you guys know this, or at least you better...
            mSteering.set(ControlMode.PercentOutput, pidOutput);
            //lastError = currentError;   //update the last error to be the current error
        });


        this.isReversed = isReversed;

        pidLoop.startPeriodic(dt);
    }

    /**@return  the velocity of the wheel, measured in ticks/100ms
     * 
     */
    public int getDriveEncoder(){
        return (mDrive.getSelectedSensorVelocity(0));
    }

    /**
     * @return  the angle of the wheel, where angle is an element of [-pi, pi]
     */

    public int getSteeringDegrees(){
        int steeringPosition = mSteering.getSelectedSensorPosition(Constants.kPIDLoopIdx);

        if(steeringPosition >= 0){
            return normalizeEncoder(POSITIVE_ROTATION_SENSOR_MIN, POSITIVE_ROTATION_SENSOR_MAX, steeringPosition)-180;
        }
        else
            return normalizeEncoder(NEGATIVE_ROTATION_SENSOR_MIN, NEGATIVE_ROTATION_SENSOR_MAX, steeringPosition)-180;



    }

    /**
     * 
     * @return  the closed-loop PID output, calculated by PID loop
     */
    public double getSteeringOutput(){
        return pidOutput;
    }


    /**
     * 
     * @return  the unbounded steering error, in radians
     */
    public double getError(){
        return setpoint - getSteeringDegrees();
    }

    /**
     * 
     * @return  the steering error bounded to [-pi, pi]
     */
    public double getModifiedError(){
        return (boundHalfDegrees(getError()))/180;
    }

    /**
     * 
     * @param power the power of the wheel, where power is [-1.0, 1.0]
     */
    public void setDrivePower(double power){
        if(isReversed)
            mDrive.set(ControlMode.PercentOutput, -power);
        else
            mDrive.set(ControlMode.PercentOutput, power);
    }

    /**
     * 
     * @param deg   the angle to set the wheel to, in degrees
     */
    public void setSteeringDegrees(double deg){
        setpoint = boundHalfDegrees(deg + offset);
    }

    /**
     * 
     * @return  returns the setpoint of the sttering in degrees
     */
    public double getSetpointDegrees(){
        return setpoint;
    }

    /**
     *
     * @param encPos    the encoder input to be normalized
     * @param minVal    the minimum MEASURED ABSOLUTE value of the encoder
     * @param maxVal    the maximum MEASURED ABSOLUTE value of the encoder
     * @return          the encoder input normalized to [0, 1023]
     * */
    private int normalizeEncoder(int minVal, int maxVal, int encPos){
        return (int)Math.round(((Math.abs(encPos) % 1023) - minVal) * Math.abs((360.0/(maxVal-minVal))));
    }

    public void setSteeringPower(double x){
        mSteering.set(ControlMode.PercentOutput, x);
    }

    public void set(double degrees, double power){
        setSteeringDegrees(degrees);
        setDrivePower(power);
    }

    public void set(DriveCommand command){
        //setSteeringDegrees(command.getDegrees());
        //setDrivePower(command.getSpeed());
    }

    public static double boundHalfDegrees(double angle_degrees) {
        while (angle_degrees >= 180.0) angle_degrees -= 360.0;
        while (angle_degrees < -180.0) angle_degrees += 360.0;
        return angle_degrees;
    }
    
    public void resetTalon(TalonSRX talon){
        talon.configOpenloopRamp(0, 10);
        talon.configClosedloopRamp(0, 10);
        talon.configPeakOutputForward(1, 10);
        talon.configPeakOutputReverse(-1, 10);
        talon.configNominalOutputForward(0, 10);
        talon.configNominalOutputReverse(0, 10);
        talon.configNeutralDeadband(0.04,10);
        talon.configVoltageCompSaturation(0, 10);
        talon.configVoltageMeasurementFilter(32, 10);
        talon.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, 0, 10);
        talon.configSelectedFeedbackCoefficient(1.0, 0, 10);
      }

}
