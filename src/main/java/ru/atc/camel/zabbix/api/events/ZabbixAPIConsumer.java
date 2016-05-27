package ru.atc.camel.zabbix.api.events;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ru.at_consulting.itsm.event.Event;
import ru.atc.monitoring.zabbix.api.DefaultZabbixApi;
import ru.atc.monitoring.zabbix.api.Request;
import ru.atc.monitoring.zabbix.api.RequestBuilder;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.atc.zabbix.general.CiItems.checkHostAliases;
import static ru.atc.zabbix.general.CiItems.checkHostPattern;
import static ru.atc.zabbix.general.HashGenerator.hashString;

public class ZabbixAPIConsumer extends ScheduledPollConsumer {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int SOCKET_TIMEOUT = 120000;
    private static final int MAX_CONN_PER_ROUTE = 40;
    private static final int MAX_CONN_TOTAL = 40;
    private static final int CONNECTION_TIME_TO_LIVE = 120;

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static ZabbixAPIEndpoint endpoint;

    public ZabbixAPIConsumer(ZabbixAPIEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        ZabbixAPIConsumer.endpoint = endpoint;
        this.setTimeUnit(TimeUnit.MINUTES);
        this.setInitialDelay(0);
        this.setDelay(endpoint.getConfiguration().getDelay());
    }

    public static void genHeartbeatMessage(Exchange exchange) {

        long timestamp = System.currentTimeMillis();
        timestamp = timestamp / 1000;
        Event genevent = new Event();
        genevent.setMessage("Сигнал HEARTBEAT от адаптера");
        genevent.setEventCategory("ADAPTER");
        genevent.setObject("HEARTBEAT");
        genevent.setSeverity(PersistentEventSeverity.OK.name());
        genevent.setTimestamp(timestamp);
        genevent.setEventsource(String.format("%s", endpoint.getConfiguration().getAdaptername()));

        logger.info(" **** Create Exchange for Heartbeat Message container");

        exchange.getIn().setBody(genevent, Event.class);
        exchange.getIn().setHeader("Timestamp", timestamp);
        exchange.getIn().setHeader("queueName", "Heartbeats");
        exchange.getIn().setHeader("Type", "Heartbeats");
        exchange.getIn().setHeader("Source", endpoint.getConfiguration().getAdaptername());

    }

    @Override
    protected int poll() throws Exception {

        String operationPath = endpoint.getOperationPath();

        if ("events".equals(operationPath))
            return processSearchEvents();

        // only one operation implemented for now !
        throw new IllegalArgumentException("Incorrect operation: " + operationPath);
    }

    @Override
    public long beforePoll(long timeout) throws Exception {

        logger.info("*** Before Poll!!!");
        // only one operation implemented for now !

        // send HEARTBEAT
        genHeartbeatMessage(getEndpoint().createExchange());

        return timeout;
    }

    private int processSearchEvents() {

        List<Event> openEventsList;
        List<Event> allEventsList = new ArrayList<>();

        logger.info("Try to get Events.");

        DefaultZabbixApi zabbixApi = null;
        int lasteventid = Integer.parseInt(endpoint.getConfiguration().getLasteventid());

        try {

            // inid zabbix api object and login to zabbix server
            zabbixApi = initZabbixApi();

            logger.debug("Last Event ID: " + lasteventid);
            // lastid = 0 (first execution)

            openEventsList = getLastEventsFromApi(zabbixApi, lasteventid);

            // add events to main global array of events
            if (openEventsList != null)
                allEventsList.addAll(openEventsList);

            processEventsToExchange(allEventsList);

            logger.info("Processed Events: " + allEventsList.size());

        } catch (NullPointerException e) {
            logger.error(String.format("Error while get Events from API: %s ", e));
            genErrorMessage(e.getMessage() + " " + e.toString());
            return 0;
        } catch (Exception e) {
            logger.error(String.format("General Error while get Events from API: %s ", e));
            genErrorMessage(e.getMessage() + " " + e.toString());
            return 0;
        } finally {
            logger.debug(String.format(" **** Close zabbixApi Client: %s",
                    zabbixApi != null ? zabbixApi.toString() : null));

            if (zabbixApi != null) {
                zabbixApi.destory();
            }

        }

        return 1;
    }

