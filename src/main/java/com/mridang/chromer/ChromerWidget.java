package com.mridang.chromer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.apps.dashclock.api.ExtensionData;

import org.acra.ACRA;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/*
 * This class is the main class that provides the widget
 */
public class ChromerWidget extends ImprovedExtension {

	/**
	 * The handler class that runs every second to update the notification with the processor usage.
	 */
	private class DiscoveryHandler extends Handler {

		/**
		 * The DIAL discovery packet sent over UDP to discover services
		 */
		private final DatagramPacket pakSearch;
		/**
		 * The DIAL discovery packet sent over UDP to discover services
		 */
		private final DatagramPacket pakReceive;
		/**
		 * The DIAL service that should be looked for
		 */
		private static final String SEARCH_TARGET = "urn:dial-multiscreen-org:service:dial:1";
		/**
		 * The DIAL service Packet that should be sent out
		 */
		private static final String M_SEARCH = "M-SEARCH * HTTP/1.1\r\n" + "HOST: 239.255.255.250:1900\r\n"
				+ "MAN: \"ssdp:discover\"\r\n" + "MX: 10\r\n" + "ST: " + SEARCH_TARGET + "\r\n\r\n";
		/**
		 * The UDP port number to send the DIAL probe chatter to.
		 */
		private final Integer BROADCAST_SERVER_PORT = 1900;
		/**
		 * The location header sent back in the DIAL discovery response
		 */
		private final String HEADER_LOCATION = "LOCATION";
		/**
		 * The service header sent back in the DIAL discovery response
		 */
		private final String HEADER_ST = "ST";
		/**
		 * The instance of the datagram socket from which to send packets
		 */
		private DatagramSocket udpSocket = null;
		/**
		 * The number of detected displays on the network
		 */
		private Integer intDisplays = 0;

		/**
		 * Simple constructor to initialize the initial value of the previous
		 */
		public DiscoveryHandler(Looper looLooper) {

			super(looLooper);

			try {

				Log.d(ChromerWidget.this.getTag(), "Initializing the Packet handler");
				this.pakSearch = new DatagramPacket(M_SEARCH.getBytes(), M_SEARCH.getBytes().length);
				this.pakSearch.setPort(BROADCAST_SERVER_PORT);
				this.pakSearch.setAddress(InetAddress.getByName("239.255.255.250"));

				byte[] buffer = new byte[1024];
				this.pakReceive = new DatagramPacket(buffer, buffer.length);

			} catch (Exception e) {
				Log.e(ChromerWidget.this.getTag(), "An unknown error occurred", e);
				throw new RuntimeException();
			} finally {
				if (udpSocket != null && udpSocket.isClosed()) {
					udpSocket.close();
				}
			}

		}

		/**
		 * Handler method that that constantly listens on the UDP socket for responses from the DIAL
		 * services and shows a the notification icon with the device details.
		 */
		@Override
		public void handleMessage(Message msgMessage) {

			ChromerWidget.hndDiscoverer.sendEmptyMessageDelayed(1, 5000L);

			try {

				Log.v(ChromerWidget.this.getTag(), "Sending a broadcast UDP packet to discover services");
				this.udpSocket = new DatagramSocket();
				this.udpSocket.setBroadcast(true);
				this.udpSocket.send(pakSearch);

				Log.v(ChromerWidget.this.getTag(), "Checking for any received broadcast UDP chatter");
				this.udpSocket.setSoTimeout(4000);
				this.udpSocket.receive(pakReceive);

				String strPacket = new String(pakReceive.getData(), 0, pakReceive.getLength());
				String strTokens[] = strPacket.trim().split("\\n");

				Boolean booLocation = false;
				Boolean booService = false;

				for (String strToken1 : strTokens) {

					String strToken = strToken1.trim();
					if (strToken.startsWith(HEADER_LOCATION)) {
						booLocation = true;
					} else if (strToken.startsWith(HEADER_ST)) {

						String strService = strToken.substring(4).trim();
						if (strService.equals(SEARCH_TARGET)) {
							booService = true;
						}

					}

				}

				if (!booService || !booLocation) {

					this.intDisplays = 0;
					ChromerWidget.mgrNotifications.cancel(114);

				} else {

					Log.v(ChromerWidget.this.getTag(), "Received a DIAL response packet");
					this.intDisplays = 1;
					ChromerWidget.mgrNotifications.notify(114, ChromerWidget.notBuilder.build());

				}

			} catch (SocketTimeoutException e) {
				Log.v(ChromerWidget.this.getTag(), "Socket timed-out waiting for chatter; retrying");
			} catch (InterruptedIOException e) {
				Log.v(ChromerWidget.this.getTag(), "No reponse received from the DIAL services; retrying");
			} catch (IOException e) {
				Log.v(ChromerWidget.this.getTag(), "Error sending/receiving the response packet");
			}

		}

		/**
		 * Returns the current number of detected displays on the network
		 *
		 * @return The number of detected displays on the network
		 */
		public Integer getDetectedDisplays() {
			return intDisplays;
		}

	}

