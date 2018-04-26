package ru.atc.camel.zabbix.api.events;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import ru.atc.adapters.type.Event;

import static ru.atc.zabbix.general.CiItems.checkHostAliases;
import static ru.atc.zabbix.general.CiItems.checkHostPattern;
import static ru.atc.zabbix.general.CiItems.checkItemForCi;

/**
 * Created by vgoryachev on 30.05.2016.
 * Package: ru.atc.camel.zabbix.api.events.
 */
public class ZabbixAPIConsumerTest {

    @Test
    public void testCiItemNaming() throws Exception {

        String itemname = "[Контроллер B (Контроллеры)::Expander Port: Enclosure ID 1, Controller B, Phy 22, PHY index 22, Type Drive (IO порты)] Status";
        String hostid = "10511";
        String hostHost = "MSA2040-C2-2";
        String hostName = "MSA2040-C2-2";
        String newhostname = checkHostPattern(hostHost, hostName);

        if (newhostname == null) {
            newhostname = hostHost;
            // Use hostid (as ciid) of hostHost
            //ciId = hostid;
        } else {
            newhostname = checkHostAliases(null, hostHost, hostName)[1];
        }

        String[] returnCiArray = checkItemForCi(itemname, hostid, newhostname,
                "\\[(.*)\\](.*)",
                "(.*)::(.*)",
                "(.*)\\((.*)\\)");
        Assert.assertThat(returnCiArray[0], CoreMatchers.is("MSA2040-C2-2:EXPANDER PORT: ENCLOSURE ID 1, CONTROLLER B, PHY 22, PHY INDEX 22, TYPE DRIVE (IO ПОРТЫ)"));

    }

    @Test
    public void testCiItemNamingWeb() throws Exception {

        String itemname = "Response code for step \"[Интеграция МЭДО(BusinessFunction)] Проверка\" of scenario \"[Интеграция МЭДО(BusinessFunction)] Проверка\"";
        String hostid = "10267";
        String hostHost = "CM-PG-node-01.nso.loc";
        String hostName = "CM-PG-node-01.nso.loc";
        String newhostname = checkHostPattern(hostHost, hostName);

        if (newhostname == null) {
            newhostname = hostHost;
            // Use hostid (as ciid) of hostHost
            //ciId = hostid;
        } else {
            newhostname = checkHostAliases(null, hostHost, hostName)[1];
        }

        String[] returnCiArray = checkItemForCi(itemname, hostid, newhostname,
                "\\[(.*)\\](.*)",
                "(.*)::(.*)",
                "(.*)\\((.*)\\)");
        Assert.assertThat(returnCiArray[0], CoreMatchers.is("CM-PG-node-01.nso.loc:ИНТЕГРАЦИЯ МЭДО(BUSINESSFUNCTION)"));

    }

    @Test
    public void testCiItemNaming2() throws Exception {

        String itemname = "Status";
        String hostid = "10511";
        String hostHost = "MSA2040-C2-2";
        String hostName = "MSA2040-C2-2";
        String newhostname = checkHostPattern(hostHost, hostName);

        if (newhostname == null) {
            newhostname = hostHost;
            // Use hostid (as ciid) of hostHost
            //ciId = hostid;
        } else {
            newhostname = checkHostAliases(null, hostHost, hostName)[1];
        }

        String[] returnCiArray = checkItemForCi(itemname, hostid, newhostname,
                "\\[(.*)\\](.*)",
                "(.*)::(.*)",
                "(.*)\\((.*)\\)");
        Assert.assertThat(returnCiArray[0], CoreMatchers.is("10511"));

    }

    @Test
    public void testCiItemNaming3() throws Exception {

        String itemname = "Status";
        String hostid = "10511";
        String hostHost = "MSA2040-C2-2--OS";
        String hostName = "MSA2040-C2-2--OS";
        String newhostname = checkHostPattern(hostHost, hostName);

        if (newhostname == null) {
            newhostname = hostHost;
            // Use hostid (as ciid) of hostHost
            //ciId = hostid;
        } else {
            newhostname = checkHostAliases(null, hostHost, hostName)[1];
        }

        String[] returnCiArray = checkItemForCi(itemname, hostid, newhostname,
                "\\[(.*)\\](.*)",
                "(.*)::(.*)",
                "(.*)\\((.*)\\)");
        Assert.assertThat(returnCiArray[0], CoreMatchers.is("10511"));

        Assert.assertThat(checkHostAliases(null, hostHost, hostName)[0], CoreMatchers.is(""));

        Assert.assertThat(checkHostAliases(null, hostHost, hostName)[1], CoreMatchers.is("OS"));

        Assert.assertThat(checkHostAliases(null, hostHost, hostName)[2], CoreMatchers.is("MSA2040-C2-2"));

    }

