package strategiesNoGUI;

import java.io.IOException;

import stockGenie.BloombergAPICommunicator;

/**
 * Class to run all strategies and deliver one combined excel report.
 * We will build strategies and each one will have its own sheet in
 * the excel report.
 * @author devan
 */
public class AllStrategies {

	public static void main() {
		try {
			BloombergAPICommunicator bloomberg = new BloombergAPICommunicator("localhost", 8194);
		} catch (InterruptedException | IOException e) {
			return;
		}
		
	}
}
