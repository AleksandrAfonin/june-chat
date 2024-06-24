package ru.otus.june.chat.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private int port;
    private List<ClientHandler> clients;

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту: " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                subscribe(new ClientHandler(this, socket));
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
        broadcastMessage("Из чата вышел: " + clientHandler.getUsername());
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public synchronized void broadcastMessageToUser(ClientHandler ch, String message) {
        int index1 = message.indexOf(' ');
        if (index1 < 0){
            return;
        }
        index1++;
        int index2 = message.indexOf(' ', index1);
        if(index2 < 0){
            return;
        }
        String name = message.substring(index1, index2++);
        String mess = message.substring(index2);
        if(name.isEmpty() || mess.isEmpty()){
            return;
        }
        mess = ch.getUsername() + ": " + mess;
        for (ClientHandler c : clients) {
            if (c.getUsername().equals(name)) {
                c.sendMessage(mess);
                ch.sendMessage(mess);
                break;
            }
        }
    }
}
