/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package test;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7;
import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
//import org.springframework.jms.connection.JmsTransactionManager;
//import javax.jms.ConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Bean
  private HL7MLLPNettyEncoderFactory hl7Encoder() {
    HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
    encoder.setCharset("iso-8859-1");
    //encoder.setConvertLFtoCR(true);
    return encoder;
  }

  @Bean
  private HL7MLLPNettyDecoderFactory hl7Decoder() {
    HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
    decoder.setCharset("iso-8859-1");
    return decoder;
  }

  @Bean
  private KafkaEndpoint kafkaEndpoint(){
    KafkaEndpoint kafkaEndpoint = new KafkaEndpoint();
    return kafkaEndpoint;
  }
  @Bean
  private KafkaComponent kafkaComponent(KafkaEndpoint kafkaEndpoint){
    KafkaComponent kafka = new KafkaComponent();
    return kafka;
  }

  @Bean
  ServletRegistrationBean camelServlet() {
    // use a @Bean to register the Camel servlet which we need to do
    // because we want to use the camel-servlet component for the Camel REST service
    ServletRegistrationBean mapping = new ServletRegistrationBean();
    mapping.setName("CamelServlet");
    mapping.setLoadOnStartup(1);
    mapping.setServlet(new CamelHttpTransportServlet());
    mapping.addUrlMappings("/camel/*");
    return mapping;
  }
  /*
   * Kafka implementation based upon https://camel.apache.org/components/latest/kafka-component.html
   * HL7 implementation based upon https://camel.apache.org/components/latest/dataformats/hl7-dataformat.html
   */
  @Override
  public void configure() throws Exception {

    /*
     * Audit
     *
     * Direct component within platform to ensure we can centralize logic
     * There are some values we will need to set within every route
     * We are doing this to ensure we dont need to build a series of beans
     * and we keep the processing as lightweight as possible
     *
     */

    // https://camel.apache.org/components/latest/kafka-component.html
    // String class and kafka
    // https://www.codota.com/code/java/methods/org.apache.camel.model.RouteDefinition/convertBodyTo
    from("direct:auditing")
         .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("messagetrigger").exchangeProperty("messagetrigger")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("auditdetails").exchangeProperty("auditdetails")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        .convertBodyTo(String.class).to("kafka://idaas-cluster-kafka-bootstrap:9092?topic=opsMgmt_PlatformTransactions&brokers=idaas-cluster-kafka-bootstrap:9092")
    ;
    /*
     *  Logging
     */
    from("direct:logging")
        .log(LoggingLevel.INFO, log, "Transaction: [${body}]")
    ;

    // MLLP Test Receiver
    from("netty4:tcp://0.0.0.0:10001?sync=true&decoder=#hl7Decoder&encoder=#hl7Encoder")
    //from("file:src/data-in?delete=true?noop=true")
          .routeId("hl7Admissions")
           // Added
          .convertBodyTo(String.class)
          .setProperty("bodyData").simple("${body}")
          .setProperty("processingtype").constant("data")
          .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
          .setProperty("industrystd").constant("HL7")
          .setProperty("messagetrigger").constant("ADT")
          .setProperty("componentname").simple("${routeId}")
          .setProperty("camelID").simple("${camelId}")
          .setProperty("exchangeID").simple("${exchangeId}")
          .setProperty("internalMsgID").simple("${id}")
          .setProperty("processname").constant("Input")
          .setProperty("auditdetails").constant("Transaction received")
          // iDAAS DataHub Processing
          .wireTap("direct:auditing")
          // Send to Topic
          .convertBodyTo(String.class).to("kafka://idaas-cluster-kafka-bootstrap:9092?topic=mctn-mms-adt&brokers=idaas-cluster-kafka-bootstrap:9092")
          //Response to HL7 Message Sent Built by platform
          .transform(HL7.ack())
          // This would enable persistence of the ACK
          .convertBodyTo(String.class)
          .setProperty("bodyData").simple("${body}")
          .setProperty("processingtype").constant("data")
          .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
          .setProperty("industrystd").constant("HL7")
          .setProperty("messagetrigger").constant("ADT")
          .setProperty("componentname").simple("${routeId}")
          .setProperty("camelID").simple("${camelId}")
          .setProperty("exchangeID").simple("${exchangeId}")
          .setProperty("internalMsgID").simple("${id}")
          .setProperty("processname").constant("Input")
          .setProperty("auditdetails").constant("ACK Processed")
          // iDAAS DataHub Processing
          .wireTap("direct:auditing")
   ;

    // FHIR
    from("servlet://condition?exchangePattern=InOut")
        //from("servlet://condition?servletName=CamelServlet")
        .routeId("FHIRCondition")
        // set Auditing Properties
        .convertBodyTo(String.class)
        .setProperty("bodyData").simple("${body}")
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Condition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        .convertBodyTo(String.class).to("kafka://idaas-cluster-kafka-bootstrap:9092?topic=fhirsvr-condition&brokers=idaas-cluster-kafka-bootstrap:9092")
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        .to("jetty:http://ibm-fhir-server.13.67.138.157.nip.io/fhir-server/api/v4/Condition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        .setProperty("bodyData").simple("${body}")
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("processname").constant("Response")
        .setProperty("auditdetails").constant("Condition response message received")
        .wireTap("direct:auditing")
    ;

   /*
    *   HCDD-EIP
    *   Healthcare Data Distribution Enterprise Integration Pattern
    *   HL7
    */

    from("kafka://idaas-cluster-kafka-bootstrap:9092?topic=mctn-mms-adt&brokers=idaas-cluster-kafka-bootstrap:9092")
            .routeId("ADT-MiddleTier")
            // Auditing
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("ADT")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("MTier")
            .setProperty("auditdetails").constant("ADT to Enterprise By Sending App By Data Type middle tier")
            .wireTap("direct:auditing")
            // Enterprise Message By Sending App By Type
            .to("kafka:MMS_ADT?brokers=idaas-cluster-kafka-bootstrap:9092")
            // Auditing
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("ADT")
            .setProperty("component").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("MTier")
            .setProperty("auditdetails").constant("ADT to Facility By Sending App By Data Type middle tier")
            .wireTap("direct:auditing")
            // Facility By Type
            .convertBodyTo(String.class).to("kafka://idaas-cluster-kafka-bootstrap:9092?topic=mctn-adt&brokers=idaas-cluster-kafka-bootstrap:9092")
            // Auditing
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("ADT")
            .setProperty("component").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("MTier")
            .setProperty("auditdetails").constant("ADT to Enterprise By Sending App By Data Type middle tier")
            .wireTap("direct:auditing")
            // Enterprise Message By Type
            .convertBodyTo(String.class).to("kafka://idaas-cluster-kafka-bootstrap:9092?topic=ent-adt&brokers=idaas-cluster-kafka-bootstrap:9092")
    ;
    /*
     *  HCDD-EIP
     *  FHIR
     *
     */
    from("kafka:idaas-cluster-kafka-bootstrap:9092?topic=fhirsvr-condition&brokers=idaas-cluster-kafka-bootstrap:9092")
        .routeId("Condition-MiddleTier")
        // Auditing
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("MTier")
        .setProperty("auditdetails").constant("Condition to Enterprise By Data Type middle tier")
        .wireTap("direct:auditing")
        // Enterprise Message By Type
        .to("kafka:idaas-cluster-kafka-bootstrap:9092?topic=ent-fhirsvr-condition&brokers=idaas-cluster-kafka-bootstrap:9092")
    ;
  }
}
