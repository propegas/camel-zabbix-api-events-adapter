package ru.atc.camel.zabbix.api.events;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.at_consulting.itsm.event.Event;

import javax.jms.ConnectionFactory;
import java.io.File;

//import ru.at_consulting.itsm.device.Device;

//import java.io.File;
//import org.apache.camel.processor.idempotent.FileIdempotentRepository;

public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int CACHE_SIZE = 2500;
    private static final int MAX_FILE_SIZE = 512000;

    private static String activemq_port;
    private static String activemq_ip;
    //private static String source;

    private Main() {

    }

    public static void main(String[] args) throws Exception {

        logger.info("Starting Custom Apache Camel component example");
        logger.info("Press CTRL+C to terminate the JVM");

        if (args.length == 2) {
            activemq_port = args[1];
            activemq_ip = args[0];
        }

        if (activemq_port == null || "".equals(activemq_port))
            activemq_port = "61616";
        if (activemq_ip == null || "".equals(activemq_ip))
            activemq_ip = "172.20.19.195";

        logger.info("activemq_ip: " + activemq_ip);
        logger.info("activemq_port: " + activemq_port);

        org.apache.camel.main.Main main = new org.apache.camel.main.Main();
        //main.enableHangupSupport();

        main.addRouteBuilder(new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                JsonDataFormat myJson = new JsonDataFormat();
                myJson.setPrettyPrint(true);
                myJson.setLibrary(JsonLibrary.Jackson);
                myJson.setAllowJmsType(true);
                myJson.setUnmarshalType(Event.class);
                //myJson.setJsonView(Event.class);

                //myJson.setPrettyPrint(true);

                PropertiesComponent properties = new PropertiesComponent();
                properties.setLocation("classpath:zabbixapi.properties");
                getContext().addComponent("properties", properties);

                ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                        "tcp://" + activemq_ip + ":" + activemq_port
                );
                getContext().addComponent("activemq", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

                File cachefile = new File("sendedEvents.dat");
                //cachefile.createNewFile();

                // Heartbeats
                from("timer://foo?period={{heartbeatsdelay}}")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                ZabbixAPIConsumer.genHeartbeatMessage(exchange);
                            }
                        })
                        //.bean(WsdlNNMConsumer.class, "genHeartbeatMessage", exchange)
                        .marshal(myJson)
                        .to("activemq:{{heartbeatsqueue}}")
                        .log("*** Heartbeat: ${id}");

                from(new StringBuilder().append("zabbixapi://events?")
                        .append("delay={{delay}}&")
                        .append("zabbixapiurl={{zabbixapiurl}}&")
                        .append("username={{username}}&")
                        .append("password={{password}}&")
                        .append("adaptername={{adaptername}}&")
                        .append("source={{source}}&")
                        .append("lasteventid={{lasteventid}}&")
                        .append("zabbixactionprefix={{zabbixactionprefix}}&")
                        .append("zabbixActionXmlNs={{zabbixactionxmlns}}&")
                        .append("zabbixtemplatepattern={{zabbixtemplatepattern}}&")
                        .append("groupCiPattern={{zabbix_group_ci_pattern}}&")
                        .append("groupSearchPattern={{zabbix_group_search_pattern}}&")
                        .append("itemCiPattern={{zabbix_item_ke_pattern}}&")
                        .append("itemCiParentPattern={{zabbix_item_ci_parent_pattern}}&")
                        .append("maxEventsPerRequest={{maxEventsPerRequest}}&")
                        .append("zabbixip={{zabbixip}}")
                        .toString())

                        .choice()
                        .when(header("Type").isEqualTo("Error"))
                        .marshal(myJson)
                        .to("activemq:{{eventsqueue}}")
                        .log("Error: ${id} ${header.EventUniqId}")

                        .otherwise()
                        .idempotentConsumer(
                                header("EventUniqId"),
                                FileIdempotentRepository.fileIdempotentRepository(
                                        cachefile, CACHE_SIZE, MAX_FILE_SIZE
                                )
                        )

                        .marshal(myJson)
                        .to("activemq:{{eventsqueue}}")
                        .log("*** NEW EVENT: ${id} ${header.EventIdAndStatus}");
            }
        });

        main.run();
    }
}