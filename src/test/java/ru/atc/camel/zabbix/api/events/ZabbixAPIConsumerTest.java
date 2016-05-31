package ru.atc.camel.zabbix.api.events;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertThat(returnCiArray[0], CoreMatchers.is("4e64165c8c5c97dd7d12ee933779c4af047dd520"));

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
}