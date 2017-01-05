package stockGenie;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import com.bloomberglp.blpapi.Element;
import com.bloomberglp.blpapi.Event;
import com.bloomberglp.blpapi.Message;
import com.bloomberglp.blpapi.Request;
import com.bloomberglp.blpapi.Service;
import com.bloomberglp.blpapi.Session;
import com.bloomberglp.blpapi.SessionOptions;
import com.bloomberglp.blpapi.MessageIterator;

public class BloombergAPICommunicator {
	
	public enum Index {
		DOWJONES, SP500, NASDAQ, SPTSX, MEXIPC, IBOVESPA, 
		EUROSTOXX, FTSE100, CAC40, DAX, IBEX35, FTSEMIB, 
		OMXSTKH30, SWISSMKT, NIKKEI, HANGSENG, CSI300, SPASX200
	}
	
	public enum Strategies {
		BASIC_INFORMATION
	}
	
	public enum HistoricalRequest {
		ALL, PX_OPEN, PX_HIGH, PX_LOW, PX_CLOSE, VOLUME
	}
	
	private final int sourceIdentifierID = 2;
	private final String sourceName = "Bloomberg API";
	private Session session;
	private Service refDataService;
	private Service histStudyService;
	private ClientGUI clientGUI;
	private int filesOutputted;
	
	public BloombergAPICommunicator(
			String ipAddress, int port, ClientGUI clientGUI)
			throws IOException, InterruptedException 
	{
		this.clientGUI = clientGUI;
		SessionOptions sessionOptions = new SessionOptions();
		sessionOptions.setServerHost(ipAddress);
		sessionOptions.setServerPort(port);
		session = new Session(sessionOptions);
		session.start();
		establishRefDataService();
		establishHistoricalStudyService();
		filesOutputted = 0;
	}
	
	private void establishRefDataService() 
			throws InterruptedException, IOException 
	{
		session.openService("//blp/refdata");
		refDataService = session.getService("//blp/refdata");
		clientGUI.makeUpdate("Opened the Reference Data Service", 
				sourceIdentifierID, sourceName);
	}
	
	private void establishHistoricalStudyService() 
			throws InterruptedException, IOException 
	{
		session.openService("//blp/tasvc");
		histStudyService = session.getService("//blp/tasvc");
		clientGUI.makeUpdate("Opened the Historical Study Service", 
				sourceIdentifierID, sourceName);
	}
	
	public void getIndexMembers(Index index) throws IOException {
		Request request = 
				refDataService.createRequest("ReferenceDataRequest");
		switch (index)
		{
			case DOWJONES: request.getElement("securities").appendValue("INDU INDEX");break;
			case SP500: request.getElement("securities").appendValue("SPX INDEX");break;
			case NASDAQ: request.getElement("securities").appendValue("NDX INDEX");break;
			case SPTSX: request.getElement("securities").appendValue("SPTSX INDEX");break;
			case MEXIPC:request.getElement("securities").appendValue("MEXBOL INDEX");break;
			case IBOVESPA: request.getElement("securities").appendValue("IBOV INDEX"); break;
			case EUROSTOXX:request.getElement("securities").appendValue("SX5E INDEX");break;
			case FTSE100: request.getElement("securities").appendValue("UKX INDEX");break;
			case CAC40: request.getElement("securities").appendValue("CAC INDEX"); break;
			case DAX: request.getElement("securities").appendValue("DAX INDEX");break;
			case IBEX35: request.getElement("securities").appendValue("IBEX INDEX");break;
			case FTSEMIB: request.getElement("securities").appendValue("FTSEMIB INDEX");break;
			case OMXSTKH30: request.getElement("securities").appendValue("OMX INDEX");break;
			case SWISSMKT:request.getElement("securities").appendValue("SMI INDEX");break;
			case NIKKEI:request.getElement("securities").appendValue("NKY INDEX"); break;
			case HANGSENG:request.getElement("securities").appendValue("HSI INDEX");break;
			case CSI300:request.getElement("securities").appendValue("SHSZ300 INDEX");break;
			case SPASX200:request.getElement("securities").appendValue("AS51 INDEX");break;
		}
		request.getElement("fields").appendValue("INDX_MEMBERS");
		request.getElement("fields").appendValue("COUNT_INDEX_MEMBERS");
		session.sendRequest(request, null);
		
		while (true) {
			Event event = null;
			try {
				event = session.nextEvent();
			} catch (InterruptedException e) {}
			if(event.eventType() == Event.EventType.RESPONSE) {
				readIndexResponse(event);
				break;
			}
			else if (event.eventType() == Event.EventType.PARTIAL_RESPONSE) {
				readIndexResponse(event);
			}
		}
	}
	
