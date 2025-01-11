/* FTC Team 7572 - Version 1.0 (11/07/2024)
*/
package org.firstinspires.ftc.teamcode;

import static java.lang.Math.toDegrees;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

/**
 */
@Autonomous(name="Autonomous Left-Blue", group="7592", preselectTeleOp = "Teleop-Blue")
//@Disabled
public class AutonomousLeftBlue extends AutonomousBase {

    // These constants define the desired driving/control characteristics
    // The can/should be tweaked to suite the specific robot drivetrain.
    static final boolean DRIVE_Y = true;    // Drive forward/backward
    static final boolean DRIVE_X = false;   // Drive right/left (not DRIVE_Y)

    int vipPos  = 0;
    double tiltAngle = robot.armTiltAngle;

    double pos_y=0, pos_x=0, pos_angle=0.0;  // Allows us to specify movement INCREMENTALLY, not ABSOLUTE

    @Override
    public void runOpMode() throws InterruptedException {

        telemetry.addData("State", "Initializing (please wait)");
        telemetry.update();

        // Initialize robot hardware (autonomous mode)
        robot.init(hardwareMap,true);

        // Initialize webcams using OpenCV
        telemetry.addData("State", "Initializing (please wait)");
        telemetry.update();

        // Ensure viper arm is fully retracted before we start
        ensureViperArmFullyRetracted();

        // Wait for the game to start (driver presses PLAY).  While waiting, poll for options
        redAlliance  = false;
        scorePreloadSpecimen = true;
        spikeSamples = 3;
        parkLocation = PARK_SUBMERSIBLE;

        while (!isStarted()) {
            // Check for operator input that changes Autonomous options
            captureGamepad1Buttons();
            // Do we need to preload a specimen?
            if( gamepad1_r_bumper_now && !gamepad1_r_bumper_last) {
                if( clawOpen ) {
                    robot.clawStateSet( Hardware2025Bot.clawStateEnum.CLAW_CLOSED );
                    clawOpen = false;
                } else {
                    robot.clawStateSet( Hardware2025Bot.clawStateEnum.CLAW_OPEN );
                    clawOpen = true;
                }
            } //  gamepad1_r_bumper
            // Do we need to change any of the other autonomous options?
            processAutonomousInitMenu();
            // Pause briefly before looping
            idle();
        } // !isStarted

        // Ensure any movement during robot setup is reset to zero
        resetGlobalCoordinatePosition();

        // Start the autonomous timer so we know how much time is remaining when cycling samples
        autonomousTimer.reset();

        // Only do these steps if we didn't hit STOP
        if( opModeIsActive() ) {
//          createAutoStorageFolder(redAlliance, pipelineBack.leftSide);
//          pipelineBack.setStorageFolder(storageDir);
//          pipelineBack.saveSpikeMarkAutoImage();
        }

        //---------------------------------------------------------------------------------
        // UNIT TEST: The following methods verify our basic robot actions.
        // Comment them out when not being tested.
//      testGyroDrive();
//      unitTestOdometryDrive();
//      timeArmMovement();
        //---------------------------------------------------------------------------------

        //---------------------------------------------------------------------------------
        // AUTONOMOUS ROUTINE:  The following method is our main autonomous.
        // Comment it out if running one of the unit tests above.
        mainAutonomous();
        //---------------------------------------------------------------------------------

        telemetry.addData("Program", "Complete");
        telemetry.update();

    } /* runOpMode() */

    /*--------------------------------------------------------------------------------------------*/
    // TEST CODE: Verify gyro/encoder-based motion functions against a tape measure
    private void testGyroDrive() {
        double startAngle;
        gyroDrive(DRIVE_SPEED_20, DRIVE_Y, 12.0, 999.9, DRIVE_THRU ); // Drive FWD 12" along current heading
        gyroDrive(DRIVE_SPEED_20, DRIVE_X, 12.0, 999.9, DRIVE_TO  ); // Strafe RIGHT 12" along current heading
        // What is our starting angle?
        startAngle = getAngle();
        gyroTurn(TURN_SPEED_20, (startAngle + 45) );   // Turn CW 45 degrees
    } // testGyroDrive

