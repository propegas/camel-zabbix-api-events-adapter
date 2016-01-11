package ru.atc.camel.zabbix.api.events;

//import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ZabbixAPIConfiguration {	
    
	private String zabbixip;
	
	private String username;
	
	private String adaptername;
	
	private String password;
	
	private String source;
	
	private String zabbixapiurl;
	
	private String zabbixactionprefix;
	
	private String zabbixtemplatepattern;
	
	
	private String lasteventid;

    private int delay = 720;
  
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


}