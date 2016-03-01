package ru.atc.camel.zabbix.api.events;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.hengyunabc.zabbix.api.DefaultZabbixApi;
import io.github.hengyunabc.zabbix.api.Request;
import io.github.hengyunabc.zabbix.api.RequestBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ru.at_consulting.itsm.device.Device;
import ru.at_consulting.itsm.event.Event;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import java.security.KeyManagementException;
//import java.security.KeyStore;
//import java.security.KeyStoreException;
//import javax.net.ssl.SSLContext;
//import org.apache.commons.codec.binary.Hex;
//import org.apache.commons.lang.CharSet;
//import org.apache.http.HttpVersion;
//import org.apache.http.client.CookieStore;
//import org.apache.http.client.config.RequestConfig;
//import org.apache.http.client.methods.HttpPut;
//import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.DefaultHttpClient;
//import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.params.CoreProtocolPNames;
//import org.apache.http.ssl.SSLContextBuilder;
//import com.google.gson.JsonObject;
//import ru.at_consulting.itsm.adapter.EventToObjectBean.PersistentEventSeverity;
//import scala.xml.dtd.ParameterEntityDecl;


public class ZabbixAPIConsumer extends ScheduledPollConsumer {

	private static Logger logger = LoggerFactory.getLogger(Main.class);

	private static ZabbixAPIEndpoint endpoint;

	public ZabbixAPIConsumer(ZabbixAPIEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		ZabbixAPIConsumer.endpoint = endpoint;
		// this.afterPoll();
		this.setTimeUnit(TimeUnit.MINUTES);
		this.setInitialDelay(0);
		this.setDelay(endpoint.getConfiguration().getDelay());
	}

	public static void genHeartbeatMessage(Exchange exchange) {

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


	}

	private static String hashString(String message, String algorithm)
			throws Exception {

		try {
			MessageDigest digest = MessageDigest.getInstance(algorithm);
			byte[] hashedBytes = digest.digest(message.getBytes("UTF-8"));

			return convertByteArrayToHexString(hashedBytes);
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
			throw new RuntimeException(
					"Could not generate hash from String", ex);
		}
	}

