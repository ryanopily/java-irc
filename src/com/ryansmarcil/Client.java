package com.ryansmarcil;

import java.io.IOException;
import java.lang.StringBuilder;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/*
 * Minimal non-blocking event-based client for sending and receiving CRLF delimited messages over
 * the network
 *
 * @author ryansmarcil
 */
public class Client {
  /* Network objects */
  private SocketChannel serverChannel;
  private InetSocketAddress serverAddress;
  private StringBuilder messageBuffer; // Incoming message buffer
  private Queue<ByteBuffer> messageQueue; // Outgoing message queue

  /* Event listeners */
  public Consumer<Client> onConnect;
  public BiConsumer<Client, StringBuilder> onSend;
  public BiConsumer<Client, StringBuilder> onReceive;

  public Client() {
    this.messageBuffer = new StringBuilder();
    this.messageQueue = new LinkedList<>();
  }

  /*
   * Check whether the client has a connection
   *
   * @return the client's connection status
   */
  public boolean isConnected() {
    return this.serverChannel != null && this.serverChannel.isConnected();
  }

  /*
   * Specify the address of the server to connect to
   *
   * @param hostname the hostname of the target server
   * @param port the port of the target server
   */
  public void connectTo(String hostname, int port) {
    connectTo(new InetSocketAddress(hostname, port));
  }

  /*
   * Specify the address of the server to connect to
   *
   * @param serverAddress the address of the target server
   * @see InetSocketAddress
   */
  public void connectTo(InetSocketAddress serverAddress) {
    this.serverAddress = serverAddress;
  }

  /*
   * Connect to the server specified by Client::connectTo().
   * If the client is already connected to a server, it MUST
   * call Client::disconnect() before establishing a new connection
   *
   * @return the client's connection status
   * @exception IOException
   */
  public boolean connect() throws IOException {
    /* We need to request a socket */
    if (this.serverChannel == null || this.serverChannel.isOpen() == false) {
      this.serverChannel = SocketChannel.open();
      this.serverChannel.configureBlocking(false);
    }
    /* Attempt to establish a connection */
    boolean connectionEstablished;
    if (this.serverChannel.isConnectionPending()) {
      connectionEstablished = this.serverChannel.finishConnect();
    } else {
      connectionEstablished = this.serverChannel.connect(this.serverAddress);
    }
    /* Connection established! Propogate event */
    if (connectionEstablished && this.onConnect != null) {
      this.onConnect.accept(this);
    }
    return connectionEstablished;
  }

  /*
   * Disconnect from the server
   *
   * @exception IOException
   */
  public void disconnect() throws IOException {
    this.serverChannel.close();
  }

  /*
   * Send a message. The message will be queued even if the client is not
   * connected to a server. rawMessage will be passed to and may be modified by the
   * Client::onSend listener
   *
   * @param rawMessage raw message
   * @return whether rawMessage was successfully added to the queue
   */
  public boolean send(String rawMessage) {
    return send(new StringBuilder(rawMessage));
  }

  /*
   * Send a message. The message will be queued even if
   * the client is not connected to a server. rawMessage will be passed to and may be modified by
   * the Client::onSend listener
   *
   * @param rawMessage raw message
   * @return whether rawMessage was successfully added to the queue
   * @see StringBuilder
   */
  public boolean send(StringBuilder rawMessage) {
    if (this.onSend != null) {
      this.onSend.accept(this, rawMessage);
    }
    ByteBuffer wrappedMessage = ByteBuffer.allocate(rawMessage.length() + 2);
    wrappedMessage.put(rawMessage.toString().getBytes());
    wrappedMessage.put((byte) '\r');
    wrappedMessage.put((byte) '\n');
    wrappedMessage.flip();
    return this.messageQueue.offer(wrappedMessage);
  }

  /*
   * Receive a message. rawMessage will be passed to the
   * Client::onReceive listener
   *
   * @param rawMessage raw message
   */
  public void receive(String rawMessage) {
    receive(new StringBuilder(rawMessage));
  }

  /*
   * Receive a message. rawMessage will be passed to the Client::onReceive listener
   *
   * @param rawMessage raw message
   * @see StringBuilder
   */
  public void receive(StringBuilder rawMessage) {
    if (this.onReceive != null) {
      this.onReceive.accept(this, rawMessage);
    }
  }

  /*
   * Poll for onSend and onReceive events
   *
   * @exception IOException
   */
  public void pollEvents() throws IOException {
    this.read();
    this.write();
  }

  private char _read_lastByte = 0;
  private ByteBuffer _read_byteByffer = ByteBuffer.allocate(4096);

  /*
   * Read incoming data from the server
   *
   * @exception IOException
   */
  public void read() throws IOException {
    int bytesRead;
    while ((bytesRead = this.serverChannel.read(this._read_byteByffer)) > 0) {
      this._read_byteByffer.flip();
      while (this._read_byteByffer.hasRemaining()) {
        char currentByte = (char) this._read_byteByffer.get();
        if (currentByte == (int) '\n') {
          if (this._read_lastByte == '\r') {
            /* We have a complete message */
            this.receive(this.messageBuffer);
            this._read_lastByte = 0;
            this.messageBuffer = new StringBuilder();
            continue;
          }
        } else if (currentByte != (int) '\r') {
          /* We have a partial message */
          this.messageBuffer.append(currentByte);
        }
        this._read_lastByte = currentByte;
      }
      this._read_byteByffer.clear();
    }
    if (bytesRead == -1) {
      /* We got disconnected */
      this.disconnect();
    }
  }

  /*
   * Write outgoing data to the server
   *
   * @exception IOException
   */
  public void write() throws IOException {
    ByteBuffer outgoingMessage = this.messageQueue.peek();
    while (outgoingMessage != null) {
      int bytesWritten = this.serverChannel.write(outgoingMessage);
      if (outgoingMessage.hasRemaining() == false) {
        this.messageQueue.poll();
        outgoingMessage = this.messageQueue.peek();
      } else if (bytesWritten == 0) {
        return;
      }
    }
  }
}
