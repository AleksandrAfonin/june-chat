package ru.otus.june.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler {
  private Server server;
  private Socket socket;
  private DataInputStream in;
  private DataOutputStream out;

  private String username;
  private boolean inChat;
  private List<Role> userRoles;

  public void setInChat(boolean inChat) {
    this.inChat = inChat;
  }

  public void setUserRole(Role userRole) {
    this.userRoles.add(userRole);
  }

  public void setUserRoles(List<Role> roles) {
    this.userRoles = roles;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public ClientHandler(Server server, Socket socket) throws IOException {
    this.server = server;
    this.socket = socket;
    this.in = new DataInputStream(socket.getInputStream());
    this.out = new DataOutputStream(socket.getOutputStream());
    this.inChat = true;
    this.userRoles = new ArrayList<>();
    new Thread(() -> {
      try {
        System.out.println("Подключился новый клиент");
        while (true) {
          String message = in.readUTF();
          if (message.equals("/exit")) {
            sendMessage("/exitok");
            return;
          }
          if (message.startsWith("/auth ")) {
            String[] elements = message.split(" ");
            if (elements.length != 3) {
              sendMessage("Не верный формат команды /auth");
              continue;
            }
            if (server.getAuthenticationProvider().authenticate(this, elements[1], elements[2])) {
              break;
            }
            continue;
          }
          if (message.startsWith("/register ")) {
            String[] elements = message.split(" ");
            if (elements.length != 4) {
              sendMessage("Не верный формат команды /register");
              continue;
            }
            if (server.getAuthenticationProvider().registration(this, elements[1], elements[2], elements[3])) {
              break;
            }
            continue;
          }
          sendMessage("Перед работой с чатом необходимо выполнить аутентификацию '/auth login password' или регистрацию '/register login password username'");
        }
        while (true) {
          String message = in.readUTF();
          if (message.startsWith("/")) {
            if (message.equals("/exit")) {
              sendMessage("/exitok");
              break;
            }
            if (inChat) {
              if (message.startsWith("/w ")) {
                server.sendPrivateMessage(this, message);
              }
              if (message.startsWith("/kick ")) {
                if (isHaveRole(userRoles, Role.ADMIN)) {
                  String[] elements = message.split(" ");
                  if (elements.length != 2) {
                    sendMessage("Не верный формат команды /kick");
                    continue;
                  }
                  server.handleKick(this, elements[1]);
                } else {
                  sendMessage("У вас не достоточно прав для команды /kick");
                  continue;
                }
              }
            }
            continue;
          }
          if (inChat) {
            server.broadcastMessage(username + ": " + message);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        disconnect();
      }
    }).start();
  }

  private boolean isHaveRole(List<Role> userRoles, Role role) {
    for (Role r : userRoles) {
      if (r == role) {
        return true;
      }
    }
    return false;
  }

  public void sendMessage(String message) {
    if (message.equals("/exitok") || inChat) {
      try {
        out.writeUTF(message);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void disconnect() {
    server.unsubscribe(this);
    try {
      if (in != null) {
        in.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      if (out != null) {
        out.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


}
