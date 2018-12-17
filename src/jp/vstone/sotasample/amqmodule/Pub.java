package jp.vstone.sotasample.amqmodule;


import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

public final class Pub {

  public static void main(final String[] args) throws Exception {
    final ConnectionFactory connFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
    final Connection conn = connFactory.createConnection();
    final Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
    final Destination dest = sess.createTopic("Speech");
    final MessageProducer prod = sess.createProducer(dest);
    final TextMessage msg = sess.createTextMessage("{ \"RobotId\":\"R00\", \"Content\":\"こんにちは\"}");
    prod.send(msg);

    conn.close();
  }

}