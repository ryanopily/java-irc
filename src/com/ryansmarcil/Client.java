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
 * Minimal non-blocking event-based client for interacting with IRC servers. The client has no
 * knowledge of the IRC protocol beyond sending and receiving arbitrary messages over the network
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
   * Check whether the client has a pending connection
   *
   * @return the client's connection status
   */
  public boolean isConnectionPending() {
    return this.serverChannel != null && this.serverChannel.isConnectionPending();
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
   * Connect to an IRC server. The server address MUST be specified with Client::connectTo()
   * before calling this method. If the client is already connected to a server, it MUST
   * disconnect before establishing a new connection
   *
   * @return the client's connection status
   * @exception IOException
   */
  public boolean connect() throws IOException {
    /* We're already connected or we don't know where to connect to*/
    if (this.isConnected()) {
      return true;
    }
    if (this.serverAddress == null) {
      return false;
    }
    /* We need to request a socket */
    if (this.serverChannel == null || this.serverChannel.isOpen() == false) {
      this.serverChannel = SocketChannel.open();
      this.serverChannel.configureBlocking(false);
    }
    /* Attempt to establish a connection */
    boolean connectionEstablished;
    if (this.isConnectionPending()) {
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
   * Disconnect the client from the server
   *
   * @exception IOException
   */
  public void disconnect() throws IOException {
    this.serverChannel.close();
  }

  /*
   * Enqueue an outgoing raw IRC message. The message will be queued even if the client is not
   * connected to any server. rawMessage will be wrapped in a StringBuilder and passed to the
   * Client::onSend listener
   *
   * @param rawMessage raw IRC message
   * @return whether rawMessage was successfully added to the queue
   * @see StringBuilder
   */
  public boolean send(String rawMessage) {
    return send(new StringBuilder(rawMessage));
  }

  /*
   * Enqueue an outgoing raw IRC message. The message will be queued even if
   * the client is not connected to any server. rawMessage will be passed to the Client::onSend
   * listener
   *
   * @param rawMessage raw IRC message
   * @return whether rawMessage was successfully added to the queue
   * @see StringBuilder
   */
  public boolean send(StringBuilder rawMessage) {
    if (this.onSend != null) {
      this.onSend.accept(this, rawMessage);
    }
    rawMessage.append("\r\n");
    ByteBuffer wrappedMessage = ByteBuffer.wrap(rawMessage.toString().getBytes());
    return this.messageQueue.offer(wrappedMessage);
  }

  /*
   * Enqueue an outgoing raw IRC message. The message will be queued even if the client is not
   * connected to any server. rawMessage will NOT be passed to the Client::onSend listener
   *
   * @param rawMessage raw IRC message
   * @return whether rawMessage was successfully added to the queue
   */
  public boolean sendUnmodifiable(String rawMessage) {
    ByteBuffer wrappedMessage = ByteBuffer.wrap((rawMessage + "\r\n").getBytes());
    return this.messageQueue.offer(wrappedMessage);
  }

  /*
   * Receive a raw IRC message. rawMessage will be wrapped in a StringBuilder and passed to the
   * Client::onReceive listener
   *
   * @param rawMessage raw IRC message
   * @return whether rawMessage was processed
   * @see StringBuilder
   */
  public boolean receive(String rawMessage) {
    return receive(new StringBuilder(rawMessage));
  }

  /*
   * Receive a raw IRC message. rawMessage will be passed to the Client::onReceive listener
   *
   * @param rawMessage raw IRC message
   * @return whether rawMessage was processed
   * @see StringBuilder
   */
  public boolean receive(StringBuilder rawMessage) {
    if (this.onReceive != null) {
      this.onReceive.accept(this, rawMessage);
      return true;
    }
    return false;
  }

  /*
   * Internally calls Client::read() and Client::write()
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
   * Read incoming data from the connected server
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
      this.serverChannel.close();
    }
  }

  /*
   * Write outgoing data to the connected server
   *
   * @exception IOException
   */
  public void write() throws IOException {
    ByteBuffer outgoingMessage = this.messageQueue.peek();
    while (outgoingMessage != null) {
      if (this.serverChannel.write(outgoingMessage) == 0) {
        if (outgoingMessage.hasRemaining()) {
          return;
        } else {
          this.messageQueue.poll();
          outgoingMessage = this.messageQueue.peek();
        }
      }
    }
  }
}