	/**
	 * The instance of the handler that updates the notification
	 */
	private static DiscoveryHandler hndDiscoverer;
	/**
	 * The instance of the manager of the notification services
	 */
	private static NotificationManager mgrNotifications;
	/**
	 * The instance of the notification builder to rebuild the notification
	 */
	private static NotificationCompat.Builder notBuilder;
	/**
	 * The instance of the connectivity manager to check the WiFi status
	 */
	private ConnectivityManager cmrConnectivity;

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getIntents()
	 */
	@Override
	protected IntentFilter getIntents() {

		IntentFilter itfIntents = new IntentFilter();
		itfIntents.addAction(Intent.ACTION_SCREEN_ON);
		itfIntents.addAction(Intent.ACTION_SCREEN_OFF);
		itfIntents.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		return itfIntents;

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getTag()
	 */
	@Override
	protected String getTag() {
		return getClass().getSimpleName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#getUris()
	 */
	@Override
	protected String[] getUris() {
		return null;
	}

	/*
	 * @see
	 * com.google.android.apps.dashclock.api.DashClockExtension#onUpdateData
	 * (int)
	 */
	@Override
	protected void onUpdateData(int intReason) {

		Log.d(getTag(), "Checking for any wireless displays on the network");
		final ExtensionData edtInformation = new ExtensionData();
		setUpdateWhenScreenOn(true);

		try {

			Integer intDisplays = hndDiscoverer.getDetectedDisplays();
			if (hndDiscoverer.getDetectedDisplays() > 0) {
				edtInformation.expandedTitle(getString(R.string.found_devices));
			} else {
				edtInformation.expandedTitle(getString(R.string.no_devices));
			}
			edtInformation.expandedBody(getQuantityString(R.plurals.devices, intDisplays, intDisplays));
			edtInformation.status(hndDiscoverer.getDetectedDisplays().toString());
			edtInformation.visible(true);

		} catch (Exception e) {
			edtInformation.visible(false);
			Log.e(getTag(), "Encountered an error", e);
			ACRA.getErrorReporter().handleSilentException(e);
		}

		edtInformation.icon(R.drawable.ic_dashclock);
		edtInformation.clickIntent(new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$WifiDisplaySettingsActivity")));
		doUpdate(edtInformation);

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#onInitialize(java.lang.Boolean)
	 */
	@Override
	protected void onInitialize(boolean booReconnect) {

		super.onInitialize(booReconnect);
		mgrNotifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		cmrConnectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		Intent ittSettings = new Intent();
		ittSettings.setComponent(new ComponentName("com.android.settings",
				"com.android.settings.Settings$WifiDisplaySettingsActivity"));
		PendingIntent pitSettings = PendingIntent.getActivity(this, 0, ittSettings, 0);
		notBuilder = new NotificationCompat.Builder(this);
		notBuilder = notBuilder.setSmallIcon(R.drawable.ic_dashclock);
		notBuilder = notBuilder.setContentIntent(pitSettings);
		notBuilder = notBuilder.setOngoing(true);
		notBuilder = notBuilder.setWhen(0);
		notBuilder = notBuilder.setOnlyAlertOnce(true);
		notBuilder = notBuilder.setPriority(Integer.MAX_VALUE);
		notBuilder = notBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
		notBuilder = notBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
		notBuilder = notBuilder.setContentTitle(getString(R.string.displays_detected));
		notBuilder = notBuilder.setContentText(getString(R.string.cast_wirelessly));

		Looper.prepare();
		hndDiscoverer = new DiscoveryHandler(Looper.myLooper());
		Looper.loop();

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#onDestroy()
	 */
	@Override
	public void onDestroy() {

		hndDiscoverer.removeMessages(1);
		mgrNotifications.cancel(111);
		super.onDestroy();

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.hardware.ImprovedExtension#onReceiveIntent(android.content.Context, android.content.Intent)
	 */
	@Override
	protected void onReceiveIntent(Context ctxContext, Intent ittIntent) {

		if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {

			Log.d(getTag(), "Screen off; stopping the handler and hiding the notification");
			hndDiscoverer.removeMessages(1);
			mgrNotifications.cancel(114);

		} else if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {

			Log.d(getTag(), "Screen on; checking if connected");
			if (cmrConnectivity != null && cmrConnectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null
					&& cmrConnectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {

				Log.d(getTag(), "WiFi connected; starting the handler");
				hndDiscoverer.removeMessages(1);
				hndDiscoverer.sendEmptyMessage(1);

			}

		} else if (ittIntent.getAction().equalsIgnoreCase(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

			Log.d(getTag(), "WiFi event; checking if connected");
			if (ittIntent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO) != null) {

				Log.d(getTag(), "WiFi connected; starting the handler");
				hndDiscoverer.removeMessages(1);
				hndDiscoverer.sendEmptyMessage(1);

			} else {

				Log.d(getTag(), "WiFi disconnected; stopping the handler and hiding the notification");
				hndDiscoverer.removeMessages(1);
				mgrNotifications.cancel(114);

			}

		}

		onUpdateData(UPDATE_REASON_MANUAL);

	}

}
