package ru.atc.camel.zabbix.api.events;

//import org.apache.camel.spi.UriParam;

import org.apache.camel.spi.UriParams;

@UriParams
public class ZabbixAPIConfiguration {

    private static final int MAX_EVENTS_PER_REQUEST = 500;

    private String zabbixip;

    private String username;

    private String adaptername;

    private String password;

    private String source;

    private String zabbixapiurl;

    private String zabbixactionprefix;

    private String zabbixActionXmlNs;

    private String zabbixtemplatepattern;

    private String groupCiPattern;

    private String groupSearchPattern;

    private String itemCiPattern;

    private String itemCiParentPattern;

    private String itemCiTypePattern;

    private String lasteventid;

    private int maxEventsPerRequest = MAX_EVENTS_PER_REQUEST;

    private int delay = 2; // in minutes

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getZabbixip() {
        return zabbixip;
    }

    public void setZabbixip(String zabbixip) {
        this.zabbixip = zabbixip;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAdaptername() {
        return adaptername;
    }

    public void setAdaptername(String adaptername) {
        this.adaptername = adaptername;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getZabbixapiurl() {
        return zabbixapiurl;
    }

    public void setZabbixapiurl(String zabbixapiurl) {
        this.zabbixapiurl = zabbixapiurl;
    }

    public String getLasteventid() {
        return lasteventid;
    }

    public void setLasteventid(String lasteventid) {
        this.lasteventid = lasteventid;
    }

    public String getZabbixactionprefix() {
        return zabbixactionprefix;
    }

    public void setZabbixactionprefix(String zabbixactionprefix) {
        this.zabbixactionprefix = zabbixactionprefix;
    }

    public String getZabbixtemplatepattern() {
        return zabbixtemplatepattern;
    }

    public void setZabbixtemplatepattern(String zabbixtemplatepattern) {
        this.zabbixtemplatepattern = zabbixtemplatepattern;
    }

    public String getGroupCiPattern() {
        return groupCiPattern;
    }

    public void setGroupCiPattern(String groupCiPattern) {
        this.groupCiPattern = groupCiPattern;
    }

    public String getGroupSearchPattern() {
        return groupSearchPattern;
    }

    public void setGroupSearchPattern(String groupSearchPattern) {
        this.groupSearchPattern = groupSearchPattern;
    }

    public String getItemCiPattern() {
        return itemCiPattern;
    }

    public void setItemCiPattern(String itemCiPattern) {
        this.itemCiPattern = itemCiPattern;
    }

    public String getItemCiParentPattern() {
        return itemCiParentPattern;
    }

    public void setItemCiParentPattern(String itemCiParentPattern) {
        this.itemCiParentPattern = itemCiParentPattern;
    }

    public int getMaxEventsPerRequest() {
        return maxEventsPerRequest;
    }

    public void setMaxEventsPerRequest(int maxEventsPerRequest) {
        this.maxEventsPerRequest = maxEventsPerRequest;
    }

    public String getZabbixActionXmlNs() {
        return zabbixActionXmlNs;
    }

    public void setZabbixActionXmlNs(String zabbixActionXmlNs) {
        this.zabbixActionXmlNs = zabbixActionXmlNs;
    }

    public String getItemCiTypePattern() {
        return itemCiTypePattern;
    }

    public void setItemCiTypePattern(String itemCiTypePattern) {
        this.itemCiTypePattern = itemCiTypePattern;
    }
}