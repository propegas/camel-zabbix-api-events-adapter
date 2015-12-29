package ru.atc.camel.zabbix.api.events;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.lang.ArrayUtils;
//import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
//import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
//import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonObject;
import io.github.hengyunabc.zabbix.api.DefaultZabbixApi;
import io.github.hengyunabc.zabbix.api.Request;
import io.github.hengyunabc.zabbix.api.RequestBuilder;
import ru.at_consulting.itsm.device.Device;
import ru.at_consulting.itsm.event.Event;
import scala.xml.dtd.ParameterEntityDecl;

public class ZabbixAPIConsumer extends ScheduledPollConsumer {

	private static Logger logger = LoggerFactory.getLogger(Main.class);

	private static ZabbixAPIEndpoint endpoint;

	//private static String SavedWStoken;

	private static CloseableHttpClient httpClient;

	public enum PersistentEventSeverity {
		OK, INFO, WARNING, MINOR, MAJOR, CRITICAL;

		public String value() {
			return name();
		}

		public static PersistentEventSeverity fromValue(String v) {
			return valueOf(v);
		}
	}

	public ZabbixAPIConsumer(ZabbixAPIEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		ZabbixAPIConsumer.endpoint = endpoint;
		// this.afterPoll();
		this.setTimeUnit(TimeUnit.MINUTES);
		this.setInitialDelay(0);
		this.setDelay(endpoint.getConfiguration().getDelay());
	}

	@Override
	protected int poll() throws Exception {

		String operationPath = endpoint.getOperationPath();

		if (operationPath.equals("events"))
			return processSearchEvents();

		// only one operation implemented for now !
		throw new IllegalArgumentException("Incorrect operation: " + operationPath);
	}

	@Override
	public long beforePoll(long timeout) throws Exception {

		logger.info("*** Before Poll!!!");
		// only one operation implemented for now !
		// throw new IllegalArgumentException("Incorrect operation: ");

		// send HEARTBEAT
		genHeartbeatMessage(getEndpoint().createExchange());

		return timeout;
	}

