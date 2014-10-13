package de.persosim.driver.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashMap;

import de.persosim.driver.connector.NativeDriverInterface;
import de.persosim.driver.connector.UnsignedInteger;

/**
 * This {@link Thread} communicates for the test driver and manages the
 * connections.
 * 
 * @author mboonk
 *
 */
public class TestDriverCommunicationThread extends Thread implements
		NativeDriverInterface {

	private ServerSocket serverSocket;
	private HashMap<Integer, Socket> lunMapping;
	private HashMap<Integer, HandshakeData> lunHandshakeData;
	private HandshakeData currentHandshake;
	private Socket clientSocket;
	private boolean isRunning = false;
	private static final byte MAX_LUNS = 10;

	private UnsignedInteger getLowestFreeLun() {

		for (byte i = 0; i < MAX_LUNS; i++) {
			if (!lunMapping.containsKey(i))
				return new UnsignedInteger(i);
		}
		return NativeDriverInterface.LUN_NOT_ASSIGNED;
	}

	public TestDriverCommunicationThread(int port,
			HashMap<Integer, Socket> lunMapping,
			Collection<DriverEventListener> listeners) throws IOException {
		this.setName("TestDriverCommunicationThread");
		this.lunMapping = lunMapping;
		lunHandshakeData = new HashMap<Integer, HandshakeData>();

		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				isRunning = true;
				clientSocket = serverSocket.accept();
				System.out.println("New local socket on port: "
						+ clientSocket.getPort());
				BufferedReader bufferedIn = new BufferedReader(
						new InputStreamReader(clientSocket.getInputStream()));

				String data = null;
				boolean handshakeDone = false;
				while (!handshakeDone && (data = bufferedIn.readLine()) != null) {
					BufferedWriter bufferedOut = new BufferedWriter(
							new OutputStreamWriter(
									clientSocket.getOutputStream()));
					System.out.println("TestDriver received data from the connector:\t" + data);
					String dataToSend = doHandshake(data);

					if (dataToSend == null) {
						if (data.equals(MESSAGE_ICC_STOP.getAsHexString())) {
							clientSocket.close();
							System.out.println("Local socket on port "
									+ clientSocket.getPort() + " closed.");
						}
						handshakeDone = true;
					} else {
						bufferedOut.write(dataToSend);
						bufferedOut.newLine();
						bufferedOut.flush();
						System.out.println("TestDriver sent data to the connector:\t" + dataToSend);
					}
					
				}
			} catch (SocketException e) {
				// ignore, expected behaviour on interrupt
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private String doHandshake(String data) {
		String[] split = data.split("\\|");
		if (split.length > 0) {
			switch (UnsignedInteger.parseUnsignedInteger(split[0], 16).getAsInt()) {
			case VALUE_MESSAGE_ICC_HELLO:
				if (split.length > 1) {
					UnsignedInteger lun = NativeDriverInterface.LUN_NOT_ASSIGNED;
					if (split.length > 1) {
						lun = UnsignedInteger.parseUnsignedInteger(split[1], 16);
						if (lun.equals(NativeDriverInterface.LUN_NOT_ASSIGNED)) {
							lun = getLowestFreeLun();
						}
						if (lun.getAsSignedLong() > MAX_LUNS) {
							return MESSAGE_IFD_ERROR.getAsHexString();
						}
					}
					if (lunMapping.containsKey(lun.getAsInt())) {
						try {
							lunMapping.get(lun.getAsInt()).close();
							System.out.println("Closed socket on port: "
									+ lunMapping.get(lun.getAsInt()).getPort());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					lunMapping.put(lun.getAsInt(), clientSocket);
					if (lunHandshakeData.containsKey(lun)) {
						currentHandshake = lunHandshakeData.get(lun);
					} else {
						currentHandshake = new HandshakeData();
						currentHandshake.setLun(lun);
						lunHandshakeData.put(lun.getAsInt(), currentHandshake);
					}
					return MESSAGE_IFD_HELLO.getAsHexString() + "|" + lun.getAsHexString();
				}
				break;
			case VALUE_MESSAGE_ICC_STOP:
				UnsignedInteger lun = currentHandshake.getLun();
				if (lunHandshakeData.containsKey(lun.getAsInt())
						&& !lunHandshakeData.get(lun.getAsInt()).isHandshakeDone()) {
					return MESSAGE_IFD_ERROR.getAsHexString();
				}
				lunHandshakeData.remove(lun);
				currentHandshake = null;
				return null;

			case VALUE_MESSAGE_ICC_DONE:
				currentHandshake.setHandshakeDone(true);
				return null;
			}
		}
		return MESSAGE_ICC_ERROR.getAsHexString();
	}

	public int getPort() {
		return serverSocket.getLocalPort();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#interrupt()
	 */
	@Override
	public void interrupt() {
		super.interrupt();
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public boolean isRunning() {
		return isRunning;
	}
}