	private void readIndexResponse(Event indexResponseEvent) 
			throws IOException, FileNotFoundException 
	{
		MessageIterator it = indexResponseEvent.messageIterator();
		while (it.hasNext()) {
			Message message = it.next();
			Element messageAsElement = message.asElement();
			Element securityDataArray = messageAsElement.getElement("securityData");
			Element securityData = securityDataArray.getValueAsElement();
			Element fieldData = securityData.getElement("fieldData");
			
			//get the number of securities in this specific index
			int memberCount = fieldData.getElementAsInt32("COUNT_INDEX_MEMBERS");
			
			//iterate through the list and get all the tickers
			Element members = fieldData.getElement("INDX_MEMBERS");
			String [] tickers = new String[memberCount];
			for (int i = 0; i < memberCount; i++) {
				Element securityName = members.getValueAsElement(i);
				tickers[i] = securityName.getElementAsString("Member Ticker and Exchange Code");
			}
			//make the universe in the GUI and add their tickers
			clientGUI.createNewStockUniverse(memberCount);
			clientGUI.getStockUniverse().setTickers(tickers);
			//after the index members are found, request data for each stock
			this.requestStockDetails(BloombergAPICommunicator.Strategies.BASIC_INFORMATION);
			this.requestHistoricalPriceData(BloombergAPICommunicator.HistoricalRequest.ALL);
			clientGUI.updateTable();//update the table to show the stock tickers
		}
	}
	
	public void requestStockDetails(BloombergAPICommunicator.Strategies strategy) 
			throws IOException 
	{
		Request request = refDataService.createRequest("ReferenceDataRequest");
		Stock [] stocks = clientGUI.getStockUniverse().getStocks();
		Element securitiesElement = request.getElement("securities");
		for (int i = 0; i < stocks.length; i++) {
			securitiesElement.appendValue(stocks[i].ticker + " EQUITY");
		}
		
		switch (strategy) {
			//Basic Information: a simple request that will fill in our table
			//Company Name, PE Ratio, and the current price of the security
			//This will be called immediately after all index members are found
			//and the user will not call it, other updates may REFRESH these values, however
			case BASIC_INFORMATION: {
				request.getElement("fields").appendValue("NAME");
				request.getElement("fields").appendValue("PE_RATIO");
				request.getElement("fields").appendValue("LAST_PRICE");
				break;
			}
		}
		session.sendRequest(request, null);
		
		while (true) {
			Event event = null;
			try {
				event = session.nextEvent();
			} catch (InterruptedException e) {}
			
			if(event.eventType() == Event.EventType.RESPONSE) {
				readStocksResponse(event, strategy, stocks);
				break;
			}
			else if (event.eventType() == Event.EventType.PARTIAL_RESPONSE) {
				readStocksResponse(event, strategy, stocks);
			}
		}
	}
	
	public void readStocksResponse(Event event, 
			BloombergAPICommunicator.Strategies strategy, 
			Stock [] stocks) 
	{
		MessageIterator iter = event.messageIterator();
		while(iter.hasNext()) {
			Message message = iter.next();
			Element referenceDataRequest = message.asElement();
			Element securityDataArray = referenceDataRequest.getElement("securityData");
			int numberOfStocksInMessage = securityDataArray.numValues();
			//FOR LOOP WILL ITERATE THROUGH ALL THE STOCKS
			for (int i = 0; i < numberOfStocksInMessage; i++) {
				switch (strategy){
					case BASIC_INFORMATION: {
						Element singleStock = securityDataArray.getValueAsElement(i);
						int sequenceNumber = singleStock.getElementAsInt32("sequenceNumber");
						Element fieldData = singleStock.getElement("fieldData");
						if(fieldData.hasElement("NAME")) {
							stocks[sequenceNumber].companyName = fieldData.getElementAsString("NAME");
						}
						if(fieldData.hasElement("PE_RATIO")) {
							stocks[sequenceNumber].pe = fieldData.getElementAsFloat64("PE_RATIO");
						}	
						if(fieldData.hasElement("LAST_PRICE")) {
							stocks[sequenceNumber].price = fieldData.getElementAsFloat64("LAST_PRICE");
						}
						break;
					}
				}
			}
		}
	}
	