	private int processSearchEvents() throws ClientProtocolException, IOException, Exception {

		// Long timestamp;

		List<Device> hostsList = new ArrayList<Device>();
		List<Device> hostgroupsList = new ArrayList<Device>();
		List<Device> listFinal = new ArrayList<Device>();
		
		List<Event> eventList = new ArrayList<Event>();

		String eventsuri = endpoint.getConfiguration().getZabbixapiurl();
		String uri = String.format("%s", eventsuri);

		System.out.println("***************** URL: " + uri);

		logger.info("Try to get Events.");
		// logger.info("Get events URL: " + uri);

		//JsonObject json = null;

		DefaultZabbixApi zabbixApi = null;
		try {
			String zabbixapiurl = endpoint.getConfiguration().getZabbixapiurl();
			String username = endpoint.getConfiguration().getUsername();
			String password = endpoint.getConfiguration().getPassword();
			int lasteventid = Integer.parseInt(endpoint.getConfiguration().getLasteventid());
			
			// String url = "http://192.168.90.102/zabbix/api_jsonrpc.php";
			zabbixApi = new DefaultZabbixApi(zabbixapiurl);
			zabbixApi.init();

			boolean login = zabbixApi.login(username, password);
			System.err.println("login:" + login);
			
			
			logger.debug("Last Event ID: " + lasteventid);
			// lastid = 0 (first execution)
			if ( lasteventid == 0 ) {
				// Get all Actual Triggers (eventid) from Zabbix
				logger.info("Try to get actual opened triggers");
				String[] openTriggerEventIDs = getOpenTriggers(zabbixApi);
				
				// Get all Actual Events for Triggers from Zabbix
				logger.info("Try to get events for them");
				List<Event> actualevents = getEventsByID(zabbixApi, openTriggerEventIDs);
			}
			
			
			// Get all Hosts from Zabbix
			//hostsList = getAllHosts(zabbixApi);
			if (hostsList != null)
				listFinal.addAll(hostsList);
			
			// Get all HostGroups from Zabbix
			//hostgroupsList = getAllHostGroups(zabbixApi);
			if (hostgroupsList != null)
				listFinal.addAll(hostgroupsList);
			
		
			for (int i = 0; i < listFinal.size(); i++) {
				logger.info("Create Exchange container");
				Exchange exchange = getEndpoint().createExchange();
				exchange.getIn().setBody(listFinal.get(i), Device.class);
				exchange.getIn().setHeader("DeviceId", listFinal.get(i).getId());
				exchange.getIn().setHeader("DeviceType", listFinal.get(i).getDeviceType());
				exchange.getIn().setHeader("queueName", "Devices");

				try {
					getProcessor().process(exchange);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			logger.info("Sended Devices: " + listFinal.size());
			
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(String.format("Error while get Devices from API: %s ", e));
			genErrorMessage(e.getMessage() + " " + e.toString());
			httpClient.close();
			return 0;
		} catch (Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(String.format("Error while get Devices from API: %s ", e));
			genErrorMessage(e.getMessage() + " " + e.toString());
			httpClient.close();
			zabbixApi.destory();
			return 0;
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(String.format("Error while get Devices from API: %s ", e));
			genErrorMessage(e.getMessage() + " " + e.toString());
			httpClient.close();
			zabbixApi.destory();
			return 0;
		} finally {
			logger.debug(String.format(" **** Close zabbixApi Client: %s", zabbixApi.toString()));
			// httpClient.close();
			zabbixApi.destory();
			// dataSource.close();
			// return 0;
		}

		return 1;
	}

	private List<Event> getEventsByID(DefaultZabbixApi zabbixApi, String[] openTriggerEventIDs) {
		// TODO Auto-generated method stub
		
		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			JSONObject filter = new JSONObject();
			// JSONObject output = new JSONObject();

			filter.put("eventids", openTriggerEventIDs);
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("event.get")
					// .paramEntry("search", search)
					.paramEntry("output", "extend").paramEntry("only_true", "1")
					.paramEntry("selectHosts", new String[] { "hostid", "host", "name" })
					.paramEntry("select_alerts", new String[] { "alertid", "actionid", "eventid", "message" })
					.paramEntry("selectRelatedObject",
							new String[] { "triggerid", "description", "status", "value", "priority" })
					.paramEntry("sortfield", new String[] { "clock", "eventid" })
					.paramEntry("sortorder", new String[] { "ASC" })
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get Open Events by ID");
		}
		
		JSONArray openEvents;
		try {
			getResponse = zabbixApi.call(getRequest);
			//System.err.println(getResponse);

			openEvents = getResponse.getJSONArray("result");
			//System.err.println(openEvents);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for Open Events by ID");
		}
		
		List<Event> eventList = new ArrayList<Event>();
		
		List<Event> listFinal = new ArrayList<Event>();
		//List<Device> listFinal = new ArrayList<Device>();
		//String device_type = "host";
		String ParentID = "";
		String newhostname = "";
		logger.info("Finded Zabbix Events: " + eventList.size());

		for (int i = 0; i < openEvents.size(); i++) {
			
			ParentID = "";
			//newhostname = "";
			// logger.debug(f.toString());
			// ZabbixAPIHost host = new ZabbixAPIHost();
			JSONObject event = openEvents.getJSONObject(i);
			String hostname = event.getString("host");
			
			newhostname = hostname;
			// Example: KRL-PHOBOSAU--MSSQL
			if (hostname.matches("(.*)--(.*)")){
				
				logger.info("Finded Zabbix Host with Aliases: " + hostname);
				newhostname = checkEventHostAliases(hostname);
				//ParentID = checkreturn[0];
				//newhostname = hostname;
			}
			
			logger.debug("*** Received JSON Event: " + event.toString());
			
			JSONArray alerts = event.getJSONArray("alerts");
			//JSONArray hosttemplates = event.getJSONArray("parentTemplates");
			//JSONArray hostitems = event.getJSONArray("items");
			//JSONArray hostmacros = event.getJSONArray("macros");
			
			for (int x = 0; x < alerts.size(); x++) {
				// logger.debug(f.toString());
				// ZabbixAPIHost host = new ZabbixAPIHost();
				JSONObject alert = alerts.getJSONObject(x);
				// JSONArray hostgroups = host.getJSONArray("groups");

				logger.debug("******** Received JSON Alert: " + alerts.toString());
			}

	
			
		}

		return eventList;
	}

	private String[] getOpenTriggers(DefaultZabbixApi zabbixApi) {
		// TODO Auto-generated method stub

		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			 JSONObject filter = new JSONObject();
			// JSONObject output = new JSONObject();

			 filter.put("filter", new int[] { 0, 1 });
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("trigger.get")
					//.paramEntry("search", search)
					.paramEntry("output", "extend")
					.paramEntry("only_true", "1" )
					.paramEntry("selectLastEvent", "extend" )
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get all Open Triggers.");
		}
		
		JSONArray openTriggers;
		try {
			getResponse = zabbixApi.call(getRequest);
			//System.err.println(getResponse);

			openTriggers = getResponse.getJSONArray("result");
			System.err.println(openTriggers);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
		}
		
		String[] eventids = new String[]{ };
		
		for (int i = 0; i < openTriggers.size(); i++) {
			//device_type = "group";

			JSONObject trigger = openTriggers.getJSONObject(i);
			// "triggerid": "13779"
			String triggerid = trigger.getString("triggerid");
			// Example: KRL-PHOBOSAU--MSSQL
			
			// "lastEvent": {
			// 		"eventid": "327723"
			String eventid = trigger.getJSONObject("lastEvent").getString("eventid");
			String status = trigger.getJSONObject("lastEvent").getString("value");
			
			eventids = (String[]) ArrayUtils.add(eventids, eventid);
			
			logger.debug("*** Received JSON EventID: " + triggerid);
			logger.debug("*** Received JSON Event Value: " + status);
			
			//Device gendevice = new Device();
			//gendevice = genHostgroupObj(hostgroup, device_type, "");
					
		}
		
		logger.debug("*** Received JSON All Actual EventID: " + eventids.length);
		
		return eventids;
	}