    private List<Event> getLastEventsFromApi(DefaultZabbixApi zabbixApi, int lasteventid) {
        List<Event> openEventsList;
        if (lasteventid == 0) {
            // Get all Actual Triggers (eventid) from Zabbix
            logger.info("Try to get actual opened triggers");
            String[] openTriggerEventIDs = getOpenTriggers(zabbixApi);

            // get last eventid from Zabbix
            String lasteventidstr = getLastEventId(zabbixApi);
            if (lasteventidstr != null) {
                endpoint.getConfiguration().setLasteventid(lasteventidstr);
            }

            // Get all Actual Open Events for Triggers from Zabbix
            logger.info("Try to get events for them");
            openEventsList = getEventsFromLastId(zabbixApi, openTriggerEventIDs, false);
        } else {
            // Get all Actual Events for Triggers from Zabbix (lastid + 1)
            logger.info("Try to get new Events from " + lasteventid);
            String lasteventidstr = Integer.toString(lasteventid + 1);
            String[] eventidsarr = {""};
            eventidsarr[0] = lasteventidstr;

            // get last eventid from Zabbix and save it in config (memory)
            String lasteventidstrnew = getLastEventId(zabbixApi);
            if (lasteventidstrnew != null) {
                endpoint.getConfiguration().setLasteventid(lasteventidstrnew);
            }

            openEventsList = getEventsFromLastId(zabbixApi, eventidsarr, true);
        }
        return openEventsList;
    }

    private DefaultZabbixApi initZabbixApi() {
        String zabbixapiurl = endpoint.getConfiguration().getZabbixapiurl();
        String username = endpoint.getConfiguration().getUsername();
        String password = endpoint.getConfiguration().getPassword();

        HttpClient httpClient2 = HttpClients.custom()
                .setConnectionTimeToLive(CONNECTION_TIME_TO_LIVE, TimeUnit.SECONDS)
                .setMaxConnTotal(MAX_CONN_TOTAL).setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setSocketTimeout(SOCKET_TIMEOUT).setConnectTimeout(CONNECT_TIMEOUT).build())
                .setRetryHandler(new DefaultHttpRequestRetryHandler(5, true))
                .build();

        DefaultZabbixApi zabbixApi = new DefaultZabbixApi(zabbixapiurl, (CloseableHttpClient) httpClient2);
        zabbixApi.init();

