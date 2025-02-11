package com.ryansmarcil;

import java.io.IOException;

public class Abjects {
  public static void main(String[] args) throws IOException {
    IRC.Client client = new IRC.Client();

    client.onConnect = (_client) -> {
      /* Register client with the server */
      _client.send("USER doejohn * * :John Doe");
      _client.send("NICK doejohn");
    };

    client.onMessage = (_client, message) -> {
      /* Write raw irc messages to stdout */
      System.out.println(message);
    };

    client.onCommand = (_client, command) -> {
      /* Respond to PING commands to keep connection alive */
      if (command[1] != null && command[1].equalsIgnoreCase("PING") && command.length > 2) {
        _client.send(String.format("PONG :%s", command[2]));
      }
    };

    if (client.connect("irc.abjects.net", 6667)) {
      while (client.isConnected()) {
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
}
