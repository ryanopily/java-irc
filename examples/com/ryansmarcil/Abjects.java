package com.ryansmarcil;

import java.io.IOException;

public class Abjects {
  public static void main(String[] args) throws IOException {
    Client client = new Client();

    /* Called when the client connects to the server for the first time */
    client.onConnect = (irc) -> {
      irc.sendUnmodifiable("USER johndoe * * :John Doe");
      irc.sendUnmodifiable("NICK johndoe");
    };

    /* Called when the client receives a message */
    client.onReceive = (irc, message) -> {
      /* Respond to server pings to keep client connected */
      if (message.substring(0, 4).equalsIgnoreCase("PING")) {
        irc.sendUnmodifiable(message.toString().replaceFirst("PING", "PONG"));
      }
      /* Print messages to stdout*/
      System.out.println(message);
    };

    /* Set the target server address */
    client.connectTo("irc.abjects.net", 6667);

    /* Attempt to connect */
    while (client.isConnected() == false) {
      client.connect();
    }

    /* Connected! */
    while (client.isConnected() == true) {
      /* Attempt to read/write */
      client.pollEvents();

      /* Get user input */
      int available = System.in.available();
      if (available > 0) {
        byte[] data = new byte[available];
        System.in.read(data);
        client.send(new String(data));
      }
    }
  }
}