    /*--------------------------------------------------------------------------------------------*/
    // TEST CODE: Verify odometry-based motion functions against a tape measure
    private void unitTestOdometryDrive() {
        // Drive forward 12"
        driveToPosition( 12.0, 0.0, 0.0, DRIVE_SPEED_20, TURN_SPEED_20, DRIVE_TO );
        // Strafe right 12"
        driveToPosition( 12.0, 12.0, 0.0, DRIVE_SPEED_20, TURN_SPEED_20, DRIVE_TO );
        // Turn 180 deg
        driveToPosition( 12.0, 12.0, 90.0, DRIVE_SPEED_20, TURN_SPEED_20, DRIVE_TO );
        // Report the final odometry position/orientation
        telemetry.addData("Final", "x=%.1f, y=%.1f, %.1f deg",
                robotGlobalXCoordinatePosition, robotGlobalYCoordinatePosition, toDegrees(robotOrientationRadians) );
        telemetry.update();
        sleep( 7000 );
    } // unitTestOdometryDrive

    /*--------------------------------------------------------------------------------------------*/
    // TEST CODE: Time how long our arm and viper slide take to get to a specified position
    private void timeArmMovement() {
       boolean tiltDone  = false;
       boolean viperDone = false;
       double  tiltTime = 0.0;
       double  viperTime = 0.0;
       // reset our timer
       autonomousTimer.reset();
       // start both movements
       autoTiltMotorMoveToTarget(Hardware2025Bot.TILT_ANGLE_SPECIMEN1_DEG, 1.0);
       autoViperMotorMoveToTarget(Hardware2025Bot.VIPER_EXTEND_AUTO1);
       // wait for both to finish
       do {
          if( !opModeIsActive() ) break;
          // only check every 100 msec
          sleep( 100 );
          // update all our status
          performEveryLoop();
          if( !tiltDone && !autoTiltMotorMoving() ) {
              tiltTime = autonomousTimer.milliseconds();
              tiltDone = true;
          }
          if( !viperDone && !autoViperMotorMoving() ) {
              viperTime = autonomousTimer.milliseconds();
              viperDone = true;
          }
       } while( !tiltDone || !viperDone );
       // display the results
       telemetry.addData("Tilt",  "%.1f sec", tiltTime/1000.0 );   // 2.2 sec
       telemetry.addData("Viper", "%.1f sec", viperTime/1000,0 );  // 1.2 sec
       telemetry.update();
       sleep( 7000 );
    } // timeArmMovement

    /*--------------------------------------------------------------------------------------------*/
    /* Autonomous Left:                                                                           */
    /*   1 Starting point                                                                         */
    /*   2 Place sample in upper bucket                                                           */
    /*   3 Collect right neutral sample                                                           */
    /*   4 Place sample in upper bucket                                                           */
    /*   5 Collect center neutral sample                                                          */
    /*   6 Place sample in upper bucket                                                           */
    /*   7 Collect left neutral sample                                                            */
    /*   8 Place sample in upper bucket                                                           */
    /*   9 Level one ascent                                                                       */
    /*--------------------------------------------------------------------------------------------*/
    private void mainAutonomous() {

        // Do we start with an initial delay?
        if( startDelaySec > 0 ) {
            sleep( startDelaySec * 1000 );
        }

        // Score the preloaded SPECIMEN
        if( !onlyPark && scorePreloadSpecimen ) {
            scoreSpecimenPreload();
        }

        // Score the preloaded SAMPLE
        if( !onlyPark && !scorePreloadSpecimen ) {
            scoreSamplePreload();
        }

        if( !onlyPark && (spikeSamples > 0) ) {
            if( scorePreloadSpecimen ) {
                driveToPosition(18.5, 0.0, 0.0, DRIVE_SPEED_100, TURN_SPEED_30, DRIVE_THRU);
                driveToPosition(14.0, -25.0, 0.0, DRIVE_SPEED_100, TURN_SPEED_30, DRIVE_THRU);
            }
            tiltAngle = Hardware2025Bot.TILT_ANGLE_DRIVE_DEG; autoTiltMotorMoveToTarget(tiltAngle, 0.80 );
            vipPos = Hardware2025Bot.VIPER_EXTEND_AUTO_READY; autoViperMotorMoveToTarget(vipPos);
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_GRAB);
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_GRAB);
            // Score starting sample
            int samplesScored = 1;