	public void requestHistoricalPriceData(
			BloombergAPICommunicator.HistoricalRequest requestType) 
			throws IOException {
		Request request = refDataService.createRequest("HistoricalDataRequest");
		Stock [] stocks = clientGUI.getStockUniverse().getStocks();
		
		//add in all the securities based on the index that was pulled
		Element securities = request.getElement("securities");
		for (int i = 0; i < stocks.length; i++)
			securities.appendValue(stocks[i].ticker + " EQUITY");
		
		Element fields = request.getElement("fields");
		switch(requestType) {
			case PX_OPEN: {
				fields.appendValue("PX_OPEN");
				break;
			}
			case PX_HIGH: {
				fields.appendValue("PX_HIGH");
				break;
			}
			case PX_LOW: {
				fields.appendValue("PX_LOW");
				break;
			}
			case PX_CLOSE: {
				fields.appendValue("PX_CLOSE_1D");
				break;
			}
			case VOLUME: {
				fields.appendValue("PX_VOLUME");
				break;
			}
			case ALL: {
				fields.appendValue("PX_OPEN");
				fields.appendValue("PX_HIGH");
				fields.appendValue("PX_LOW");
				fields.appendValue("PX_CLOSE_1D");
				fields.appendValue("PX_VOLUME");
				break;
			}
		}
		
		request.set("periodicityAdjustment", "ACTUAL");
		request.set("periodicitySelection", "DAILY");
		request.set("startDate", "20060101");
		request.set("endDate", "20061231");
		request.set("maxDataPoints", 500);
		request.set("returnEids", true);
		session.sendRequest(request, null);
		
		while (true) {
			Event event = null;
			try {
				event = session.nextEvent();
				readHistoricalResponse(event, stocks, requestType);
				if (event.eventType() == Event.EventType.RESPONSE)
					break;
			} 
			catch (InterruptedException e) {}
			catch (FileNotFoundException e) {}
		}
	}
	
	private void readHistoricalResponse(Event event, Stock [] stocks, BloombergAPICommunicator.HistoricalRequest request) throws FileNotFoundException {
		//OVERALL MESSAGE : HistoricalDataResponse
		//----> securityData
		//----> ----> sequenceNumber
		//----> ----> fieldData []
		//----> ----> ----> fieldData values
		
		MessageIterator msgIter = event.messageIterator();
		
		while (msgIter.hasNext()) {

			Message message = msgIter.next();
			
			
			Element messageAsElement = message.asElement();


			if (filesOutputted == 0) {
				PrintWriter pw = new PrintWriter(new File("volumeOutput.txt"));
				pw.println(messageAsElement);
				pw.flush();
				pw.close();
				filesOutputted++;
				pw.close();
			}
			
			Element securityDataElement = messageAsElement.getElement("securityData");
			int sequenceNumber = securityDataElement.getElementAsInt32("sequenceNumber");
			Stock stock = stocks[sequenceNumber];
			Element fieldDataArray = securityDataElement.getElement("fieldData");
			
			int numberOfValues = fieldDataArray.numValues();
			switch (request) {
				case PX_OPEN: {
					stock.px_open = new double[numberOfValues];
					//fill in all the values
					for (int i = 0; i < numberOfValues; i++)
						stock.px_open[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_OPEN");
					break;
				}
				case PX_HIGH: {
					stock.px_high = new double[numberOfValues];
					for (int i = 0; i < numberOfValues; i++)
						stock.px_high[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_HIGH");
					break;
				}
				case PX_LOW: {
					stock.px_low = new double[numberOfValues];
					for (int i = 0; i < numberOfValues; i++)
						stock.px_low[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_LOW");
					break;
				}
				case PX_CLOSE:{
					stock.px_close = new double[numberOfValues];
					for (int i = 0; i < numberOfValues; i++)
						stock.px_close[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_CLOSE_1D");
					break;
				}
				case VOLUME: {
					stock.volume = new double[numberOfValues];
					for (int i = 0; i < numberOfValues; i++)
						stock.volume[i] = fieldDataArray.getValueAsElement(i).getElementAsInt32("PX_VOLUME");
					break;
				}
				case ALL: {
					stock.px_open = new double[numberOfValues];
					stock.px_close = new double[numberOfValues];
					stock.px_high = new double[numberOfValues];
					stock.px_low = new double[numberOfValues];
					stock.volume = new double[numberOfValues];
					for (int i = 0; i < numberOfValues; i++) {
						stock.px_open[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_OPEN");
						stock.px_high[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_HIGH");
						stock.px_low[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_LOW");
						stock.px_close[i] = fieldDataArray.getValueAsElement(i).getElementAsFloat64("PX_CLOSE_1D");
						stock.volume[i] = fieldDataArray.getValueAsElement(i).getElementAsInt32("PX_VOLUME");
					}
					break;
				}
			}
		}
		
	}
}