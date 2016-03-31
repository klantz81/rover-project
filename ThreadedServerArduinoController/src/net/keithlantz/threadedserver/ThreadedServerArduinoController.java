package net.keithlantz.threadedserverarduinocontroller;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Handler;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.hardware.usb.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.io.IOException;
import java.lang.String;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.AutoFocusCallback;


public class ThreadedServerArduinoController extends Activity {

	private String ipAddress;
	private ServerSocket serverSocket;
	private ServerSocketChannel serverSocketChannel;
	private static final int PORT = 31236;

	private Button stopThreads;
	private Button startThread;
	volatile boolean runThreads;

	private Button enumerateDevices;
	private Button stopUSBThread;
	private UsbInterface intf;
	private UsbEndpoint endpoint1, endpoint2;
	private UsbDeviceConnection connection;
	//private EditText intervalInput;
	//private long interval;
	volatile boolean runUsbThread;

	private ScrollView scroll;
	private TextView log;

	private Handler handler = new Handler();

	private boolean[] button_states;

	private Camera camera;
	private boolean camera_on;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		button_states = new boolean[8];
		for (int i = 0; i < 8; i++) button_states[i] = false;
		camera_on = false;

		setContentView(R.layout.main);
		log = (TextView)findViewById(R.id.log);
		scroll = (ScrollView)findViewById(R.id.scroll);

		runThreads = false;

