package com.ryansmarcil;

import java.io.IOException;
import java.lang.StringBuilder;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IRC {
  public static class Client {
    /* Network objects */
    private SocketChannel server;

    /* Event listeners */
    public Consumer<Client> onConnect;
    public Consumer<Client> onDisconnect;
    public BiConsumer<Client, String> onMessage;
    public BiConsumer<Client, String[]> onCommand;

    /*
     * Check connection status
     */
    public boolean isConnected() {
      return this.server != null && this.server.isOpen() && this.server.isConnected();
    }

    /*
     * Connect to an IRC server
     */
    public boolean connect(String hostname, int port) throws IOException {
      if (this.server == null || this.server.isOpen() == false
          || this.server.isConnected() == false) {
        this.server = SocketChannel.open();

        if (this.server.connect(new InetSocketAddress(hostname, port))) {
          this.server.configureBlocking(false);

          if (this.onConnect != null) {
            this.onConnect.accept(this);
          }

          return true;
        }
      }
      return false;
    }

    /*
     * Disconnect from an IRC server
     */
    public boolean disconnect() throws IOException {
      if (this.server != null && this.server.isOpen() && this.server.isConnected()) {
        this.server.configureBlocking(true);

        if (this.onDisconnect != null) {
          this.onDisconnect.accept(this);
        }

        this.server.close();
        return true;
      }
      return false;
    }

    /*
     * Poll for events
     */
    public void pollEvents() throws IOException {
      this.read();
      this.write();
    }

    /*
     * Send raw IRC message
     */
    public boolean send(String rawMessage) {
      ByteBuffer outPacket = ByteBuffer.allocate(rawMessage.length() + 2);
      outPacket.put(rawMessage.getBytes());
      outPacket.put("\r\n".getBytes());
      outPacket.flip();
      return outPacketQueue.offer(outPacket);
    }

    /*
     * Receive raw IRC message
     */
    public boolean receive(String rawMessage) {
      if (this.onMessage != null) {
        this.onMessage.accept(this, rawMessage);
      }

      if (this.onCommand != null) {
        // Parse raw message into IRC fields
        LinkedList<String> command = new LinkedList<>();
        command.add(null); // Prefix
        command.add(null); // Command

        int index = -1;
        // Check if message has a prefix
        if (rawMessage.indexOf(':') == 0) {
          index = rawMessage.indexOf(' ');
          if (index != -1) {
            // Found the prefix!
            command.set(0, rawMessage.substring(1, index));
            rawMessage = rawMessage.substring(index + 1);
          } else {
            // The message contains ONLY a prefix
            command.set(0, rawMessage.substring(1));
            rawMessage = rawMessage.substring(1);
          }
        }

        index = rawMessage.indexOf(' ');
        if (index != -1) {
          // Found the command name!
          command.set(1, rawMessage.substring(0, index));
          rawMessage = rawMessage.substring(index + 1);
        } else {
          // The message contains ONLY a command
          command.set(1, rawMessage);
          rawMessage = "";
        }

        String[] parameters;

        if (rawMessage.length() == 0) {
          // There are no parameters
          parameters = new String[0];
        } else {
          parameters = rawMessage.split(":");
        }

        // There are possibly parameters
        if (parameters.length > 0) {
          for (String parameter : parameters[0].split(" ")) {
            if (parameter.length() > 0) {
              command.add(parameter);
            }
          }
        }

        // There is a special final parameter
        if (parameters.length > 1) {
          String parameter = parameters[1];
          if (parameter.length() > 0) {
            command.add(parameter);
          }
        }

        this.onCommand.accept(this, command.toArray(new String[0]));
      }
      return true;
    }

    /* Read incoming data */

    private char inLastByte = 0;
    private StringBuilder inMessageBuffer = new StringBuilder();
    private ByteBuffer inPacketBuffer = ByteBuffer.allocate(4096);

    private void read() throws IOException {
      int bytesRead;
      while ((bytesRead = this.server.read(this.inPacketBuffer)) > 0) {
        this.inPacketBuffer.flip();

        while (this.inPacketBuffer.hasRemaining()) {
          char inCurrentByte = (char) this.inPacketBuffer.get();

          if (inCurrentByte == (int) '\n') {
            if (this.inLastByte == '\r') {
              this.receive(this.inMessageBuffer.toString());
              this.inLastByte = 0;
              this.inMessageBuffer = new StringBuilder();
              continue;
            }
          } else if (inCurrentByte != (int) '\r') {
            this.inMessageBuffer.append(inCurrentByte);
          }

          this.inLastByte = inCurrentByte;
        }

        this.inPacketBuffer.clear();
      }

      if (bytesRead == -1) {
        this.server.close();
      }
    }

    /* Write outgoing data */

    private LinkedList<ByteBuffer> outPacketQueue = new LinkedList<>();

    private void write() throws IOException {
      ByteBuffer outPacket = outPacketQueue.peek();

      while (outPacket != null) {
        if (this.server.write(outPacket) == 0) {
          if (outPacket.hasRemaining()) {
            return;
          } else {
            outPacketQueue.poll();
            outPacket = outPacketQueue.peek();
          }
        }
      }
    }
  }
}