	private static String convertByteArrayToHexString(byte[] arrayBytes) {
		StringBuffer stringBuffer = new StringBuffer();
		for (byte arrayByte : arrayBytes) {
			stringBuffer.append(Integer.toString((arrayByte & 0xff) + 0x100, 16)
					.substring(1));
		}
		return stringBuffer.toString();
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

	private int processSearchEvents() throws Exception {

		// Long timestamp;

		//List<Device> hostsList = new ArrayList<Device>();
		List<Event> actualevents;
		List<Event> listFinal = new ArrayList<>();

		//List<Event> eventList = new ArrayList<>();

		String eventsuri = endpoint.getConfiguration().getZabbixapiurl();
		String uri = String.format("%s", eventsuri);

		System.out.println("***************** URL: " + uri);

		logger.info("Try to get Events.");
		// logger.info("Get events URL: " + uri);

		// JsonObject json = null;

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
			//System.err.println("login:" + login);
			if (!login) {

				throw new RuntimeException("Failed to login to Zabbix API.");
			}


			logger.debug("Last Event ID: " + lasteventid);
			// lastid = 0 (first execution)

			//List<Event> actualevents = null;

			if (lasteventid == 0) {
				// Get all Actual Triggers (eventid) from Zabbix
				logger.info("Try to get actual opened triggers");
				String[] openTriggerEventIDs = getOpenTriggers(zabbixApi);

				// get last eventid from Zabbix
				String lasteventidstr = getLastEventId(zabbixApi);
				if (lasteventidstr != null){
					//lasteventid = Integer.parseInt(lasteventidstr);
					endpoint.getConfiguration().setLasteventid(lasteventidstr);
				}

				// Get all Actual Events for Triggers from Zabbix
				logger.info("Try to get events for them");
				actualevents = getEventsByID(zabbixApi, openTriggerEventIDs, false);
			}
			else {
				// Get all Actual Events for Triggers from Zabbix
				logger.info("Try to get new Events from " + lasteventid);
				lasteventid++;
				String lasteventidstr = lasteventid + "";
				String[] eventidsarr = { "" };
				eventidsarr[0] = lasteventidstr;

				// get last eventid from Zabbix
				String lasteventidstrnew = getLastEventId(zabbixApi);
				if (lasteventidstrnew != null){
					//lasteventid = Integer.parseInt(lasteventidstrnew);
					endpoint.getConfiguration().setLasteventid(lasteventidstrnew);
				}

				actualevents = getEventsByID(zabbixApi, eventidsarr, true);
			}

			// Get all Hosts from Zabbix
			// hostsList = getAllHosts(zabbixApi);
			//if (hostsList != null)
			//	listFinal.addAll(hostsList);

			// Get all HostGroups from Zabbix
			// hostgroupsList = getAllHostGroups(zabbixApi);
			if (actualevents != null)
				listFinal.addAll(actualevents);

			for (Event aListFinal : listFinal) {
				logger.info("Create Exchange container");

				String key = aListFinal.getExternalid() + "_" +
						aListFinal.getStatus();

				Exchange exchange = getEndpoint().createExchange();
				exchange.getIn().setBody(aListFinal, Event.class);
				exchange.getIn().setHeader("EventUniqId", key);
				//exchange.getIn().setHeader("EventId", listFinal.get(i).getUuid());
				//exchange.getIn().setHeader("DeviceType", listFinal.get(i).getDeviceType());
				exchange.getIn().setHeader("queueName", "Events");

				try {
					getProcessor().process(exchange);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			logger.info("Sended Events: " + listFinal.size());

		} catch (NullPointerException e) {
			e.printStackTrace();
			logger.error(String.format("Error while get Events from API: %s ", e));
			genErrorMessage(e.getMessage() + " " + e.toString());
			//httpClient.close();
			return 0;
		} catch (Throwable e) {
			e.printStackTrace();
			logger.error(String.format("Error while get Events from API: %s ", e));
			genErrorMessage(e.getMessage() + " " + e.toString());
			//httpClient.close();
            if (zabbixApi != null) {
                zabbixApi.destory();
            }
            return 0;
		} finally {
			logger.debug(String.format(" **** Close zabbixApi Client: %s",
					zabbixApi != null ? zabbixApi.toString() : null));
			// httpClient.close();

            if (zabbixApi != null) {
                zabbixApi.destory();
            }
            // dataSource.close();
			// return 0;
		}

		String hash = null;
		try {
			hash = hashString(String.format("%s:%s", "1232131$!$ававяЙ", "bfbvl-lvlbv9595"), "SHA-1");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    logger.debug("*** Generated Hash: " + hash );

		return 1;
	}

	private String getLastEventId(DefaultZabbixApi zabbixApi) {

		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			//JSONObject search = new JSONObject();
			// JSONObject output = new JSONObject();

			//search.put("name", new String[] { prefix + "*" });
			//filter.put("eventsource", 0);

			getRequest = RequestBuilder.newBuilder().method("event.get")
					//.paramEntry("search", search)
					//.paramEntry("filter", filter)
					.paramEntry("output", "extend").paramEntry("only_true", "1")
					.paramEntry("sortfield", new String[] { "eventid" })
					.paramEntry("sortorder", new String[] { "DESC" }).paramEntry("limit", "1")
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get System LAst Event ID");
		}

		JSONArray actions;
		try {
			getResponse = zabbixApi.call(getRequest);
			// System.err.println(getResponse);

			actions = getResponse.getJSONArray("result");
			//System.err.println(actions);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
		}

		String lasteventid = actions.getJSONObject(0).getString("eventid");

		return lasteventid;
	}

	private List<Event> getEventsByID(DefaultZabbixApi zabbixApi, String[] openTriggerEventIDs, Boolean searchfornew)
                throws Exception {

		RequestBuilder getRequest_b;
		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			JSONObject filter = new JSONObject();

			filter.put("eventids", openTriggerEventIDs);
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest_b = RequestBuilder.newBuilder().method("event.get")
					.paramEntry("output", "extend").paramEntry("only_true", "1")
					.paramEntry("selectHosts", new String[] { "hostid", "host", "name" })
					.paramEntry("select_alerts", new String[] { "alertid", "actionid", "eventid", "message" })
					.paramEntry("selectRelatedObject",
							new String[] { "triggerid", "description", "status", "value", "priority" })
					.paramEntry("sortfield", new String[] { "clock", "eventid" })
					.paramEntry("sortorder", new String[]{"ASC"}).paramEntry("limit", "500");
					//.build();

			if (searchfornew){
				getRequest_b = getRequest_b.paramEntry("eventid_from", openTriggerEventIDs[0]);
			}
			else {
				getRequest_b = getRequest_b.paramEntry("eventids", openTriggerEventIDs);
			}

			getRequest = getRequest_b.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get Open Events by ID");
		}

		JSONArray openEvents;
		try {
			getResponse = zabbixApi.call(getRequest);
			// System.err.println(getResponse);

			openEvents = getResponse.getJSONArray("result");
			// System.err.println(openEvents);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for Open Events by ID");
		}

		List<Event> eventList = new ArrayList<>();

		// List<Device> listFinal = new ArrayList<Device>();
		// String device_type = "host";
        logger.info("***** Finded Zabbix Events: " + openEvents.size());

		String[] systemActionIDs = getSystemActionsID(zabbixApi);

		// get all host from Zabbix for further usage for parent association
		//JSONArray hosts = getAllHosts(zabbixApi);

		for (int i = 0; i < openEvents.size(); i++) {

            // newhostname = "";
			// logger.debug(f.toString());
			// ZabbixAPIHost host = new ZabbixAPIHost();
			JSONObject event = openEvents.getJSONObject(i);
			String hostname = event.getJSONArray("hosts").getJSONObject(0).getString("host");
			String hostid = event.getJSONArray("hosts").getJSONObject(0).getString("hostid");
			String hostdescription = event.getJSONArray("hosts").getJSONObject(0).getString("name");
			String objectid = event.getString("objectid");
			String eventid = event.getString("eventid");
			String status = event.getString("value");
			String timestamp = event.getString("clock");

			Event genevent = new Event();

			logger.debug("*** Received JSON Event: " + event.toString());

			JSONArray alerts = event.getJSONArray("alerts");
			// JSONArray hosttemplates = event.getJSONArray("parentTemplates");
			// JSONArray hostitems = event.getJSONArray("items");
			String actionid;
			String message;

			//try to find alerts in event
			loopalerts:
			for (int x = 0; x < alerts.size(); x++) {
				// logger.debug(f.toString());
				// ZabbixAPIHost host = new ZabbixAPIHost();
				JSONObject alert = alerts.getJSONObject(x);
				actionid = alert.getString("actionid");
				HashMap<String, Object> alertresult;

				// if pattern of Zabbix action name is appropriate
				if (Arrays.asList(systemActionIDs).contains(actionid)){
					message = alert.getString("message");
					logger.debug("******** Received JSON Alert message: " + message);

					Pattern p = Pattern.compile(":echo '(.*)' > '.*",Pattern.DOTALL);
					Matcher matcher = p.matcher(message);

					// if message's pattern slot in alert is appropriate
					if (matcher.matches()) {
						message = matcher.group(1);
						logger.debug("******** Received JSON Alert XML message: " + message);
						logger.debug("******** Trying to parse XML...  ");

						// try to parse XML of alert's message
						alertresult = parseXMLtoObject(message);

						// Generate Event
						if (alertresult != null){
							logger.debug("**** Received well-formatted message.  ");
							logger.debug("**** Trying to generate event for it...  ");
							genevent = genEventObj(zabbixApi, hostname, hostid, hostdescription, eventid,
									status, timestamp, objectid, alertresult);

						}
						else {
							logger.debug("**** Received bad-formatted message.  ");
							genevent = null;
						}


					}

					break loopalerts;


				}
				// if pattern of Zabbix action name is not appropriate
				else {

				}


			}

			// Add generated event to list
			if (genevent != null ){
				eventList.add(genevent);
			}

			logger.debug("**** Received Total well-formatted Events: " + eventList.size());

		}

		return eventList;
	}

	private Event genEventObj(DefaultZabbixApi zabbixApi, String hostname, String hostid, String hostdescription,
			String eventid, String status, String timestamp, String objectid, HashMap<String, Object> alertresult) {

		Event event = new Event();
		String triggername;
		String value;
		//String hostgroup = "";
		String severity;
		String template;
		//String itemid = "";
		String itemname;

		try {
			triggername = alertresult.get("triggername").toString();
			value = alertresult.get("value").toString();
			//hostgroup = alertresult.get("hostgroup").toString();
			severity = alertresult.get("severity").toString();
			template = alertresult.get("template").toString();
			//itemid = alertresult.get("itemid").toString();
			//itemname = alertresult.get("itemname").toString().toUpperCase();
			itemname = alertresult.get("itemname").toString();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		//
		//logger.debug("Finded Zabbix hostgroup: " + hostgroup);

		String newhostname;
		String ParentID = hostid;
		String newobject = itemname;
		newhostname = hostname;

		Pattern p = Pattern.compile("(.*)--(.*)");
		Matcher matcher = p.matcher(hostname);
		// Example: KRL-PHOBOSAU--MSSQL
		// if pattern of hostname is appropriate as alias-name

		if (matcher.matches()) {

			logger.debug("Finded Zabbix Host with Aliases: " + hostname);
			// check aliases of zabbix host and get new name and Parent
			//String[] checkreturn = checkEventHostAliases(hosts, hostname);
			//ParentID = checkreturn[0];

			//hostreturn[0] = ParentID;
			//hostreturn[1] = hostnameend;
			//hostreturn[2] = hostnamebegin;

			newhostname = matcher.group(1);
			//newobject = checkreturn[1];
		}
		// else
		else {
			logger.debug("Use hostid (as ciid) of hostname: " + hostname);
			// Use hostid (as ciid) of hostname
			ParentID = hostid;
			//ParentID = checkreturn[0];
		}


		event.setExternalid(eventid);
		event.setStatus(setRightStatus(status));
		event.setMessage(String.format("%s: %s", triggername, value));

		Long newtimstamp = (long) Integer.parseInt(timestamp);
		event.setTimestamp(newtimstamp);

		logger.debug("*** Received Zabbix Item : " + itemname);

		// zabbix_item_ke_pattern=\\[(.*)\\](.*)
		String pattern = endpoint.getConfiguration().getItemCiPattern();
		logger.debug("*** Zabbix Item CI Pattern: " + pattern);

		// zabbix_item_ci_parent_pattern=(.*)::(.*)
		String zabbix_item_ci_parent_pattern = endpoint.getConfiguration().getItemCiParentPattern();
		Pattern ciWithParentPattern = Pattern.compile(zabbix_item_ci_parent_pattern);

		// Example item as CI :
		// [test CI item] bla-bla
		p = Pattern.compile(pattern);
		matcher = p.matcher(itemname);

		// if Item has CI pattern
		if (matcher.matches()) {

			logger.debug("*** Finded Zabbix Item with Pattern as CI: " + itemname);
			// save as ne CI name
			itemname = matcher.group(1).toUpperCase();
			//event.setHost(itemname);
		    newobject = itemname;

		    // CI 2 (CIITEM)::CI 3 (CIITEM2)
 			Matcher matcher2 = ciWithParentPattern.matcher(itemname);
 			if (matcher2.matches()) {
 				logger.debug("*** Finded Zabbix Item with Pattern with Parent: " + itemname);
 				itemname = matcher2.group(2).trim().toUpperCase();

 				//ciname = newitemname;

 				//parentitem = matcher2.group(1).toString().trim().toUpperCase();
 				logger.debug("*** itemname: " + itemname);
 				//logger.debug("*** parentitem: " + parentitem);

 			}

 			// get SHA-1 hash for hostname-item block for saving as ciid
		    // Example:
		    // KRL-PHOBOSAU--PHOBOS:TEST CI ITEM (TYPE)
		    logger.debug(String.format("*** Trying to generate hash for Item with Pattern: %s:%s",
                        hostname, itemname));
		    String hash = null;
			try {
				hash = hashString(String.format("%s:%s", hostname, itemname), "SHA-1");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    logger.debug("*** Generated Hash: " + hash );
		    ParentID = hash;
			//event.setParametr(itemname);
		}
		// if Item has no CI pattern
		else {


		}

		// Example Template name :
		// Template --SNMP Traps Nortel--
		p = Pattern.compile(endpoint.getConfiguration().getZabbixtemplatepattern());
		matcher = p.matcher(template);

		// if Template name has Template pattern
		if (matcher.matches()) {

			logger.debug("*** Zabbix Template Pattern: " + endpoint.getConfiguration().getZabbixtemplatepattern());
			logger.debug("*** Finded Zabbix Template with Pattern: " + template);
			// save as ne CI name
			event.setEventCategory(matcher.group(1));

		}
		// if Item has no CI pattern
		else {


		}

		event.setParametr(objectid);
		event.setOrigin(hostname);
		event.setCi(String.format("%s:%s", endpoint.getConfiguration().getSource(),ParentID));
		event.setHost(newhostname);
		event.setObject(newobject);
		event.setEventsource(String.format("%s", endpoint.getConfiguration().getSource()));
		event.setSeverity(setRightSeverity(severity.toUpperCase()));

		logger.debug("**** Generated event: " + event.toString());

		return event;
	}

	private HashMap<String, Object> parseXMLtoObject(String xmlmessage) {

		DocumentBuilderFactory domFactory = DocumentBuilderFactory
	                .newInstance();
	        domFactory.setNamespaceAware(true);
	        DocumentBuilder builder;
			try {
				builder = domFactory.newDocumentBuilder();
			} catch (ParserConfigurationException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return null;
			}
	        Document doc;
			try {
				doc = builder.parse(new InputSource(new StringReader(xmlmessage)));
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
	        XPath xpath = XPathFactory.newInstance().newXPath();
	        xpath.setNamespaceContext(new NamespaceContext() {

	            @Override
	            public Iterator<String> getPrefixes(String arg0) {
	                return null;
	            }

	            @Override
	            public String getPrefix(String arg0) {
	                return null;
	            }

	            @Override
	            public String getNamespaceURI(String arg0) {
	                if("ns".equals(arg0)) {
	                    return "http://skuf.gosuslugi.ru/mon/";
	                }
	                return null;
	            }
	        });

		try {
	        	XPathExpression expr;
	        	Object result;
	        	//NodeList nodes;

			HashMap<String,Object> alertreturn= new HashMap<>(6);

			// <ns:severity>Information</ns:severity>
	            expr = xpath.compile("/ns:zabbixEvent/ns:severity");
	            result = expr.evaluate(doc, XPathConstants.NODESET);
	            String severity = ((NodeList) result).item(0).getTextContent();
	            logger.debug("*** Received XML severity : " + severity);
	            alertreturn.put("severity",severity);

			// <ns:value><![CDATA[16:53:06 2015/12/25 TYPE=NORTEL::CODE=AUD0000::SEVERITY=Info::DESCRIPTION=One pass made by audit. No errors detected. System software OK..Action: No ]]></ns:value>
	            expr = xpath.compile("/ns:zabbixEvent/ns:value");
	            result = expr.evaluate(doc, XPathConstants.NODESET);
	            String value = ((NodeList) result).item(0).getTextContent();
	            logger.debug("*** Received XML value : " + value);
	            alertreturn.put("value",value);

			// <ns:hostgroup>АТС </ns:hostgroup>
	            expr = xpath.compile("/ns:zabbixEvent/ns:hostgroup");
	            result = expr.evaluate(doc, XPathConstants.NODESET);
	            String hostgroup = ((NodeList) result).item(0).getTextContent();
	            logger.debug("*** Received XML hostgroup : " + hostgroup);
	            alertreturn.put("hostgroup",hostgroup);

			// <ns:template>SNMP Traps Nortel </ns:template>
	            expr = xpath.compile("/ns:zabbixEvent/ns:template");
	            result = expr.evaluate(doc, XPathConstants.NODESET);
	            String template = ((NodeList) result).item(0).getTextContent();
	            logger.debug("*** Received XML template : " + template);
	            alertreturn.put("template",template);

			// <ns:triggername><![CDATA[SNMP Info trap]]></ns:triggername>
	            expr = xpath.compile("/ns:zabbixEvent/ns:triggername");
	            result = expr.evaluate(doc, XPathConstants.NODESET);
	            String triggername = ((NodeList) result).item(0).getTextContent();
	            logger.debug("*** Received XML triggername : " + triggername);
	            alertreturn.put("triggername",triggername);

			// <ns:itemid>23876</ns:itemid>
	            try {
					expr = xpath.compile("/ns:zabbixEvent/ns:itemid");
					result = expr.evaluate(doc, XPathConstants.NODESET);
					String itemid = ((NodeList) result).item(0).getTextContent();
					logger.debug("*** Received XML itemid : " + itemid);
					alertreturn.put("itemid",itemid);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}

			// <ns:itemname><![CDATA[SNMP Trap SEVERITY=Info]]>
	            try {
					expr = xpath.compile("/ns:zabbixEvent/ns:itemname");
					result = expr.evaluate(doc, XPathConstants.NODESET);
					String itemname = ((NodeList) result).item(0).getTextContent();
					logger.debug("*** Received XML itemname : " + itemname);
					alertreturn.put("itemname",itemname);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}


			return alertreturn;

			// System.out.println(nodes.item(0).getNodeValue());
	        } catch (Exception E) {
	            //System.out.println(E);
	            E.printStackTrace();
	            return null;
	        }

	}

	private String[] getSystemActionsID(DefaultZabbixApi zabbixApi){

		String prefix = endpoint.getConfiguration().getZabbixactionprefix();
		//prefix = prefix . "*";


		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			JSONObject filter = new JSONObject();
			JSONObject search = new JSONObject();
			// JSONObject output = new JSONObject();

			search.put("name", new String[]{prefix + "*"});

			filter.put("eventsource", 0);
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("action.get")
					.paramEntry("search", search)
					.paramEntry("filter", filter)
					.paramEntry("output", new String[]{"actionid", "name"})
					.paramEntry("searchWildcardsEnabled", 1)
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get System Actions");
		}


		JSONArray actions;
		try {
			getResponse = zabbixApi.call(getRequest);
			// System.err.println(getResponse);

			actions = getResponse.getJSONArray("result");
			//System.err.println(actions);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
		}

		String[] actionids = new String[]{};

		for (int i = 0; i < actions.size(); i++) {
			// device_type = "group";

			JSONObject action = actions.getJSONObject(i);
			// "triggerid": "13779"
			//String triggerid = action.getString("triggerid");
			// Example: KRL-PHOBOSAU--MSSQL

			// "lastEvent": {
			// "eventid": "327723"
			String actionid = action.getString("actionid");

			actionids = (String[]) ArrayUtils.add(actionids, actionid);


			logger.debug("*** Received JSON System ActionID: " + actionid);


		}

		//logger.debug("*** Received JSON All Actual EventID: " + actionids.length);

		return actionids;


	}

	private String[] getOpenTriggers(DefaultZabbixApi zabbixApi) {

		Request getRequest;
		JSONObject getResponse;
		// JsonObject params = new JsonObject();
		try {
			// String host1 = "172.20.14.68";
			// String host2 = "TGC1-ASODU2";
			//JSONObject filter = new JSONObject();
			// JSONObject output = new JSONObject();

			//filter.put("filter", new int[] { 0, 1 });
			// output.put("output", new String[] { "hostid", "name", "host" });

			getRequest = RequestBuilder.newBuilder().method("trigger.get")
					// .paramEntry("search", search)
					.paramEntry("output", "extend")
					.paramEntry("only_true", "1")
					.paramEntry("selectLastEvent", "extend")
					.build();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Failed create JSON request for get all Open Triggers.");
		}

		JSONArray openTriggers;
		try {
			getResponse = zabbixApi.call(getRequest);
			System.err.println(getResponse);

			openTriggers = getResponse.getJSONArray("result");
			System.err.println(openTriggers);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
		}

		String[] eventids = new String[]{};

		for (int i = 0; i < openTriggers.size(); i++) {
			// device_type = "group";

			JSONObject trigger = openTriggers.getJSONObject(i);
			// "triggerid": "13779"
			String triggerid = trigger.getString("triggerid");
			// Example: KRL-PHOBOSAU--MSSQL

			// "lastEvent": {
			// "eventid": "327723"
			try {
				String eventid = trigger.getJSONObject("lastEvent").getString("eventid");
				String status = trigger.getJSONObject("lastEvent").getString("value");
				eventids = (String[]) ArrayUtils.add(eventids, eventid);

				logger.debug("*** Received JSON EventID: " + eventid);
				logger.debug("*** Received JSON Event Value: " + status);
			} catch (Exception e) {
				logger.debug("*** No event for trigger: " + triggerid);
			}


		}

		logger.debug("*** Received JSON All Actual EventID: " + eventids.length);

		return eventids;
	}

	/*private JSONArray getAllHosts(DefaultZabbixApi zabbixApi) {

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
					//.paramEntry("selectItems", new String[] { "itemid", "name", "key_", "description" })
					.build();

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
			logger.debug("*** Request: " + getRequest);
			logger.debug("*** getResponse: " + getResponse);

			hosts = getResponse.getJSONArray("result");
			System.err.println(hosts);


		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed get JSON response result for all Hosts.");
		}

		// listFinal.addAll(deviceList);
		return hosts;
	}
*/
	/*
	private String[] checkEventHostAliases(JSONArray hosts, String hostname) {
		// Example: KRL-PHOBOSAU--MSSQL
		// String[] hostreturn = new String[] { "", "" } ;
		Pattern p = Pattern.compile("(.*)--(.*)");
		Matcher matcher = p.matcher(hostname.toUpperCase());
		String hostnamebegin = hostname;
		String hostnameend = "";
		// String output = "";
		if (matcher.matches()) {
			hostnameend = matcher.group(2).toUpperCase();
			hostnamebegin = matcher.group(1).toUpperCase();
		}

		String ParentID = "";
		for (int j = 0; j < hosts.size(); j++) {
			// logger.debug(f.toString());
			// ZabbixAPIHost host = new ZabbixAPIHost();
			JSONObject host_a = hosts.getJSONObject(j);
			String name = host_a.getString("host");
			if (name.equalsIgnoreCase(hostnamebegin)) {
				ParentID =  host_a.getString("hostid");

			}
		}

		String[] hostreturn = new String[] { "", "", "" } ;

		hostreturn[0] = ParentID;
		hostreturn[1] = hostnameend;
		hostreturn[2] = hostnamebegin;

		logger.info("*** New Zabbix Host ParentID: " + hostreturn[0]);
		logger.info("*** New Zabbix Host Name: " + hostreturn[2]);
		logger.info("*** New Zabbix Host Object: " + hostreturn[1]);

		return hostreturn;
	}
*/
	private void genErrorMessage(String message) {
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

	private String setRightSeverity(String severity)
	{
		String newseverity;
		/*
		 *
		Severity
 “NORMAL”
 “WARNING”
 “MINOR”
 “MAJOR”
 “CRITICAL”
		 */


		switch (severity) {
        	case "DISASTER":  newseverity = PersistentEventSeverity.CRITICAL.name();break;
        	case "HIGH":  newseverity = PersistentEventSeverity.MAJOR.name();break;
        	case "AVERAGE":  newseverity = PersistentEventSeverity.MINOR.name();break;
        	case "WARNING":  newseverity = PersistentEventSeverity.WARNING.name();break;
        	case "INFORMATION":  newseverity = PersistentEventSeverity.INFO.name();break;

			default:  newseverity = PersistentEventSeverity.INFO.name();break;

		}
		logger.debug("***************** severity: " + severity);
		logger.debug("***************** newseverity: " + newseverity);
		return newseverity;
	}
	
	private String setRightStatus(String status)
	{
		String newstatus = "";
		/*
		 *
State of the related object.

Possible values for trigger events:
0 - OK;
1 - problem.
		 */


		switch (status) {
        	case "1":  newstatus = "OPEN";break;
        	case "0":  newstatus = "CLOSED";break;

			//default:  newseverity = PersistentEventSeverity.INFO.name();break;

		}
		logger.debug("***************** status: " + status);
		logger.debug("***************** newstatus: " + newstatus);
		return newstatus;
	}

	public enum PersistentEventSeverity {
		OK, INFO, WARNING, MINOR, MAJOR, CRITICAL;

		public static PersistentEventSeverity fromValue(String v) {
			return valueOf(v);
		}

		public String value() {
			return name();
		}
	}



}