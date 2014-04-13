package ioio.irontrack;

import ioio.lib.api.PwmOutput;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.os.Bundle;
import android.util.Log;

public class IronTrackActivity extends IOIOActivity {

	private static final String _TAG = "IronTrack";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.iron_track);
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run every time the application is resumed and aborted when it is paused. The method setup() will be called right after a
	 * connection with the IOIO has been established (which might happen several times!). Then, loop() will be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {

		private PwmOutput _pwmPan;
		private PwmOutput _pwmTilt;
		private PwmOutput _pwmMotor;
		private PwmOutput _pwmFrontWheels;

		private Uart _uart;
		private InputStream _uartInputStream;

		int _inByte = 0; // incomming serial byte
		int _inbyteIndex = 0; // incomming bytes counter
		char _oscControl; // control in TouchOSC sending the message
		int[] _oscMsg = new int[11]; // buffer for incoming OSC packet
		float _pwm = 0.0f;

		/**
		 * Called every time a connection with IOIO has been established. Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * @throws InterruptedException
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException, InterruptedException {

			try {
				_uart = ioio_.openUart(5, 4, 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
				_uartInputStream = _uart.getInputStream();
				
				_pwmPan = ioio_.openPwmOutput(10, 100); // 9 shield
				_pwmTilt = ioio_.openPwmOutput(6, 100); // 5 shield
				_pwmMotor = ioio_.openPwmOutput(27, 100); // screw terminal
				_pwmFrontWheels = ioio_.openPwmOutput(12, 100); // 11 shield
				
				_pwmPan.setPulseWidth(1550);
				_pwmTilt.setPulseWidth(1800);
				_pwmMotor.setPulseWidth(1500);
				_pwmFrontWheels.setPulseWidth(1475);				
			} catch (ConnectionLostException e) {
				Log.e(_TAG, e.getMessage());
				throw e;
			}
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {

			try {

				_inByte = _uartInputStream.read();

				// An OSC address pattern is a string beginning with the character forward slash '/'
				if (_inByte == 47) {
					_inbyteIndex = 0; // and we set array pointer to 0
				}
				// ASCII values for M = 0x4D | W = 0x57
				if (_inbyteIndex == 0 && (_inByte == 0x4D || _inByte == 0x57)) {
					switch (_inByte) {
					case (0x4D): // Motor
						_oscControl = 'M';
						break;
					case (0x57): // Wheels
						_oscControl = 'W';
						break;
					default:
						_oscControl = ' ';
					}
					_inbyteIndex++;
				}
				if (_inbyteIndex < 12 && _inbyteIndex > 0) {
					_oscMsg[_inbyteIndex - 1] = _inByte;
					_inbyteIndex++;
				}
				if (_inbyteIndex == 11) { // end of the OSC message
					_inbyteIndex = -1; // set the pointer to -1 so we stop processing

					byte[] byte_array = new byte[4];
					byte_array[0] = (byte) _oscMsg[10];
					byte_array[1] = (byte) _oscMsg[9];
					byte_array[2] = (byte) _oscMsg[8];
					byte_array[3] = (byte) _oscMsg[7];
					ByteBuffer byteBuffer = ByteBuffer.allocate(byte_array.length);
					byteBuffer.put(byte_array);

					_pwm = getOSCValue(byteBuffer.array());
					
//					Log.e("array", String.valueOf(_pwm));
					
					switch (_oscControl) {
					case ('M'): // Motor
						_pwmMotor.setPulseWidth((int) _pwm);
						break;
					case ('W'): // Wheels
						_pwmFrontWheels.setPulseWidth((int) _pwm);
						break;
					}
					
				}

				Thread.sleep(1);

			} catch (InterruptedException e) {
				ioio_.disconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private float getOSCValue(byte[] byte_array_4) {
			int ret = 0;
			for (int i = 0; i < 4; i++) {
				int b = (int) byte_array_4[i];
				if (i < 3 && b < 0) {
					b = 256 + b;
				}
				ret += b << (i * 8);
			}
			return Float.intBitsToFloat(ret);
		}

		@Override
		public void disconnected() {
			try {
				if (_uartInputStream != null)
					_uartInputStream.close();
				if (_uart != null)
					_uart.close();
				
			} catch (IOException e) {
				// Nothing to do at this point!
			} finally {
				_uartInputStream = null;
				_uart = null;
				_pwmMotor.close();
				_pwmFrontWheels.close();
			}
		}
	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}
}
