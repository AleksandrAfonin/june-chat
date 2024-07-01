package ru.otus.june.chat.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
  private int port;
  private List<ClientHandler> clients;
  private AuthenticationProvider authenticationProvider;

  public Server(int port) {
    this.port = port;
    this.clients = new ArrayList<>();
    this.authenticationProvider = new InMemoryAuthenticationProvider(this);
  }

  public AuthenticationProvider getAuthenticationProvider() {
    return authenticationProvider;
  }

  public void start() {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Сервер запущен на порту: " + port);
      authenticationProvider.initialize();
      while (true) {
        Socket socket = serverSocket.accept();
        new ClientHandler(this, socket);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public synchronized void subscribe(ClientHandler clientHandler) {
    broadcastMessage("В чат зашел: " + clientHandler.getUsername());
    clients.add(clientHandler);
  }

  public synchronized void unsubscribe(ClientHandler clientHandler) {
    clients.remove(clientHandler);
    if (clientHandler.getUsername() == null){
      return;
    }
    broadcastMessage("Из чата вышел: " + clientHandler.getUsername());
  }

  public synchronized void broadcastMessage(String message) {
    for (ClientHandler c : clients) {
      c.sendMessage(message);
    }
  }

  public synchronized void sendPrivateMessage(ClientHandler ch, String message) {
    String[] str = message.split(" ", 3);
    if (str.length < 3) {
      return;
    }

    String mess = ch.getUsername() + " -> " + str[1] + ": " + str[2];
    for (ClientHandler c : clients) {
      if (c.getUsername().equals(str[1])) {
        c.sendMessage(mess);
        ch.sendMessage(mess);
        break;
      }
    }
  }

  public synchronized boolean isUsernameBusy(String username){
    for(ClientHandler c : clients){
      if(c.getUsername().equals(username)){
        return true;
      }
    }
    return false;
  }
}