    @Test
    public void testCiItemNaming4() throws Exception {

        ZabbixAPIComponent zabbixAPIComponent = new ZabbixAPIComponent();

        Processor processor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        };

        ZabbixAPIConsumer testCons;
        ZabbixAPIEndpoint zabbixAPIEndpoint = new ZabbixAPIEndpoint("", "", zabbixAPIComponent);
        ZabbixAPIConfiguration zabbixAPIConfiguration = new ZabbixAPIConfiguration();
        zabbixAPIConfiguration.setDelay(5);
        zabbixAPIConfiguration.setZabbixapiurl("http://172.20.19.195/zabbix/api_jsonrpc.php");
        zabbixAPIConfiguration.setUsername("Admin");
        zabbixAPIConfiguration.setPassword("zabbix");
        zabbixAPIConfiguration.setZabbixActionXmlNs("http://skuf.gosuslugi.ru/mon/");
        zabbixAPIConfiguration.setItemCiParentPattern("(.*)::(.*)");
        zabbixAPIConfiguration.setItemCiPattern("\\[(.*)\\](.*)");
        zabbixAPIConfiguration.setItemCiTypePattern("(.*)\\((.*)\\)");
        zabbixAPIConfiguration.setSource("Zabbix");
        zabbixAPIConfiguration.setZabbixtemplatepattern(".*--(.*)--.*");
        zabbixAPIConfiguration.setHostAliasPattern("(.*)--(.*)");
        zabbixAPIEndpoint.setConfiguration(zabbixAPIConfiguration);
        testCons = new ZabbixAPIConsumer(zabbixAPIEndpoint, processor);

        testCons.setZabbixApiFromSearchEvents(testCons.initZabbixApi());

        String stringEventFromZabbix = "{\"ns\":\"599417900\",\"source\":\"0\",\"clock\":\"1464681283\",\"alerts\":[{\"message\":\":echo '<ns:zabbixEvent xmlns:ns=\\\"http://skuf.gosuslugi.ru/mon/\\\" eventid=\\\"11836800\\\"><ns:date>2016.05.31</ns:date><ns:time>10:54:43</ns:time><ns:host>172.20.150.179--NORTEL</ns:host><ns:triggername><![CDATA[SNMP Minor trap]]></ns:triggername><ns:triggerid>15056</ns:triggerid><ns:status>PROBLEM</ns:status><ns:itemid>32580</ns:itemid><ns:value><![CDATA[10:54:42 2016/05/31 TYPE=NORTEL::CODE=DTA0301::SEVERITY=Minor::DESCRIPTION=loop A slip deletion has occurred on PRI2 loop.]]></ns:value><ns:itemkey><![CDATA[snmptrap[SEVERITY=Minor]]]></ns:itemkey><ns:itemkeyorig><![CDATA[snmptrap[SEVERITY=Minor]]]></ns:itemkeyorig><ns:itemname><![CDATA[SNMP Trap SEVERITY=Minor]]></ns:itemname><ns:itemnameorig><![CDATA[SNMP Trap $1]]></ns:itemnameorig><ns:severity>Average</ns:severity><ns:triggerurl></ns:triggerurl><ns:proxyname>Proxy: </ns:proxyname><ns:hostgroup>Невский Nortel, (Невский.СТС)ГЭС-6 Волховская </ns:hostgroup><ns:template>Template --SNMP Traps Nortel-- </ns:template><ns:metadescription><![CDATA[and {Template --SNMP Traps Nortel--:snmptrap[SEVERITY=Minor].nodata(60m)}=0]]></ns:metadescription><ns:alias>{$MC_SMC_ALIAS}</ns:alias></ns:zabbixEvent>' > '/dev/null'\",\"actionid\":\"7\",\"alertid\":\"560477\",\"mediatypes\":[],\"eventid\":\"11836800\"}],\"acknowledged\":\"0\",\"hosts\":[{\"host\":\"172.20.150.179--NORTEL\",\"hostid\":\"10238\",\"name\":\"Диспетчерская АТС ГЭС-6--NORTEL\"}],\"value\":\"1\",\"object\":\"0\",\"objectid\":\"15056\",\"eventid\":\"11836800\",\"relatedObject\":{\"triggerid\":\"15056\",\"status\":\"0\",\"priority\":\"3\",\"description\":\"SNMP Minor trap\",\"value\":\"1\"}}";
        JSONObject jsonEventFromZabbix = (JSONObject) JSON.parse(stringEventFromZabbix);