		stopThreads = (Button)findViewById(R.id.stopThreads);
		stopThreads.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				runThreads = false;
			}
		});

		startThread = (Button)findViewById(R.id.startThread);
		startThread.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!runThreads) {
					boolean networkAvailable = isNetworkAvailable();
					log.append(networkAvailable ? "Network available.\n" : "Network unavailable.\n");
					scroll.fullScroll(View.FOCUS_DOWN);
					if (networkAvailable) {
						InetAddress inetAddress = getInetAddress();
						ipAddress = inetAddress != null ? inetAddress.getHostAddress().toString() : "";
						runThreads = true;
						new Thread(new ServerThread()).start();
					}
				}
			}
		});

		//intervalInput = (EditText)findViewById(R.id.interval);

		runUsbThread = false;

		stopUSBThread = (Button)findViewById(R.id.stopUSBthread);
		stopUSBThread.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				runUsbThread = false;
			}
		});


		final UsbManager manager = (UsbManager)getSystemService(Context.USB_SERVICE);
		final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
		BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (ACTION_USB_PERMISSION.equals(action)) {
					synchronized (this) {
						final UsbDevice mDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

						if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
							if(mDevice != null){
								//call method to set up device communication
								int iCount = mDevice.getInterfaceCount();
								log.append(iCount + " interface(s)\n");
								scroll.fullScroll(View.FOCUS_DOWN);
								for (int i = 0; i < iCount; i++) {
									log.append(mDevice.getInterface(i).toString() + "\n");
									scroll.fullScroll(View.FOCUS_DOWN);
								}

								intf = mDevice.getInterface(1);
								int eCount = intf.getEndpointCount();
								log.append(eCount + " endpoint(s)\n");
								scroll.fullScroll(View.FOCUS_DOWN);

								if (eCount == 2) {
									endpoint1 = intf.getEndpoint(1);
									endpoint2 = intf.getEndpoint(0);

									if (endpoint2.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
										if (endpoint2.getDirection() == UsbConstants.USB_DIR_IN)
											log.append("USB_DIR_IN\n");
										else
											log.append("USB_DIR_OUT\n");
									}

									connection = manager.openDevice(mDevice); 
									connection.claimInterface(intf, true);
									try {
										//interval = Integer.parseInt(intervalInput.getText().toString());
									} catch (NumberFormatException e) {
										//interval = 1000;
									}
									//interval = interval < 1000 ? 1000 : interval;

									runUsbThread = true;
									new Thread(new UsbThread()).start();
								}
							}
						} 
						else {
							log.append("permission denied for device " + mDevice + "\n");
							scroll.fullScroll(View.FOCUS_DOWN);
						}
					}
				} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
					final UsbDevice mDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if(mDevice != null){
						runUsbThread = false;
					}
				}
			}
		};

		final PendingIntent mPermissionIntent = PendingIntent.getBroadcast(ThreadedServerArduinoController.this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
		registerReceiver(mUsbReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

		enumerateDevices = (Button)findViewById(R.id.enumerateDevices);
		enumerateDevices.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (runUsbThread) {
					log.append("USB thread is running.\n");
					scroll.fullScroll(View.FOCUS_DOWN);
					return;
				}

				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
				UsbDevice device, arduinodevice = null;
				while(deviceIterator.hasNext()){
					device = deviceIterator.next();
					log.append(device.toString() + "\n");
					scroll.fullScroll(View.FOCUS_DOWN);

					// check for arduino
					// k8055 - vendor id (4303) productid (21760, 21761, 21762, 21763)
					// arduino - vendor id (9025) productid (67)
					if (device.getVendorId() == 9025 && device.getProductId() == 67) arduinodevice = device;
					log.append(Integer.toString(device.getVendorId())+"\n");
					log.append(Integer.toString(device.getProductId())+"\n");
				}

				if (arduinodevice == null) {
					log.append("device not detected\n");
					scroll.fullScroll(View.FOCUS_DOWN);
					return;
				}

				manager.requestPermission(arduinodevice, mPermissionIntent);
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		runThreads = false;
		runUsbThread = false;
	}

	private void ledOn() {
		if (!camera_on) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("torch on\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
			camera = Camera.open();
			Parameters params = camera.getParameters();
final String err = params.toString();
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append(err);
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
//log.append(params.toString());
//return;
			params.setFlashMode(Parameters.FLASH_MODE_TORCH);
			camera.setParameters(params);
			camera.startPreview();
			//camera.autoFocus(new AutoFocusCallback() { public void onAutoFocus(boolean success, Camera camera) {} });
			camera_on = true;
		}
	}

	private void ledOff() {
		if (camera_on) {
			camera.stopPreview();
			camera.release();
			camera_on = false;
		}
	}

	private boolean isNetworkAvailable() {
		NetworkInfo networkInfo = ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
		return networkInfo != null && networkInfo.isConnected();
	}

	private InetAddress getInetAddress() {
		try {
			Enumeration<NetworkInterface> enum_networkInterface = NetworkInterface.getNetworkInterfaces();
			while (enum_networkInterface.hasMoreElements()) {
				NetworkInterface networkInterface = enum_networkInterface.nextElement();
				Enumeration<InetAddress> enum_inetAddress = networkInterface.getInetAddresses();
				while (enum_inetAddress.hasMoreElements()) {
					InetAddress inetAddress = enum_inetAddress.nextElement();
					if (!inetAddress.isLoopbackAddress()) return inetAddress;
				}
			}
		} catch (SocketException e) {
			log.append(e.toString()+"\n");
			scroll.fullScroll(View.FOCUS_DOWN);
		}
		return null;
	}

	public class AcceptThread implements Runnable {
		private SocketChannel sc;
		private String socketAddressStr;
		private boolean runChild;
		public AcceptThread(SocketChannel socketChannel) {
			sc = socketChannel;
			socketAddressStr = "IP "+sc.socket().getInetAddress().getHostAddress().toString()+" Port "+sc.socket().getPort();
			runChild = true;
		}

		public void run() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("Child thread started (Client "+socketAddressStr+").\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
			try {
				sc.configureBlocking(false);
				while (runThreads && runChild) {
					ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
					buffer.clear();
					int bytesRead = sc.read(buffer);
					if (bytesRead == -1) {
						runChild = false;
						sc.close();
					} else if (bytesRead > 0) {
						buffer.flip();
						Charset charset = Charset.forName("UTF-8");
						CharsetDecoder decoder = charset.newDecoder();
						final String buf = decoder.decode(buffer).toString();
						for (int i = 0; i < bytesRead; i++) button_states[i % 8] = buf.charAt(i) == '1';

						if (button_states[5]) ledOn(); else ledOff();

						handler.post(new Runnable() {
							@Override
							public void run() {
								log.append("(Client IP "+socketAddressStr+") "+buf+"\n");
								scroll.fullScroll(View.FOCUS_DOWN);
							}
						});
					}
				}
			} catch(IOException e) {
				final String err = e.toString();
				handler.post(new Runnable() {
					@Override
					public void run() {
						log.append(err+"\n");
						scroll.fullScroll(View.FOCUS_DOWN);
					}
				});
			}
			try {
				sc.close();
			} catch(IOException e) {
				final String err = e.toString();
				handler.post(new Runnable() {
					@Override
					public void run() {
						log.append(err+"\n");
						scroll.fullScroll(View.FOCUS_DOWN);
					}
				});
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("Child thread terminated (Client "+socketAddressStr+").\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
		}
	}

	public class ServerThread implements Runnable {
		public void run() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("Main thread started (Server IP "+ipAddress+" Port "+PORT+").\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
			try {
				serverSocketChannel = ServerSocketChannel.open();
				serverSocketChannel.configureBlocking(false);
				serverSocket = serverSocketChannel.socket();
				serverSocket.bind(new InetSocketAddress(PORT));
				while(runThreads) {
					SocketChannel socketChannel = serverSocketChannel.accept();
					if (socketChannel != null)
						new Thread(new AcceptThread(socketChannel)).start();
				}
			} catch(IOException e) {
				final String err = e.toString();
				handler.post(new Runnable() {
					@Override
					public void run() {
						log.append(err+"\n");
						scroll.fullScroll(View.FOCUS_DOWN);
					}
				});
			}
			try {
				serverSocketChannel.close();
			} catch(IOException e) {
				final String err = e.toString();
				handler.post(new Runnable() {
					@Override
					public void run() {
						log.append(err+"\n");
						scroll.fullScroll(View.FOCUS_DOWN);
					}
				});
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("Main thread terminated (Server "+ipAddress+" Port "+PORT+").\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
		}
	}

	private Timer timer;

	public class UsbThread implements Runnable {
		public void run() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("USB thread started.\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});

/*			timer = new Timer();
			timer.scheduleAtFixedRate(
				new TimerTask() {
					public void run() {
						// output
						ByteBuffer buffer = ByteBuffer.allocateDirect(8);
						UsbRequest req = new UsbRequest();
						buffer.clear();
						if (req.initialize(connection, endpoint2)) {
							buffer.put((byte)5);
							buffer.put((byte)255);
							req.queue(buffer, 2);
							req = connection.requestWait();
							Timer t = new Timer();
							t.schedule(
								new TimerTask() {
									public void run() {
										ByteBuffer buffer = ByteBuffer.allocateDirect(8);
										UsbRequest req = new UsbRequest();
										buffer.clear();
										if (req.initialize(connection, endpoint2)) {
											buffer.put((byte)5);
											buffer.put((byte)0);
											req.queue(buffer, 2);
											req = connection.requestWait();
										}
									}
								}, 250
							);
						}
					}
				}, (long)1000, (long)interval
			);
*/
			int last = 0;
			while (runUsbThread) {
/* input
				ByteBuffer buffer = ByteBuffer.allocateDirect(8);
				UsbRequest req = new UsbRequest();
				ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
				buffer.clear();
				UsbRequest req = new UsbRequest();
				if (req.initialize(connection, endpoint1)) {
					req.queue(buffer, 32);
					req = connection.requestWait();
					if (req != null) {
						buffer.flip();
						byte b[] = buffer.array();
						// check inputs
						if (b[0] == 0) logMessage("off"); else logMessage("on");
					}
				}
*/
				int value = 0;
				for (int i = 0; i < 8; i++) {
					value = value | ((button_states[i] ? 1 : 0) << i);
				}

				if (last != value) {
					ByteBuffer buffer = ByteBuffer.allocateDirect(8);
					UsbRequest req = new UsbRequest();
					buffer.clear();
					if (req.initialize(connection, endpoint2)) {
	//					buffer.put((byte)5);
						buffer.put((byte)value);
						req.queue(buffer, 1);
						req = connection.requestWait();
					}
					last = value;	
				}
			}

			ByteBuffer buffer = ByteBuffer.allocateDirect(8);
			UsbRequest req = new UsbRequest();
			buffer.clear();
			if (req.initialize(connection, endpoint2)) {
//				buffer.put((byte)5);
				buffer.put((byte)0);
				req.queue(buffer, 1);
				req = connection.requestWait();
			}

			//timer.cancel();
			//timer.purge();
			connection.releaseInterface(intf);
			connection.close();
			handler.post(new Runnable() {
				@Override
				public void run() {
					log.append("USB thread terminated.\n");
					scroll.fullScroll(View.FOCUS_DOWN);
				}
			});
		}
	}

	public void logMessage(final String message) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				log.append(message + "\n");
				scroll.fullScroll(View.FOCUS_DOWN);
			}
		});
	}
}