	private List<Device> getAllHostGroups(DefaultZabbixApi zabbixApi) {
		// TODO Auto-generated method stub
		
		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			 JSONObject search = new JSONObject();
			// JSONObject output = new JSONObject();

			 search.put("name", new String[] { "[*]*" });
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("hostgroup.get")
					.paramEntry("search", search)
					.paramEntry("output", "extend")
					.paramEntry("searchWildcardsEnabled", 1 )
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get all Groups.");
		}

		// logger.info(" *** Get All host JSON params: " + params.toString());

		// JsonObject json = null;
		JSONArray hostgroups;
		try {
			getResponse = zabbixApi.call(getRequest);
			System.err.println(getResponse);

			hostgroups = getResponse.getJSONArray("result");
			System.err.println(hostgroups);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Hosts.");
		}
		List<Device> deviceList = new ArrayList<Device>();
		
		//List<Device> listFinal = new ArrayList<Device>();
		//List<Device> listFinal = new ArrayList<Device>();
		String device_type = "group";
		//String ParentID = "";
		//String newhostname = "";
		logger.info("Finded Zabbix Groups: " + hostgroups.size());
		
		for (int i = 0; i < hostgroups.size(); i++) {
			device_type = "group";

			JSONObject hostgroup = hostgroups.getJSONObject(i);
			String hostgroupname = hostgroup.getString("name");
			// Example: KRL-PHOBOSAU--MSSQL
			
			logger.debug("*** Received JSON Group: " + hostgroup.toString());
			
			Device gendevice = new Device();
			gendevice = genHostgroupObj(hostgroup, device_type, "");
			deviceList.add(gendevice);
			
		}
		
