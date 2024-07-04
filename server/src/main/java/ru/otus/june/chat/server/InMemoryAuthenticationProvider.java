package ru.otus.june.chat.server;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAuthenticationProvider implements AuthenticationProvider {
  private class User {
    private String login;
    private String password;
    private String username;
    private Role userRole;

    public User(String login, String password, String username, String role) {
      this.login = login;
      this.password = password;
      this.username = username;
      for (Role r : Role.values()) {
        if (r.name().equalsIgnoreCase(role)) {
          this.userRole = r;
          break;
        }
      }
      if (this.userRole == null) {
        this.userRole = Role.USER;
      }
    }
  }

  private Server server;
  private List<User> users;

  public InMemoryAuthenticationProvider(Server server) {
    this.server = server;
    this.users = new ArrayList<>();
    this.users.add(new User("login1", "pass1", "bob", "ADMIN"));
    this.users.add(new User("login2", "pass2", "user2", "USER"));
    this.users.add(new User("login3", "pass3", "user3", "USER"));
  }

  @Override
  public void initialize() {
    System.out.println("Сервис аутентификации запущен: In-Memory режим");
  }

  private String getUsernameByLoginAndPassword(String login, String password) {
    for (User u : users) {
      if (u.login.equals(login) && u.password.equals(password)) {
        return u.username;
      }
    }
    return null;
  }

  private boolean isLoginAlreadyExits(String login) {
    for (User u : users) {
      if (u.login.equals(login)) {
        return true;
      }
    }
    return false;
  }

  private boolean isUsernameAlreadyExist(String username) {
    for (User u : users) {
      if (u.username.equals(username)) {
        return true;
      }
    }
    return false;
  }

  private Role getUserRoleByName(String name) {
    for (User u : users) {
      if (u.username.equals(name)) {
        return u.userRole;
      }
    }
    return null;
  }

  @Override
  public synchronized boolean authenticate(ClientHandler clientHandler, String login, String password) {
    String authUsername = getUsernameByLoginAndPassword(login, password);
    if (authUsername == null) {
      clientHandler.sendMessage("Некорректный логин/пароль");
      return false;
    }
    if (server.isUsernameBusy(authUsername)) {
      clientHandler.sendMessage("Указанная учетная запись уже занята");
      return false;
    }
    clientHandler.setUsername(authUsername);
    clientHandler.setUserRole(getUserRoleByName(authUsername));
    server.subscribe(clientHandler);
    clientHandler.sendMessage("/authok " + authUsername);
    return true;
  }

  @Override
  public synchronized boolean registration(ClientHandler clientHandler, String login, String password, String username) {
    if (login.trim().length() < 3 || password.trim().length() < 6 || username.trim().length() < 1) {
      clientHandler.sendMessage("Логин 3+ символа, Пароль 6+ символов, Имя пользователя 1+ символ");
      return false;
    }
    if (isLoginAlreadyExits(login)) {
      clientHandler.sendMessage("Указанный логин уже занят");
      return false;
    }
    if (isUsernameAlreadyExist(username)) {
      clientHandler.sendMessage("Указанное имя пользователя уже занято");
      return false;
    }
    users.add(new User(login, password, username, "USER"));
    clientHandler.setUsername(username);
    clientHandler.setUserRole(Role.USER);
    server.subscribe(clientHandler);
    clientHandler.sendMessage("/regok " + username);
    return true;
  }
}
