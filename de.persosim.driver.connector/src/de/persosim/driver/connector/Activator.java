package de.persosim.driver.connector;

import java.io.IOException;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import de.persosim.simulator.Simulator;

/**
 * The activator for this bundle.
 * @author mboonk
 *
 */
public class Activator implements BundleActivator {

	private static BundleContext context;
	private static ServiceTracker<Simulator, Simulator> simulatorServiceTracker;
	private static NativeDriverConnector connector;

	static BundleContext getContext() {
		return context;
	}
	
	/**
	 * @return the OSGi-provided simulator service or null if it is not available
	 */
	public static Simulator getSim() {
		if (simulatorServiceTracker != null){

			return simulatorServiceTracker.getService();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		try {
			if (connector == null) {
				connector = new NativeDriverConnector("localhost", 5678);
				connector.connect();
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Activator.context = bundleContext;
		
		simulatorServiceTracker = new ServiceTracker<Simulator, Simulator>(context, Simulator.class.getName(), null);
		simulatorServiceTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;
		if (connector != null) {
			connector.disconnect();
		}
		simulatorServiceTracker.close();
	}

}