		return deviceList;
	}

	private List<Device> getAllHosts(DefaultZabbixApi zabbixApi) {

		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			// JSONObject filter = new JSONObject();
			// JSONObject output = new JSONObject();

			// filter.put("host", new String[] { host1, host2 });
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("host.get")
					// .paramEntry("filter", filter)
					.paramEntry("output", new String[] { "hostid", "name", "host" })
					.paramEntry("selectMacros", new String[] { "hostmacroid", "macro", "value" })
					.paramEntry("selectGroups", "extend")
					.paramEntry("selectParentTemplates", new String[] { "templateeid", "host", "name" })
					.paramEntry("selectItems", new String[] { "itemid", "name", "key_", "description" }).build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get all Hosts.");
		}

		// logger.info(" *** Get All host JSON params: " + params.toString());

		// JsonObject json = null;
		JSONArray hosts;
		try {
			getResponse = zabbixApi.call(getRequest);
			System.err.println(getResponse);

			hosts = getResponse.getJSONArray("result");
			System.err.println(hosts);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Hosts.");
		}
		List<Device> deviceList = new ArrayList<Device>();
		
		List<Device> listFinal = new ArrayList<Device>();
		//List<Device> listFinal = new ArrayList<Device>();
		String device_type = "host";
		String ParentID = "";
		String newhostname = "";
		logger.info("Finded Zabbix Hosts: " + hosts.size());

		for (int i = 0; i < hosts.size(); i++) {
			device_type = "host";
			ParentID = "";
			newhostname = "";
			// logger.debug(f.toString());
			// ZabbixAPIHost host = new ZabbixAPIHost();
			JSONObject host = hosts.getJSONObject(i);
			String hostname = host.getString("host");
			// Example: KRL-PHOBOSAU--MSSQL
			if (hostname.matches("(.*)--(.*)")){
				
				logger.info("Finded Zabbix Host with Aliases: " + hostname);
				String[] checkreturn = {""} ; //checkEventHostAliases(hosts, hostname);
				ParentID = checkreturn[0];
				newhostname = checkreturn[1];
			}
			
			JSONArray hostgroups = host.getJSONArray("groups");
			JSONArray hosttemplates = host.getJSONArray("parentTemplates");
			JSONArray hostitems = host.getJSONArray("items");
			JSONArray hostmacros = host.getJSONArray("macros");

			logger.debug("*** Received JSON Host: " + host.toString());
			
			// if ParentID is not set in host name
			// Example: KRL-PHOBOSAU--MSSQL
			if (ParentID.equals("")){
				hostgroupsloop:
					for (int j = 0; j < hostgroups.size(); j++) {
						// logger.debug(f.toString());
						// ZabbixAPIHost host = new ZabbixAPIHost();
						JSONObject hostgroup = hostgroups.getJSONObject(j);
						String name = hostgroup.getString("name");
						if (name.startsWith("[")) {
							logger.debug("************* Found ParentGroup hostgroup: " + name);
							ParentID = hostgroup.getString("groupid").toString();
							logger.debug("************* Found ParentGroup hostgroup value: " + ParentID);
							break hostgroupsloop;
						}
						// JSONArray hostgroups = host.getJSONArray("groups");

						logger.debug("******** Received JSON Hostgroup: " + hostgroup.toString());
					}
			}
			

			for (int x = 0; x < hosttemplates.size(); x++) {
				// logger.debug(f.toString());
				// ZabbixAPIHost host = new ZabbixAPIHost();
				JSONObject hostgtemplate = hosttemplates.getJSONObject(x);
				// JSONArray hostgroups = host.getJSONArray("groups");

				logger.debug("******** Received JSON Hosttemplate: " + hostgtemplate.toString());
			}

			for (int y = 0; y < hostitems.size(); y++) {
				// logger.debug(f.toString());
				// ZabbixAPIHost host = new ZabbixAPIHost();
				JSONObject hostitem = hostitems.getJSONObject(y);
				// JSONArray hostgroups = host.getJSONArray("groups");

				//logger.debug("******** Received JSON hostitem: " + hostitem.toString());
			}
			
			hostmacrosloop:
			for (int z = 0; z < hostmacros.size(); z++) {
				// logger.debug(f.toString());
				// ZabbixAPIHost host = new ZabbixAPIHost();
				JSONObject hostmacro = hostmacros.getJSONObject(z);
				String name = hostmacro.getString("macro");
				if (name.equals("{$TYPE}")) {
					logger.debug("************* Found DeviceType hostmacro: " + name);
					device_type = hostmacro.getString("value");
					logger.debug("************* Found DeviceType hostmacro value: " + device_type);
					break hostmacrosloop;
				}
				// JSONArray hostgroups = host.getJSONArray("groups");

				logger.debug("******** Received JSON hostmacro: " + hostmacro.toString());
			}

			Device gendevice = new Device();
			gendevice = genHostObj(host, device_type, newhostname, ParentID);
			deviceList.add(gendevice);

			// fckey = host.getKey();

			// logger.info("Try to get fcSwitches for FCfabric " + fckey);
			// List<Device> fcSwitches = processSearchFcswitchesByFckey(fckey);
			// logger.info("Finded fcSwitches: "+ fcSwitches.size() + " for
			// FCfabric " + fckey);
			// fcSwitches = processEnrichPhysicalSwitches(fcSwitches,fckey);

			// listFinal.addAll(fcSwitches);

		}
		
		/*
		 * 
		 * if (hosts == null){ throw new RuntimeException(
		 * "Failed get JSON response result for all Hosts."); }
		 * 
		 * Gson gson = new
		 * GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		 * 
		 * logger.info("Received " + hosts.size() + " Total Hosts." );
		 * 
		 * List<Device> deviceList = new ArrayList<Device>(); List<Device>
		 * listFinal = new ArrayList<Device>();
		 * 
		 * //String fckey;
		 * 
		 * //ZabbixAPIHost host;
		 * 
		 * for (JsonElement f : hosts) { logger.debug(f.toString());
		 * ZabbixAPIHost host = new ZabbixAPIHost(); host = gson.fromJson(f,
		 * ZabbixAPIHost.class);
		 * 
		 * logger.info("**** Received JSON Host: " + host.toString() );
		 * 
		 * //Device gendevice = new Device(); //gendevice = genHostObj( host,
		 * "host" ); //deviceList.add(gendevice);
		 * 
		 * //fckey = host.getKey();
		 * 
		 * //logger.info("Try to get fcSwitches for FCfabric " + fckey);
		 * //List<Device> fcSwitches = processSearchFcswitchesByFckey(fckey);
		 * //logger.info("Finded fcSwitches: "+ fcSwitches.size() +
		 * " for FCfabric " + fckey); //fcSwitches =
		 * processEnrichPhysicalSwitches(fcSwitches,fckey);
		 * 
		 * //listFinal.addAll(fcSwitches);
		 * 
		 * }
		 * 
		 * logger.info("Finded Hosts: "+ deviceList.size());
		 */
		listFinal.addAll(deviceList);
		return listFinal;
	}

	private String checkEventHostAliases(String hostname) {
		// TODO Auto-generated method stub
		// Example: KRL-PHOBOSAU--MSSQL
		//String[] hostreturn = new String[] { "", "" } ;
		Pattern p = Pattern.compile("(.*)--(.*)");
		Matcher matcher = p.matcher(hostname.toUpperCase());
		String hostnamebegin = hostname;
		String hostnameend = "";
		//String output = "";
		if (matcher.matches()){
			hostnameend = matcher.group(2).toString().toUpperCase();
			hostnamebegin = matcher.group(1).toString().toUpperCase();
		}
	
		//logger.info("New Zabbix Host ParentID: " + hostreturn[0]);
		logger.info("New Zabbix Host Name: " + hostnamebegin);
		
		return hostnamebegin;
	}

	private void genErrorMessage(String message) {
		// TODO Auto-generated method stub
		long timestamp = System.currentTimeMillis();
		timestamp = timestamp / 1000;
		String textError = "Возникла ошибка при работе адаптера: ";
		Event genevent = new Event();
		genevent.setMessage(textError + message);
		genevent.setEventCategory("ADAPTER");
		genevent.setSeverity(PersistentEventSeverity.CRITICAL.name());
		genevent.setTimestamp(timestamp);
		genevent.setEventsource(String.format("%s", endpoint.getConfiguration().getSource()));
		genevent.setStatus("OPEN");
		genevent.setHost("adapter");

		logger.info(" **** Create Exchange for Error Message container");
		Exchange exchange = getEndpoint().createExchange();
		exchange.getIn().setBody(genevent, Device.class);

		exchange.getIn().setHeader("EventIdAndStatus", "Error_" + timestamp);
		exchange.getIn().setHeader("Timestamp", timestamp);
		exchange.getIn().setHeader("queueName", "Events");
		exchange.getIn().setHeader("Type", "Error");

		try {
			getProcessor().process(exchange);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void genHeartbeatMessage(Exchange exchange) {
		// TODO Auto-generated method stub
		long timestamp = System.currentTimeMillis();
		timestamp = timestamp / 1000;
		// String textError = "Возникла ошибка при работе адаптера: ";
		Event genevent = new Event();
		genevent.setMessage("Сигнал HEARTBEAT от адаптера");
		genevent.setEventCategory("ADAPTER");
		genevent.setObject("HEARTBEAT");
		genevent.setSeverity(PersistentEventSeverity.OK.name());
		genevent.setTimestamp(timestamp);
		genevent.setEventsource(String.format("%s", endpoint.getConfiguration().getAdaptername()));

		logger.info(" **** Create Exchange for Heartbeat Message container");
		// Exchange exchange = getEndpoint().createExchange();
		exchange.getIn().setBody(genevent, Event.class);

		exchange.getIn().setHeader("Timestamp", timestamp);
		exchange.getIn().setHeader("queueName", "Heartbeats");
		exchange.getIn().setHeader("Type", "Heartbeats");
		exchange.getIn().setHeader("Source", endpoint.getConfiguration().getAdaptername());

		try {
			// Processor processor = getProcessor();
			// .process(exchange);
			// processor.process(exchange);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
	}

	private Device genHostObj(JSONObject host, String device_type, String newhostname, String parentID) {
		Device gendevice = null;
		gendevice = new Device();
		
		if (newhostname.equals("")) {
			gendevice.setName(host.getString("host"));
		}
		else {
			gendevice.setName(newhostname);
		}
		
		gendevice.setId(host.getString("hostid"));
		gendevice.setDeviceType(device_type);
		gendevice.setParentID(parentID);

		// gendevice.setDeviceState(host.getStatus());
		// gendevice.setDeviceState(host.getOperationalStatus());

		gendevice.setSource(String.format("%s", endpoint.getConfiguration().getSource()));
		
		logger.debug("Received device_type: " + device_type);
		logger.debug(gendevice.toString());

		return gendevice;

	}
	
	private Device genHostgroupObj(JSONObject host, String device_type, String newhostname) {
		Device gendevice = null;
		gendevice = new Device();
		
		if (newhostname.equals("")) {
			gendevice.setName(host.getString("name"));
		}
		else {
			gendevice.setName(newhostname);
		}
		
		gendevice.setId(host.getString("groupid"));
		gendevice.setDeviceType(device_type);
		//gendevice.setParentID(parentID);

		// gendevice.setDeviceState(host.getStatus());
		// gendevice.setDeviceState(host.getOperationalStatus());

		gendevice.setSource(String.format("%s", endpoint.getConfiguration().getSource()));
		
		logger.debug("Received device_type: " + device_type);
		logger.debug(gendevice.toString());

		return gendevice;

	}

	private CloseableHttpClient HTTPinit(RequestConfig globalConfig, CookieStore cookieStore) {

		SSLContext sslContext = null;
		// HttpClient client = HttpClientBuilder.create().build();
		HttpClientBuilder cb = HttpClientBuilder.create();
		SSLContextBuilder sslcb = new SSLContextBuilder();
		try {
			sslcb.loadTrustMaterial(KeyStore.getInstance(KeyStore.getDefaultType()), new TrustSelfSignedStrategy());
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			cb.setSslcontext(sslcb.build());
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			sslContext = sslcb.build();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		@SuppressWarnings("deprecation")
		// RequestConfig globalConfig =
		// RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
		// CookieStore cookieStore = new BasicCookieStore();
		// HttpClientContext context = HttpClientContext.create();
		// context.setCookieStore(cookieStore);
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
				SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		CloseableHttpClient httpclient = HttpClients.custom().setUserAgent("Mozilla/5.0")
				.setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).setSSLSocketFactory(sslsf)
				.build();

		// logger.debug("*** Received cookies: " +
		// context.getCookieStore().getCookies());

		return httpclient;
	}

	/*
	 * private int processSearchFeeds() throws Exception {
	 * 
	 * String query = endpoint.getConfiguration().getQuery(); String uri =
	 * String.format("login?query=%s", query); JsonObject json =
	 * performGetRequest(uri);
	 * 
	 * //JsonArray feeds = (JsonArray) json.get("results"); JsonArray feeds =
	 * (JsonArray) json.get("ServerName"); List<Feed2> feedList = new
	 * ArrayList<Feed2>(); Gson gson = new
	 * GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create(); for
	 * (JsonElement f : feeds) { //logger.debug(gson.toJson(i)); Feed2 feed =
	 * gson.fromJson(f, Feed2.class); feedList.add(feed); }
	 * 
	 * Exchange exchange = getEndpoint().createExchange();
	 * exchange.getIn().setBody(feedList, ArrayList.class);
	 * getProcessor().process(exchange);
	 * 
	 * return 1; }
	 */

}