            while (samplesScored <= spikeSamples) {
                collectSample(samplesScored);
                scoreSample(samplesScored);
                samplesScored++;
            }
        }
        // Park for 3pts (level 1 ascent)
        level1Ascent();

        // ensure motors are turned off even if we run out of time
        robot.driveTrainMotorsZero();
    } // mainAutonomous

    /*--------------------------------------------------------------------------------------------*/
    private void scoreSpecimenPreload() {
        // Drive forward to submersible
        if( opModeIsActive() ) {
            telemetry.addData("Motion", "Move to submersible");
            telemetry.update();
            // Move away from field wall (viper slide motor will hit field wall if we tilt up too soon!)
            driveToPosition( 3.00, 0.00, 0.00, DRIVE_SPEED_30, TURN_SPEED_30, DRIVE_THRU );
            // Start tilting and extending the arm, and positioning the specimen
            tiltAngle = Hardware2025Bot.TILT_ANGLE_SPECIMEN1_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0);
            vipPos    = Hardware2025Bot.VIPER_EXTEND_AUTO1; autoViperMotorMoveToTarget(vipPos);
            // Drive to the scoring position next to the submersible
            driveToPosition( 18.20, 7.20, 0.00, DRIVE_SPEED_40, TURN_SPEED_20, DRIVE_THRU );
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_BAR1);
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_BAR1);
            driveToPosition( 27.80, 7.20, 0.00, DRIVE_SPEED_40, TURN_SPEED_20, DRIVE_TO );
            robot.driveTrainMotorsZero();  // make double sure we're stopped
            // If we drive to the submersible faster than the arm moves, wait for the arm
            do {
                if( !opModeIsActive() ) break;
                // wait for lift/tilt to finish...
                sleep( 50 );
                // update all our status
                performEveryLoop();
            } while( autoViperMotorMoving(vipPos, 100) || autoTiltMotorMoving(tiltAngle, 100) );
            // sleep(100); old
        } // opModeIsActive

        // Rotate arm, viper slide, and claw down to clip the specimen
        if( opModeIsActive() ) {
            tiltAngle = Hardware2025Bot.TILT_ANGLE_SPECIMEN2_DEG; autoTiltMotorMoveToTarget(tiltAngle,0.80 );
            vipPos = Hardware2025Bot.VIPER_EXTEND_AUTO2; autoViperMotorMoveToTarget(vipPos);
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_BAR2);
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_BAR2);
            do {
                if( !opModeIsActive() ) break;
                // wait for lift/tilt to finish...
                sleep( 50 );
                // update all our status
                performEveryLoop();
            } while( autoViperMotorMoving(vipPos, 1000) || autoTiltMotorMoving(tiltAngle, 1000) );
            //sleep( 1000 ); //while( autoTiltMotorMoving() || autoViperMotorMoving());
            // release the specimen
            autoClawMoveToPosition(Hardware2025Bot.CLAW_SERVO_OPEN_W);
            //robot.clawStateSet( Hardware2025Bot.clawStateEnum.CLAW_OPEN_WIDE ); //old
        } // opModeIsActive

        //Prepare arm for what comes next (samples/parking)
        if( spikeSamples > 0 ) {
           prepareArmForSamples();
        }
        // Whether driving to park, or doing nothing, store the arm
        else {
           prepareArmForDriving();
        }

    } // scoreSpecimenPreload

    /*--------------------------------------------------------------------------------------------*/
    private void scoreSamplePreload() {
        // Drive forward to submersible
        if( opModeIsActive() ) {
            telemetry.addData("Motion", "Move to submersible");
            telemetry.update();
            // Move away from field wall (viper slide motor will hit field wall if we tilt up too soon!)
            driveToPosition( 8.0, -8.0, 0.0, DRIVE_SPEED_45, TURN_SPEED_30, DRIVE_THRU );
            // Move to basket and score preloaded sample
            scoreSample(0);
        } // opModeIsActive

    } // scoreSamplePreload

    /*--------------------------------------------------------------------------------------------*/
    private void prepareArmForSamples() {

        // Setup the arm for scoring samples
        if( opModeIsActive() ) {
            vipPos = Hardware2025Bot.VIPER_EXTEND_AUTO_COLLECT; autoViperMotorMoveToTarget( vipPos );
            tiltAngle = Hardware2025Bot.TILT_ANGLE_COLLECT1_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0 );
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_GRAB);
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_GRAB);
            autoClawMoveToPosition( Hardware2025Bot.CLAW_SERVO_OPEN_W);
        } // opModeIsActive

    } // prepareArmForSamples

    /*--------------------------------------------------------------------------------------------*/
    private void prepareArmForDriving() {

        // Retract any arm extension
        if( opModeIsActive() ) {
            vipPos = Hardware2025Bot.VIPER_EXTEND_ZERO; autoViperMotorMoveToTarget( vipPos );
//          driveToPosition( 32.60, 2.70, 52.20, DRIVE_SPEED_80, TURN_SPEED_20, DRIVE_THRU );
        } // opModeIsActivee

        // Now that we're clear from the submersible, rotate arm down and store claw
        if( opModeIsActive() ) {
            autoClawMoveToPosition(Hardware2025Bot.CLAW_SERVO_CLOSED);
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_INIT);
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_INIT);
            tiltAngle = Hardware2025Bot.TILT_ANGLE_WALL_DEG; autoTiltMotorMoveToTarget(tiltAngle, 0.80);