        Event eventFromJson = testCons.checkAlertInJsonAndCreateEvent(new String[]{"7", "8", "9"}, jsonEventFromZabbix);

        Assert.assertThat(eventFromJson.getHost(), CoreMatchers.is("172.20.150.179"));
        Assert.assertThat(eventFromJson.getCi(), CoreMatchers.is("Zabbix:10238"));
        Assert.assertThat(eventFromJson.getOrigin(), CoreMatchers.is("172.20.150.179--NORTEL"));
        Assert.assertThat(eventFromJson.getObject(), CoreMatchers.is("SNMP Trap SEVERITY=Minor".toUpperCase()));
        Assert.assertThat(eventFromJson.getParametr(), CoreMatchers.is("15056"));
    }

    @Test
    public void testCiItemNaming5() throws Exception {

        ZabbixAPIComponent zabbixAPIComponent = new ZabbixAPIComponent();

        Processor processor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        };

        ZabbixAPIConsumer testCons;
        ZabbixAPIEndpoint zabbixAPIEndpoint = new ZabbixAPIEndpoint("", "", zabbixAPIComponent);
        ZabbixAPIConfiguration zabbixAPIConfiguration = new ZabbixAPIConfiguration();
        zabbixAPIConfiguration.setDelay(5);
        zabbixAPIConfiguration.setZabbixActionXmlNs("http://skuf.gosuslugi.ru/mon/");
        zabbixAPIConfiguration.setItemCiParentPattern("(.*)::(.*)");
        zabbixAPIConfiguration.setItemCiPattern("\\[(.*)\\](.*)");
        zabbixAPIConfiguration.setItemCiTypePattern("(.*)\\((.*)\\)");
        zabbixAPIConfiguration.setSource("Zabbix");
        zabbixAPIConfiguration.setZabbixtemplatepattern(".*--(.*)--.*");
        zabbixAPIEndpoint.setConfiguration(zabbixAPIConfiguration);
        testCons = new ZabbixAPIConsumer(zabbixAPIEndpoint, processor);

        String stringEventFromZabbix = "{\"ns\":\"67303730\",\"source\":\"0\",\"clock\":\"1464622425\",\"alerts\":[{\"message\":\":echo '<ns:zabbixEvent xmlns:ns=\\\"http://skuf.gosuslugi.ru/mon/\\\" eventid=\\\"11773150\\\"><ns:date>2016.05.30</ns:date><ns:time>18:33:45</ns:time><ns:host>MSA2040_02_C1</ns:host><ns:triggername><![CDATA[Informational Event received on MSA2040_02_C1]]></ns:triggername><ns:triggerid>53537</ns:triggerid><ns:status>PROBLEM</ns:status><ns:itemid>240706</ns:itemid><ns:value><![CDATA[2016-05-30 18:32:36 INFORMATIONAL 205 A715124 A mapping or masking operation for a volume was performed. (pool: A, volume: DS02-00-C1_s000, SN: 00c0ff2629cf000001874c5701000000) (access: read-only, LUN: 20)]]></ns:value><ns:itemkey><![CDATA[hp.p2000.stats[events,controller_a,event]]]></ns:itemkey><ns:itemkeyorig><![CDATA[hp.p2000.stats[events,controller_a,event]]]></ns:itemkeyorig><ns:itemname><![CDATA[[Полка 1 (Полки)::Контроллер A (Контроллеры)] Events log]]></ns:itemname><ns:itemnameorig><![CDATA[[Полка 1 (Полки)::Контроллер A (Контроллеры)] Events log]]></ns:itemnameorig><ns:severity>Information</ns:severity><ns:triggerurl></ns:triggerurl><ns:proxyname>Proxy: </ns:proxyname><ns:hostgroup>HP MSA Storage MSA 2040 SAN, (Невский.СХД)ТЭЦ-17 Выборгская </ns:hostgroup><ns:template>Template --HPP2000-MSA-- </ns:template><ns:metadescription><![CDATA[]]></ns:metadescription><ns:alias>{$MC_SMC_ALIAS}</ns:alias></ns:zabbixEvent>' > '/dev/null'\",\"actionid\":\"7\",\"alertid\":\"557071\",\"mediatypes\":[],\"eventid\":\"11773150\"}],\"acknowledged\":\"0\",\"hosts\":[{\"host\":\"MSA2040_02_C1\",\"hostid\":\"10499\",\"name\":\"MSA2040_02_C1\"}],\"value\":\"1\",\"object\":\"0\",\"objectid\":\"53537\",\"eventid\":\"11773150\",\"relatedObject\":{\"triggerid\":\"53537\",\"status\":\"0\",\"priority\":\"1\",\"description\":\"Informational Event received on {HOST.HOST}\",\"value\":\"1\"}}";
        JSONObject jsonEventFromZabbix = (JSONObject) JSON.parse(stringEventFromZabbix);

        Event eventFromJson = testCons.checkAlertInJsonAndCreateEvent(new String[]{"7", "8", "9"}, jsonEventFromZabbix);

        Assert.assertThat(eventFromJson.getHost(), CoreMatchers.is("MSA2040_02_C1"));
        Assert.assertThat(eventFromJson.getCi(), CoreMatchers.is("Zabbix:MSA2040_02_C1:КОНТРОЛЛЕР A (КОНТРОЛЛЕРЫ)"));
        Assert.assertThat(eventFromJson.getOrigin(), CoreMatchers.is("MSA2040_02_C1"));
        Assert.assertThat(eventFromJson.getObject(), CoreMatchers.is("КОНТРОЛЛЕР A".toUpperCase()));
        Assert.assertThat(eventFromJson.getParametr(), CoreMatchers.is("53537"));
    }

    @Test
    public void testCiItemNaming6() throws Exception {

        ZabbixAPIComponent zabbixAPIComponent = new ZabbixAPIComponent();

        Processor processor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        };

        ZabbixAPIConsumer testCons;
        ZabbixAPIEndpoint zabbixAPIEndpoint = new ZabbixAPIEndpoint("", "", zabbixAPIComponent);
        ZabbixAPIConfiguration zabbixAPIConfiguration = new ZabbixAPIConfiguration();
        zabbixAPIConfiguration.setDelay(5);
        zabbixAPIConfiguration.setZabbixActionXmlNs("http://skuf.gosuslugi.ru/mon/");
        zabbixAPIConfiguration.setItemCiParentPattern("(.*)::(.*)");
        zabbixAPIConfiguration.setItemCiPattern("\\[(.*)\\](.*)");
        zabbixAPIConfiguration.setItemCiTypePattern("(.*)\\((.*)\\)");
        zabbixAPIConfiguration.setSource("Zabbix");
        zabbixAPIConfiguration.setZabbixtemplatepattern(".*--(.*)--.*");
        zabbixAPIEndpoint.setConfiguration(zabbixAPIConfiguration);
        testCons = new ZabbixAPIConsumer(zabbixAPIEndpoint, processor);

        String stringEventFromZabbix = "{\"ns\":\"870992284\",\"source\":\"0\",\"clock\":\"1464613592\",\"alerts\":[{\"message\":\":echo '<ns:zabbixEvent xmlns:ns=\\\"http://skuf.gosuslugi.ru/mon/\\\" eventid=\\\"11763576\\\"><ns:date>2016.05.30</ns:date><ns:time>16:06:32</ns:time><ns:host>MSA2040-C2-2</ns:host><ns:triggername><![CDATA[Изменился Health-статус IO-порта Enclosure ID 1, Controller A, Phy 0, PHY index 24, Type SC-1]]></ns:triggername><ns:triggerid>62407</ns:triggerid><ns:status>PROBLEM</ns:status><ns:itemid>230389</ns:itemid><ns:value><![CDATA[OK (1)]]></ns:value><ns:itemkey><![CDATA[hp.p2000.stats[ioports,_1_a_0_24_sc-1,elem-status-numeric]]]></ns:itemkey><ns:itemkeyorig><![CDATA[hp.p2000.stats[ioports,_1_a_0_24_sc-1,elem-status-numeric]]]></ns:itemkeyorig><ns:itemname><![CDATA[[Контроллер A (Контроллеры)::Expander Port: Enclosure ID 1, Controller A, Phy 0, PHY index 24, Type SC-1 | enc.0.24.sc-1 (IO порты)] Element Status]]></ns:itemname><ns:itemnameorig><![CDATA[[Контроллер A (Контроллеры)::Expander Port: Enclosure ID 1, Controller A, Phy 0, PHY index 24, Type SC-1 | enc.0.24.sc-1 (IO порты)] Element Status]]></ns:itemnameorig><ns:severity>High</ns:severity><ns:triggerurl></ns:triggerurl><ns:proxyname>Proxy: </ns:proxyname><ns:hostgroup>HP MSA Storage MSA 2040 SAN, (Невский.СХД)ТЭЦ-15 Автовская </ns:hostgroup><ns:template>*UNKNOWN* </ns:template><ns:metadescription><![CDATA[last()}<>1]]></ns:metadescription><ns:alias>{$MC_SMC_ALIAS}</ns:alias></ns:zabbixEvent>' > '/dev/null'\",\"actionid\":\"7\",\"alertid\":\"556901\",\"mediatypes\":[],\"eventid\":\"11763576\"}],\"acknowledged\":\"0\",\"hosts\":[{\"host\":\"MSA2040-C2-2\",\"hostid\":\"10511\",\"name\":\"MSA2040-C2-2\"}],\"value\":\"1\",\"object\":\"0\",\"objectid\":\"62407\",\"eventid\":\"11763576\",\"relatedObject\":{\"triggerid\":\"62407\",\"status\":\"0\",\"priority\":\"4\",\"description\":\"Изменился Health-статус IO-порта Enclosure ID 1, Controller A, Phy 0, PHY index 24, Type SC-1\",\"value\":\"1\"}}";
        JSONObject jsonEventFromZabbix = (JSONObject) JSON.parse(stringEventFromZabbix);

        Event eventFromJson = testCons.checkAlertInJsonAndCreateEvent(new String[]{"7", "8", "9"}, jsonEventFromZabbix);

        Assert.assertThat(eventFromJson.getHost(), CoreMatchers.is("MSA2040-C2-2"));
        Assert.assertThat(eventFromJson.getCi(), CoreMatchers.is("Zabbix:MSA2040-C2-2:EXPANDER PORT: ENCLOSURE ID 1, CONTROLLER A, PHY 0, PHY INDEX 24, TYPE SC-1 | ENC.0.24.SC-1 (IO ПОРТЫ)"));
        Assert.assertThat(eventFromJson.getOrigin(), CoreMatchers.is("MSA2040-C2-2"));
        Assert.assertThat(eventFromJson.getObject(), CoreMatchers.is("EXPANDER PORT: ENCLOSURE ID 1, CONTROLLER A, PHY 0, PHY INDEX 24, TYPE SC-1".toUpperCase()));
        Assert.assertThat(eventFromJson.getParametr(), CoreMatchers.is("62407"));
    }

    @Test
    public void testCiItemNaming6_1() throws Exception {

        ZabbixAPIComponent zabbixAPIComponent = new ZabbixAPIComponent();

        Processor processor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        };

        ZabbixAPIConsumer testCons;
        ZabbixAPIEndpoint zabbixAPIEndpoint = new ZabbixAPIEndpoint("", "", zabbixAPIComponent);
        ZabbixAPIConfiguration zabbixAPIConfiguration = new ZabbixAPIConfiguration();
        zabbixAPIConfiguration.setDelay(5);
        zabbixAPIConfiguration.setZabbixActionXmlNs("http://skuf.gosuslugi.ru/mon/");
        zabbixAPIConfiguration.setItemCiParentPattern("(.*)::(.*)");
        zabbixAPIConfiguration.setItemCiPattern("\\[(.*)\\](.*)");
        zabbixAPIConfiguration.setItemCiTypePattern("(.*)\\((.*)\\)");
        zabbixAPIConfiguration.setSource("Zabbix");
        zabbixAPIConfiguration.setZabbixtemplatepattern(".*--(.*)--.*");
        zabbixAPIEndpoint.setConfiguration(zabbixAPIConfiguration);
        testCons = new ZabbixAPIConsumer(zabbixAPIEndpoint, processor);

        String stringEventFromZabbix = "{\"ns\":\"870995581\",\"source\":\"0\",\"clock\":\"1498613592\",\"alerts\":[{\"message\":\":echo '<ns:zabbixEvent xmlns:ns=\\\"http://skuf.gosuslugi.ru/mon/\\\" eventid=\\\"11763576\\\"><ns:date>2016.05.30</ns:date><ns:time>16:06:32</ns:time><ns:host>172.20.14.135</ns:host><ns:triggername><![CDATA[Изменился статус модуля mgmt-module-a]]></ns:triggername><ns:triggerid>58835</ns:triggerid><ns:status>PROBLEM</ns:status><ns:itemid>230389</ns:itemid><ns:value><![CDATA[OK (1)]]></ns:value><ns:itemkey><![CDATA[vplex.stat.module.operational-status[/engines/engine-1-1/mgmt-modules/mgmt-module-a]]]></ns:itemkey><ns:itemkeyorig><![CDATA[vplex.stat.module.operational-status[/engines/engine-1-1/mgmt-modules/mgmt-module-a]]]></ns:itemkeyorig><ns:itemname><![CDATA[[engine-1-1 (Шасси Vplex)::mgmt-module-a (Модули Vplex)] Operational Status]]></ns:itemname><ns:itemnameorig><![CDATA[[[engine-1-1 (Шасси Vplex)::mgmt-module-a (Модули Vplex)] Operational Status]]></ns:itemnameorig><ns:severity>High</ns:severity><ns:triggerurl></ns:triggerurl><ns:proxyname>Proxy: </ns:proxyname><ns:hostgroup>VPLEX, (Невский.СВС)Прочее </ns:hostgroup><ns:template>*UNKNOWN* </ns:template><ns:metadescription><![CDATA[last()}<>1]]></ns:metadescription><ns:alias>{$MC_SMC_ALIAS}</ns:alias></ns:zabbixEvent>' > '/dev/null'\",\"actionid\":\"7\",\"alertid\":\"556901\",\"mediatypes\":[],\"eventid\":\"11763576\"}],\"acknowledged\":\"0\",\"hosts\":[{\"host\":\"172.20.14.135\",\"hostid\":\"12081\",\"name\":\"172.20.14.135\"}],\"value\":\"1\",\"object\":\"0\",\"objectid\":\"62407\",\"eventid\":\"11763576\",\"relatedObject\":{\"triggerid\":\"58835\",\"status\":\"0\",\"priority\":\"4\",\"description\":\"Изменился Health-статус IO-порта Enclosure ID 1, Controller A, Phy 0, PHY index 24, Type SC-1\",\"value\":\"1\"}}";
        JSONObject jsonEventFromZabbix = (JSONObject) JSON.parse(stringEventFromZabbix);

        Event eventFromJson = testCons.checkAlertInJsonAndCreateEvent(new String[]{"7", "8", "9"}, jsonEventFromZabbix);

        Assert.assertThat(eventFromJson.getHost(), CoreMatchers.is("172.20.14.135"));
        Assert.assertThat(eventFromJson.getCi(), CoreMatchers.is("Zabbix:172.20.14.135:MGMT-MODULE-A (МОДУЛИ VPLEX)"));
        Assert.assertThat(eventFromJson.getOrigin(), CoreMatchers.is("172.20.14.135"));
        Assert.assertThat(eventFromJson.getObject(), CoreMatchers.is("mgmt-module-a".toUpperCase()));
        Assert.assertThat(eventFromJson.getParametr(), CoreMatchers.is("62407"));
    }

    @Test
    public void testCiItemNaming7() throws Exception {

        ZabbixAPIComponent zabbixAPIComponent = new ZabbixAPIComponent();

        Processor processor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        };

        ZabbixAPIConsumer testCons;
        ZabbixAPIEndpoint zabbixAPIEndpoint = new ZabbixAPIEndpoint("", "", zabbixAPIComponent);
        ZabbixAPIConfiguration zabbixAPIConfiguration = new ZabbixAPIConfiguration();
        zabbixAPIConfiguration.setZabbixapiurl("http://172.20.19.195/zabbix/api_jsonrpc.php");
        zabbixAPIConfiguration.setUsername("Admin");
        zabbixAPIConfiguration.setPassword("zabbix");
        zabbixAPIConfiguration.setDelay(5);
        zabbixAPIConfiguration.setZabbixActionXmlNs("http://skuf.gosuslugi.ru/mon/");
        zabbixAPIConfiguration.setItemCiParentPattern("(.*)::(.*)");
        zabbixAPIConfiguration.setItemCiPattern("\\[(.*)\\](.*)");
        zabbixAPIConfiguration.setItemCiTypePattern("(.*)\\((.*)\\)");
        zabbixAPIConfiguration.setSource("Zabbix");
        zabbixAPIConfiguration.setZabbixtemplatepattern(".*--(.*)--.*");
        zabbixAPIConfiguration.setHostAliasPattern("(.*)--(.*)");
        zabbixAPIEndpoint.setConfiguration(zabbixAPIConfiguration);
        testCons = new ZabbixAPIConsumer(zabbixAPIEndpoint, processor);

        testCons.setZabbixApiFromSearchEvents(testCons.initZabbixApi());

        String stringEventFromZabbix = "{\"ns\":\"877750235\",\"source\":\"0\",\"clock\":\"1461881850\",\"alerts\":[{\"message\":\":echo '<ns:zabbixEvent xmlns:ns=\\\"http://skuf.gosuslugi.ru/mon/\\\" eventid=\\\"8587471\\\"><ns:date>2016.04.29</ns:date><ns:time>01:17:30</ns:time><ns:host>500a2230-a55a-0b0a-f151-c878e8cb58eb</ns:host><ns:triggername><![CDATA[VM backup status: more than 24 hours from latest backup]]></ns:triggername><ns:triggerid>36065</ns:triggerid><ns:status>PROBLEM</ns:status><ns:itemid>202391</ns:itemid><ns:value><![CDATA[1461795430]]></ns:value><ns:itemkey><![CDATA[vc.vm.config[vmconfig,backupTime2]]]></ns:itemkey><ns:itemkeyorig><![CDATA[vc.vm.config[vmconfig,backupTime2]]]></ns:itemkeyorig><ns:itemname><![CDATA[VM Config: backupTime (unixtime)]]></ns:itemname><ns:itemnameorig><![CDATA[VM Config: backupTime (unixtime)]]></ns:itemnameorig><ns:severity>Warning</ns:severity><ns:triggerurl></ns:triggerurl><ns:proxyname>Proxy: </ns:proxyname><ns:hostgroup>Virtual machines </ns:hostgroup><ns:template>Template Virt --VMware Guest-- additional config </ns:template><ns:metadescription><![CDATA[]]></ns:metadescription><ns:alias>{$MC_SMC_ALIAS}</ns:alias></ns:zabbixEvent>' > '/var/tmp/zbx/8587471.PROBLEM.xml'\",\"actionid\":\"7\",\"alertid\":\"252994\",\"mediatypes\":[],\"eventid\":\"8587471\"}],\"acknowledged\":\"0\",\"hosts\":[{\"host\":\"500a2230-a55a-0b0a-f151-c878e8cb58eb\",\"hostid\":\"11774\",\"name\":\"172.20.22.211--Tgc1-tms\"}],\"value\":\"1\",\"object\":\"0\",\"objectid\":\"36065\",\"eventid\":\"8587471\",\"relatedObject\":{\"triggerid\":\"36065\",\"status\":\"0\",\"priority\":\"2\",\"description\":\"VM backup status: more than 24 hours from latest backup\",\"value\":\"1\"}}";
        JSONObject jsonEventFromZabbix = (JSONObject) JSON.parse(stringEventFromZabbix);

        Event eventFromJson = testCons.checkAlertInJsonAndCreateEvent(new String[]{"7", "8", "9"}, jsonEventFromZabbix);

        Assert.assertThat(eventFromJson.getHost(), CoreMatchers.is("Tgc1-tms".toUpperCase()));
        Assert.assertThat(eventFromJson.getCi(), CoreMatchers.is("Zabbix:11774"));
        Assert.assertThat(eventFromJson.getOrigin(), CoreMatchers.is("500a2230-a55a-0b0a-f151-c878e8cb58eb"));
        Assert.assertThat(eventFromJson.getObject(), CoreMatchers.is("VM Config: backupTime (unixtime)".toUpperCase()));
        Assert.assertThat(eventFromJson.getParametr(), CoreMatchers.is("36065"));
    }
}