        boolean login = zabbixApi.login(username, password);
        if (!login) {
            throw new RuntimeException("Failed to login to Zabbix API.");
        }
        return zabbixApi;
    }

    private void processEventsToExchange(List<Event> allEventsList) {
        for (Event aListFinal : allEventsList) {
            logger.info("Create Exchange container");

            String key = aListFinal.getExternalid() + "_" +
                    aListFinal.getStatus();

            Exchange exchange = getEndpoint().createExchange();
            exchange.getIn().setBody(aListFinal, Event.class);
            exchange.getIn().setHeader("EventUniqId", key);

            exchange.getIn().setHeader("queueName", "Events");

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                logger.error("Ошибка при передачи сообщения в очередь: ", e);
            }
        }
    }

    private String getLastEventId(DefaultZabbixApi zabbixApi) {

        Request getRequest;
        JSONObject getResponse;

        try {

            getRequest = RequestBuilder.newBuilder().method("event.get")
                    //.paramEntry("search", search)
                    //.paramEntry("filter", filter)
                    .paramEntry("output", "extend").paramEntry("only_true", "1")
                    .paramEntry("sortfield", new String[]{"eventid"})
                    .paramEntry("sortorder", new String[]{"DESC"}).paramEntry("limit", "1")
                    .build();

        } catch (Exception ex) {
            logger.error(String.format("General Error while get Events from API: %s ", ex));
            throw new RuntimeException("Failed create JSON request for get System LAst Event ID");
        }

        JSONArray actions;
        try {
            getResponse = zabbixApi.call(getRequest);
            actions = getResponse.getJSONArray("result");
        } catch (Exception e) {
            logger.error(String.format("General Error while get Events from API: %s ", e));
            throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
        }

        return actions.getJSONObject(0).getString("eventid");
    }

    private List<Event> getEventsFromLastId(DefaultZabbixApi zabbixApi, String[] openTriggerEventIDs, Boolean searchfornew) {

        RequestBuilder requestBuilder;
        Request getRequest;
        JSONObject getResponse;

        try {
            requestBuilder = RequestBuilder.newBuilder().method("event.get")
                    .paramEntry("output", "extend").paramEntry("only_true", "1")
                    .paramEntry("selectHosts", new String[]{"hostid", "host", "name"})
                    .paramEntry("select_alerts", new String[]{"alertid", "actionid", "eventid", "message"})
                    .paramEntry("selectRelatedObject",
                            new String[]{"triggerid", "description", "status", "value", "priority"})
                    .paramEntry("sortfield", new String[]{"clock", "eventid"})
                    .paramEntry("sortorder", new String[]{"ASC"})
                    .paramEntry("limit", endpoint.getConfiguration().getMaxEventsPerRequest());

            if (searchfornew) {
                requestBuilder = requestBuilder.paramEntry("eventid_from", openTriggerEventIDs[0]);
            } else {
                requestBuilder = requestBuilder.paramEntry("eventids", openTriggerEventIDs);
            }

            getRequest = requestBuilder.build();

        } catch (Exception ex) {
            logger.error(String.format("General Error while get Events from API: %s ", ex));
            throw new RuntimeException("Failed create JSON request for get Open Events by ID");
        }

        JSONArray openEvents;
        try {
            getResponse = zabbixApi.call(getRequest);
            openEvents = getResponse.getJSONArray("result");

        } catch (Exception e) {
            logger.error(String.format("General Error while get Events from API: %s ", e));
            throw new RuntimeException("Failed get JSON response result for Open Events by ID");
        }

        // array for generated events from received JSONs
        List<Event> eventsList = new ArrayList<>();

        logger.info("***** Finded Zabbix Events: " + openEvents.size());

        // get action IDs from zabbix (using adapter config parameter 'zabbixactionprefix')
        String[] systemActionIDs = getSystemActionsID(zabbixApi);

        // try to find alerts in json event's array
        for (int i = 0; i < openEvents.size(); i++) {

            JSONObject event = openEvents.getJSONObject(i);

            logger.debug("*** Received JSON Event: " + event.toString());

            Event eventFromJson = checkAlertInJsonAndCreateEvent(systemActionIDs, event);
            // Add generated event to list
            if (eventFromJson != null)
                eventsList.add(eventFromJson);

        }

        logger.debug("**** Received Total well-formatted Events: " + eventsList.size());

        return eventsList;
    }

    private Event checkAlertInJsonAndCreateEvent(String[] systemActionIDs, JSONObject event) {
        String alertActionId;

        JSONArray alerts = event.getJSONArray("alerts");

        // try to find alerts in event
        // if found one - exit from loop
        int x = 0;
        boolean exitFlag = false;
        Event eventFromJson = null;
        while (x < alerts.size() && !exitFlag) {
            JSONObject alert = alerts.getJSONObject(x);
            alertActionId = alert.getString("actionid");

            // if pattern of Zabbix action name is appropriate
            if (Arrays.asList(systemActionIDs).contains(alertActionId)) {
                // create event
                eventFromJson = createEventUsingAlertFromJson(event, alert);

                exitFlag = true;
            } else {
                // if pattern of Zabbix action name is not appropriate
                logger.error("**** Zabbix action name is not in appropriated List. ");
            }

            x++;
        }

        return eventFromJson;
    }

    private Event createEventUsingAlertFromJson(JSONObject event, JSONObject alert) {

        Event genEvent = new Event();

        String message;
        HashMap<String, Object> alertresult;
        message = alert.getString("message");
        logger.debug("******** Received JSON Alert message: " + message);

        Pattern p = Pattern.compile(":echo '(.*)' > '.*", Pattern.DOTALL);
        Matcher matcher = p.matcher(message);

        // if message's pattern slot in alert is appropriate
        if (matcher.matches()) {
            message = matcher.group(1);
            logger.debug("******** Received JSON Alert XML message: " + message);
            logger.debug("******** Trying to parse XML...  ");

            // try to parse XML of alert's message
            alertresult = parseXMLtoObject(message);

            // Generate Event
            if (alertresult != null) {
                logger.debug("**** Received well-formatted message.  ");
                logger.debug("**** Trying to generate event for it...  ");
                String hostHost = event.getJSONArray("hosts").getJSONObject(0).getString("host");
                String hostName = event.getJSONArray("hosts").getJSONObject(0).getString("name");
                String hostId = event.getJSONArray("hosts").getJSONObject(0).getString("hostid");
                String objectId = event.getString("objectid");
                String eventId = event.getString("eventid");
                String status = event.getString("value");
                String timestamp = event.getString("clock");
                genEvent = genEventObj(hostHost, hostId, hostName, eventId,
                        status, timestamp, objectId, alertresult);

            } else {
                logger.error("**** Received bad-formatted message.  ");
                genEvent = null;
            }

        }
        return genEvent;
    }

    @SuppressWarnings("checkstyle:methodlength")
    //CHECKSTYLE:OFF
    private Event genEventObj(String hostHost, String hostid, String hostName,
                              String eventId, String status, String timestamp,
                              String objectid, HashMap<String, Object> alertresult) {
        //CHECKSTYLE:OFF

        Event event = new Event();
        String triggername;
        String value;
        String severity;
        String template;
        String itemname;

        try {
            triggername = alertresult.get("triggername").toString();
            value = alertresult.get("value").toString();
            severity = alertresult.get("severity").toString();
            template = alertresult.get("template").toString();
            itemname = alertresult.get("itemname").toString();
        } catch (Exception e) {
            logger.error(String.format("General Error while get Events from API: %s ", e));
            return null;
        }

        String newhostname;
        String parentID = hostid;
        String newobject = itemname;

        // Check host Aliases
        // Example: KRL-PHOBOSAU--MSSQL
        // if pattern of hostHost or hostName is appropriate as alias-name
        logger.debug("**** Check Zabbix hostHost and hostName for Aliases.");
        newhostname = checkHostPattern(hostHost, hostName);
        if (newhostname == null) {
            logger.debug("**** No Aliases found.");
            newhostname = hostHost;
            logger.debug("Use hostid (as ciid): " + hostid + " of hostHost: " + hostHost);
            // Use hostid (as ciid) of hostHost
            parentID = hostid;
        } else {
            newhostname = checkHostAliases(null, hostHost, hostName)[1];
        }

        event.setExternalid(eventId);
        event.setStatus(setRightStatus(status));
        event.setMessage(String.format("%s: %s", triggername, value));

        Long newTimestamp = (long) Integer.parseInt(timestamp);
        event.setTimestamp(newTimestamp);

        logger.debug("*** Received Zabbix Item : " + itemname);

        // zabbix_item_ke_pattern=\\[(.*)\\](.*)
        String pattern = endpoint.getConfiguration().getItemCiPattern();
        logger.debug("*** Zabbix Item CI Pattern: " + pattern);

        // zabbixItemCiParentPattern=(.*)::(.*)
        String zabbixItemCiParentPattern = endpoint.getConfiguration().getItemCiParentPattern();
        Pattern ciWithParentPattern = Pattern.compile(zabbixItemCiParentPattern);

        // Example item as CI :
        // [test CI item] bla-bla
        Pattern p = Pattern.compile(pattern);
        Matcher matcher = p.matcher(itemname);

        // if Item has CI pattern
        if (matcher.matches()) {

            logger.debug("*** Finded Zabbix Item with Pattern as CI: " + itemname);
            // save as ne CI name
            itemname = matcher.group(1).toUpperCase();
            newobject = itemname;

            // CI 2 (CIITEM)::CI 3 (CIITEM2)
            Matcher matcher2 = ciWithParentPattern.matcher(itemname);
            if (matcher2.matches()) {
                logger.debug("*** Finded Zabbix Item with Pattern with Parent: " + itemname);
                itemname = matcher2.group(2).trim().toUpperCase();

                logger.debug("*** itemname: " + itemname);
            }

            // get SHA-1 hash for hostHost-item block for saving as ciid
            // Example:
            // KRL-PHOBOSAU--PHOBOS:TEST CI ITEM (TYPE)
            logger.debug(String.format("*** Trying to generate hash for Item with Pattern: %s:%s",
                    hostHost, itemname));
            String hash = null;
            try {
                hash = hashString(String.format("%s:%s", hostHost, itemname), "SHA-1");
            } catch (Exception e) {
                logger.error(String.format("General Error while create hash: %s ", e));
            }
            logger.debug("*** Generated Hash: " + hash);
            parentID = hash;
        }

        // Example Template name :
        // Template --SNMP Traps Nortel--
        p = Pattern.compile(endpoint.getConfiguration().getZabbixtemplatepattern());
        matcher = p.matcher(template);

        // if Template name has Template pattern
        if (matcher.matches()) {
            logger.debug("*** Zabbix Template Pattern: " + endpoint.getConfiguration().getZabbixtemplatepattern());
            logger.debug("*** Finded Zabbix Template with Pattern: " + template);
            event.setEventCategory(matcher.group(1));
        }

        event.setParametr(objectid);
        event.setOrigin(hostHost);
        event.setCi(String.format("%s:%s", endpoint.getConfiguration().getSource(), parentID));
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
            logger.error(String.format("Error while create XML builder: %s ", e1));
            return null;
        }
        Document doc;
        try {
            doc = builder.parse(new InputSource(new StringReader(xmlmessage)));
        } catch (SAXException | IOException e) {
            logger.error(String.format("Error while parsing XML string: %s ", e));
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
                if ("ns".equals(arg0)) {
                    return endpoint.getConfiguration().getZabbixActionXmlNs();
                }
                return null;
            }
        });

        return parseXmlForEventAttributes(doc, xpath);

    }

    private HashMap<String, Object> parseXmlForEventAttributes(Document doc, XPath xpath) {
        try {
            XPathExpression expr;
            Object result;

            logger.debug("[XML PARSING] getNamespaceURI: " + xpath.getNamespaceContext().getNamespaceURI("ns"));
            HashMap<String, Object> alertreturn = new HashMap<>(6);

            // <ns:severity>Information</ns:severity>
            logger.debug("[XML PARSING] try to get Severity...");
            expr = xpath.compile("/ns:zabbixEvent/ns:severity");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String severity = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML severity : " + severity);
            alertreturn.put("severity", severity);

            /* <ns:value>
            <![CDATA[16:53:06 2015/12/25 TYPE=NORTEL::CODE=AUD0000::SEVERITY=Info::DESCRIPTION=One pass made by audit.
            No errors detected. System software OK..Action: No ]]>
            </ns:value> */
            logger.debug("[XML PARSING] try to get Value...");
            expr = xpath.compile("/ns:zabbixEvent/ns:value");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String value = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML value : " + value);
            alertreturn.put("value", value);

            // <ns:hostgroup>АТС </ns:hostgroup>
            logger.debug("[XML PARSING] try to get Hostgroup...");
            expr = xpath.compile("/ns:zabbixEvent/ns:hostgroup");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String hostgroup = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML hostgroup : " + hostgroup);
            alertreturn.put("hostgroup", hostgroup);

            // <ns:template>SNMP Traps Nortel </ns:template>
            logger.debug("[XML PARSING] try to get Template...");
            expr = xpath.compile("/ns:zabbixEvent/ns:template");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String template = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML template : " + template);
            alertreturn.put("template", template);

            // <ns:triggername><![CDATA[SNMP Info trap]]></ns:triggername>
            logger.debug("[XML PARSING] try to get Triggername...");
            expr = xpath.compile("/ns:zabbixEvent/ns:triggername");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String triggername = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML triggername : " + triggername);
            alertreturn.put("triggername", triggername);

            // <ns:itemid>23876</ns:itemid>
            logger.debug("[XML PARSING] try to get Itemid...");
            expr = xpath.compile("/ns:zabbixEvent/ns:itemid");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String itemid = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML itemid : " + itemid);
            alertreturn.put("itemid", itemid);

            // <ns:itemname><![CDATA[SNMP Trap SEVERITY=Info]]>
            logger.debug("[XML PARSING] try to get Itemname...");
            expr = xpath.compile("/ns:zabbixEvent/ns:itemname");
            result = expr.evaluate(doc, XPathConstants.NODESET);
            String itemname = ((NodeList) result).item(0).getTextContent();
            logger.debug("*** Received XML itemname : " + itemname);
            alertreturn.put("itemname", itemname);

            return alertreturn;

        } catch (XPathExpressionException | DOMException e) {
            logger.error(String.format("Error while get 'itemid' from XML string: %s ", e));
            return null;
        }
    }

    private String[] getSystemActionsID(DefaultZabbixApi zabbixApi) {

        String prefix = endpoint.getConfiguration().getZabbixactionprefix();
        Request getRequest;
        JSONObject getResponse;

        try {
            JSONObject filter = new JSONObject();
            JSONObject search = new JSONObject();

            search.put("name", new String[]{prefix + "*"});
            filter.put("eventsource", 0);

            getRequest = RequestBuilder.newBuilder().method("action.get")
                    .paramEntry("search", search)
                    .paramEntry("filter", filter)
                    .paramEntry("output", new String[]{"actionid", "name"})
                    .paramEntry("searchWildcardsEnabled", 1)
                    .build();

        } catch (Exception ex) {
            logger.error(String.format("Error while create HTTP request to API: %s ", ex));
            throw new RuntimeException("Failed create JSON request for get System Actions");
        }

        JSONArray actions;
        try {
            getResponse = zabbixApi.call(getRequest);
            actions = getResponse.getJSONArray("result");
        } catch (Exception e) {
            logger.error(String.format("Error while get HTTP response from API: %s ", e));
            throw new RuntimeException("Failed get JSON response result for get System Actions.");
        }

        String[] actionids = new String[]{};

        for (int i = 0; i < actions.size(); i++) {

            JSONObject action = actions.getJSONObject(i);
            String actionid = action.getString("actionid");
            actionids = (String[]) ArrayUtils.add(actionids, actionid);

            logger.debug("*** Received JSON System ActionID: " + actionid);
        }

        return actionids;

    }

    private String[] getOpenTriggers(DefaultZabbixApi zabbixApi) {

        Request getRequest;
        JSONObject getResponse;
        try {

            getRequest = RequestBuilder.newBuilder().method("trigger.get")
                    // .paramEntry("search", search)
                    .paramEntry("output", "extend")
                    .paramEntry("only_true", "1")
                    .paramEntry("selectLastEvent", "extend")
                    .build();

        } catch (Exception ex) {
            logger.error(String.format("Error while create HTTP request to API: %s ", ex));
            throw new RuntimeException("Failed create JSON request for get all Open Triggers.");
        }

        JSONArray openTriggers;
        try {
            getResponse = zabbixApi.call(getRequest);
            openTriggers = getResponse.getJSONArray("result");

        } catch (Exception e) {
            logger.error(String.format("Error while get HTTP response from API: %s ", e));
            throw new RuntimeException("Failed get JSON response result for all Open Triggers.");
        }

        String[] eventids = new String[]{};

        for (int i = 0; i < openTriggers.size(); i++) {

            JSONObject trigger = openTriggers.getJSONObject(i);
            // "triggerid": "13779"
            String triggerid = trigger.getString("triggerid");

            try {
                String eventid = trigger.getJSONObject("lastEvent").getString("eventid");
                String status = trigger.getJSONObject("lastEvent").getString("value");
                eventids = (String[]) ArrayUtils.add(eventids, eventid);

                logger.debug("*** Received JSON EventID: " + eventid);
                logger.debug("*** Received JSON Event Value: " + status);
            } catch (Exception e) {
                logger.error(String.format("Error while get attributes from JSONObject: %s ", e));
                logger.debug("*** No event for trigger: " + triggerid);
            }

        }

        logger.debug("*** Received JSON All Actual EventID: " + eventids.length);

        return eventids;
    }

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
        exchange.getIn().setBody(genevent, Event.class);

        exchange.getIn().setHeader("EventIdAndStatus", "Error_" + timestamp);
        exchange.getIn().setHeader("Timestamp", timestamp);
        exchange.getIn().setHeader("queueName", "Events");
        exchange.getIn().setHeader("Type", "Error");

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            logger.error("Ошибка при передачи сообщения в очередь: ", e);
        }

    }

    private String setRightSeverity(String severity) {
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
            case "DISASTER":
                newseverity = PersistentEventSeverity.CRITICAL.name();
                break;
            case "HIGH":
                newseverity = PersistentEventSeverity.MAJOR.name();
                break;
            case "AVERAGE":
                newseverity = PersistentEventSeverity.MINOR.name();
                break;
            case "WARNING":
                newseverity = PersistentEventSeverity.WARNING.name();
                break;
            case "INFORMATION":
                newseverity = PersistentEventSeverity.INFO.name();
                break;

            default:
                newseverity = PersistentEventSeverity.INFO.name();
                break;

        }
        logger.debug("***************** severity: " + severity);
        logger.debug("***************** newseverity: " + newseverity);
        return newseverity;
    }

    private String setRightStatus(String status) {
        String newstatus;
        /*
         *
* State of the related object.

* Possible values for trigger events:
* 0 - OK;
* 1 - problem.
         */

        switch (status) {
            case "1":
                newstatus = "OPEN";
                break;
            case "0":
                newstatus = "CLOSED";
                break;
            default:
                newstatus = "OPEN";
                break;

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