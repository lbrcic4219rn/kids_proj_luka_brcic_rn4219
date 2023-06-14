package app;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cli.CLIParser;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiMutex;
import servent.SimpleServentListener;
import servent.message.mutex.SKTokenMessage;
import servent.message.util.FifoSendWorker;
import servent.message.util.MessageUtil;

/**
 * Describes the procedure for starting a single Servent
 *
 * @author bmilojkovic
 */
public class ServentMain {

	/**
	 * Command line arguments are:
	 * 0 - path to servent list file
	 * 1 - this servent's id
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			AppConfig.timestampedErrorPrint("Please provide servent list file and id of this servent.");
		}
		
		int serventId = -1;
		int portNumber = -1;
		
		String serventListFile = args[0];
		
		AppConfig.readConfig(serventListFile);
		
		try {
			serventId = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			AppConfig.timestampedErrorPrint("Second argument should be an int. Exiting...");
			System.exit(0);
		}
		
		if (serventId >= AppConfig.getServentCount()) {
			AppConfig.timestampedErrorPrint("Invalid servent id provided");
			System.exit(0);
		}
		
		AppConfig.myServentInfo = AppConfig.getInfoById(serventId);
		
		try {
			portNumber = AppConfig.myServentInfo.getListenerPort();
			
			if (portNumber < 1000 || portNumber > 2000) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			AppConfig.timestampedErrorPrint("Port number should be in range 1000-2000. Exiting...");
			System.exit(0);
		}
		
		MessageUtil.initializePendingMessages();
		
		AppConfig.timestampedStandardPrint("Starting servent " + AppConfig.myServentInfo);
		
		DistributedMutex mutex = null;
		
		switch (AppConfig.MUTEX_TYPE) {
			case SUZUKI_KASAMI:
				mutex = new SuzukiKasamiMutex();
				break;
			default:
				mutex = null;
				AppConfig.timestampedErrorPrint("Unknown mutex type in config.");
				break;
		}

		if(AppConfig.myServentInfo.getId() == 1) {
			System.out.println("JA SAM");
			AppConfig.myServentInfo.setHasToken(true);
			AppConfig.myServentInfo.setToken(new SKTokenMessage(null, null, new ConcurrentHashMap<>(), new LinkedList<>()));
		}
		
		SimpleServentListener simpleListener = new SimpleServentListener(mutex);
		Thread listenerThread = new Thread(simpleListener);
		listenerThread.start();
		
		List<FifoSendWorker> senderWorkers = new ArrayList<>();
		if (AppConfig.IS_FIFO) {
			for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
				FifoSendWorker senderWorker = new FifoSendWorker(neighbor);
				
				Thread senderThread = new Thread(senderWorker);
				
				senderThread.start();
				
				senderWorkers.add(senderWorker);
			}
			
		}
		
		CLIParser cliParser = new CLIParser(simpleListener, senderWorkers, mutex);
		Thread cliThread = new Thread(cliParser);
		cliThread.start();
	}
}