//          driveToPosition( 38.40, 17.50, 90.00, DRIVE_SPEED_80, TURN_SPEED_20, DRIVE_THRU );
//          driveToPosition( 47.40, 10.00, 180.00, DRIVE_SPEED_80, TURN_SPEED_20,
//                                             ((spikeSamples > 0)? DRIVE_THRU : DRIVE_TO) );
        } // opModeIsActive

    } // prepareArmForDriving

    //************************************
    // Collect sample
    //************************************
    private void collectSample(int samplesScored) {

        switch(samplesScored) {
            case 1:
                // Drive forward toward the wall
                driveToPosition( 20.9, -32.7, 0.0, DRIVE_SPEED_100, TURN_SPEED_20, DRIVE_TO );
                do {
                    if( !opModeIsActive() ) break;
                    // wait for lift/tilt to finish...
                    sleep( 50 );
                    // update all our status
                    performEveryLoop();
                } while( autoViperMotorMoving(vipPos, 1500) || autoTiltMotorMoving(tiltAngle, 1500) );
                //sleep(1500);  // old: viper should already be in position
                break;
            case 2:
                driveToPosition( 20.9, -43.1, 0.0, DRIVE_SPEED_100, TURN_SPEED_20, DRIVE_TO );
                do {
                    if( !opModeIsActive() ) break;
                    // wait for lift/tilt to finish...
                    sleep( 50 );
                    // update all our status
                    performEveryLoop();
                } while( autoViperMotorMoving(vipPos, 1300) || autoTiltMotorMoving(tiltAngle, 1300) );
                //sleep(1300);  // old: wait for viper to fully retract
                break;
            case 3:
                autoViperMotorMoveToTarget( Hardware2025Bot.VIPER_EXTEND_SAMPLE3);
                autoTiltMotorMoveToTarget(Hardware2025Bot.TILT_ANGLE_SAMPLE3_DEG, 1.0 );
                autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_GRAB3);
                // drive slow because waiting for arm to lower
                driveToPosition( 24.4, -43.8, 5.0, DRIVE_SPEED_40, TURN_SPEED_30, DRIVE_TO );
                do {
                    if( !opModeIsActive() ) break;
                    // wait for lift/tilt to finish...
                    sleep( 50 );
                    // update all our status
                    performEveryLoop();
                } while( autoViperMotorMoving(vipPos, 1000) || autoTiltMotorMoving(tiltAngle, 1000) );
                //sleep(1000); // old: while( autoViperMotorMoving() || autoTiltMotorMoving() )
                driveToPosition( 24.4, -43.8, 32.1, DRIVE_SPEED_40, TURN_SPEED_30, DRIVE_TO );
                break;
            default:
        }

        // Close the claw on this sample
        autoClawMoveToPosition( Hardware2025Bot.CLAW_SERVO_CLOSED );
        do {
            if( !opModeIsActive() ) break;
            // wait for servo to finish...
            sleep( 50 );
            // update all our status
            performEveryLoop();
        } while( autoClawMoving(Hardware2025Bot.CLAW_SERVO_CLOSED, 900));
        //sleep(900); // Old: wait for claw to close on sample
    } // collectSample

    //************************************
    // Score Sample
    //************************************
    private void scoreSample(int samplesScored) {
        tiltAngle = Hardware2025Bot.TILT_ANGLE_BASKET_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0 );
        // drive partway there while we wait for arm to lift (before extending viper)
        if( scorePreloadSpecimen || (samplesScored > 0) ){
            driveToPosition( 11.0, -33.5, -46.6, DRIVE_SPEED_100, TURN_SPEED_30, DRIVE_THRU );
            vipPos = Hardware2025Bot.VIPER_EXTEND_BASKET; robot.startViperSlideExtension( vipPos );
        } else {
            vipPos = Hardware2025Bot.VIPER_EXTEND_BASKET; robot.startViperSlideExtension( vipPos );
            driveToPosition( 9.5, -20.0, -23.0, DRIVE_SPEED_30, TURN_SPEED_30, DRIVE_THRU );
            autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_GRAB);
            autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_GRAB);
            driveToPosition( 11.0, -33.5, -46.6, DRIVE_SPEED_30, TURN_SPEED_30, DRIVE_THRU );
        }
        driveToPosition( 6.0, -38.5, -46.6, DRIVE_SPEED_40, TURN_SPEED_20, DRIVE_TO );
        autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_BASKET);
        autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_BASKET1);
        do {
            if( !opModeIsActive() ) break;
            // wait for lift/tilt to finish...
            sleep( 50 );
            // update all our status
            performEveryLoop();
        } while( autoViperMotorMoving(vipPos) || autoTiltMotorMoving(tiltAngle) );
        autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_BASKET);
        autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_BASKET2);
        do {
            if( !opModeIsActive() ) break;
            // wait for servo to finish...
            sleep( 50 );
            // update all our status
            performEveryLoop();
        } while( autoElbowMoving(Hardware2025Bot.ELBOW_SERVO_BASKET, 250) || autoWristMoving(Hardware2025Bot.WRIST_SERVO_BASKET2, 250));
        //sleep(250); // Old: wait for wrist/elbow to move
        autoClawMoveToPosition( Hardware2025Bot.CLAW_SERVO_OPEN_W );
        do {
            if( !opModeIsActive() ) break;
            // wait for servo to finish...
            sleep( 50 );
            // update all our status
            performEveryLoop();
        } while( autoElbowMoving(Hardware2025Bot.CLAW_SERVO_OPEN_W, 250));
        //sleep(250); // Old: wait for claw to drop sample
        autoElbowMoveToPosition(Hardware2025Bot.ELBOW_SERVO_GRAB);
        autoWristMoveToPosition(Hardware2025Bot.WRIST_SERVO_GRAB);
        sleep(100); // wait for claw to start moving up/back before lowering arm
        // Don't retract arm if we are going to park
        if(samplesScored < spikeSamples) {
            tiltAngle = Hardware2025Bot.TILT_ANGLE_COLLECT1_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0);
            vipPos = Hardware2025Bot.VIPER_EXTEND_AUTO_COLLECT; autoViperMotorMoveToTarget(vipPos);
        }
        else{
            tiltAngle = Hardware2025Bot.TILT_ANGLE_ASCENT2_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0);
            vipPos = Hardware2025Bot.VIPER_EXTEND_SAFE; autoViperMotorMoveToTarget(vipPos);
        }
    } // scoreSample

    private void level1Ascent() {
        if( opModeIsActive() && (spikeSamples < 1)) {
            // Back up from submersible TODO OLD ODOMETRY VALUES NEEDS TO BE UPDATED
            driveToPosition( 32.0, 6.0, 90.0, DRIVE_SPEED_50, TURN_SPEED_50, DRIVE_THRU );
            // Drive forward toward the wall
            driveToPosition( 38.0, -27.0, 90.0, DRIVE_SPEED_50, TURN_SPEED_30, DRIVE_TO );
        } // opModeIsActive

        if( opModeIsActive() ) {
            // Drive towards submersible
            driveToPosition(44.0, -20.00, -70.0, DRIVE_SPEED_70, TURN_SPEED_50, DRIVE_THRU);
            // Extend to level1 ascent position
            tiltAngle = Hardware2025Bot.TILT_ANGLE_PARK_DEG; autoTiltMotorMoveToTarget(tiltAngle, 1.0);
            vipPos    = Hardware2025Bot.VIPER_EXTEND_PARK; autoViperMotorMoveToTarget(vipPos);
            // Drive forward into rung
            driveToPosition(50.0, -17.00, -70.0, DRIVE_SPEED_70, TURN_SPEED_50, DRIVE_TO);
        } // opModeIsActive

    } // level1Ascent

} /* AutonomousLeftBlue */
