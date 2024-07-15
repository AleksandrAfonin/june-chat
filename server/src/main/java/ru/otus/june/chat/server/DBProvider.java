package ru.otus.june.chat.server;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class DBProvider implements AuthenticationProvider {
  private Server server;

  private final String DATABASE_URL = "jdbc:sqlite:databases/users.db";
  private final String USERNAME_BY_LOGIN_AND_PASSWORD = "SELECT username FROM users WHERE login = ? AND password = ?";
  private final String IS_LOGIN_EXISTS = "SELECT login FROM users WHERE login = ?";
  private final String IS_USERNAME_EXISTS = "SELECT username FROM users WHERE username = ?";
  private final String GET_USERID_BY_NAME = "SELECT id FROM users WHERE username = ?";
  private final String GET_ROLEID_BY_ROLENAME = "SELECT id FROM roles WHERE role = ?";
  private final String GET_USER_ROLES_BY_NAME = """
          SELECT roles.role FROM roles, roles_to_users, users WHERE
           roles_to_users.id_role = roles.id AND
           roles_to_users.id_user = users.id AND
           users.username = ?
          """;
  private final String ADD_NEW_USER = """
          INSERT INTO users
           (login, password, username, date, isdeleted)
           VALUES (?, ?, ?, ?, ?)
           """;

  private final String ADD_ROLE_TO_USER = """
          INSERT INTO roles_to_users
           (id_user, id_role)
           VALUES (?, ?)
           """;

  public DBProvider(Server server) {
    this.server = server;
  }

//  public DBProvider() {
//  }

  @Override
  public void initialize() {
    System.out.println("Сервис аутентификации запущен: JDBC режим");
  }

  @Override
  public boolean authenticate(ClientHandler clientHandler, String login, String password) {
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
    clientHandler.setUserRoles(getUserRolesByName(authUsername));
    server.subscribe(clientHandler);
    clientHandler.sendMessage("/authok " + authUsername);
    return true;
  }

  @Override
  public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
    if (login.trim().length() < 3 || password.trim().length() < 6 || username.trim().length() < 1) {
      clientHandler.sendMessage("Логин 3+ символа, Пароль 6+ символов, Имя пользователя 1+ символ");
      return false;
    }
    if (isLoginAlreadyExits(login)) {
      clientHandler.sendMessage("Указанный логин уже занят");
      return false;
    }
    if (isUsernameAlreadyExists(username)) {
      clientHandler.sendMessage("Указанное имя пользователя уже занято");
      return false;
    }

    if (addNewUser(login, password, username)) {
      clientHandler.setUsername(username);
      clientHandler.setUserRole(Role.USER);
      server.subscribe(clientHandler);
      clientHandler.sendMessage("/regok " + username);
      return true;
    }
    return false;
  }

  public boolean addNewUser(String login, String password, String username) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(ADD_NEW_USER)) {
        statement.setString(1, login);
        statement.setString(2, password);
        statement.setString(3, username);
        statement.setString(4, (new SimpleDateFormat("dd.MM.YYYY")).format(new Date(System.currentTimeMillis())));
        statement.setBoolean(5, false);
        if (statement.executeUpdate() != 0) {
          System.out.println("The new user added !");
        } else {
          return false;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return addRoleToUserByUsername(username, Role.USER);
  }

  public boolean addRoleToUserByUsername(String username, Role role) {
    int id_user;
    if ((id_user = getUserIdByName(username)) < 0){
      System.out.println("No such user with name: " + username);
      return false;
    }
    int id_role;
    if ((id_role = getRoleIdByRole(role)) < 0){
      System.out.println("No such role with name: " + role.name());
      return false;
    }

    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(ADD_ROLE_TO_USER)) {
        statement.setInt(1, id_user);
        statement.setInt(2, id_role);
        if (statement.executeUpdate() != 0) {
          System.out.println("The new role added to user " + username);
          return true;
        } else {
          return false;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private int getRoleIdByRole(Role role) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(GET_ROLEID_BY_ROLENAME)) {
        statement.setString(1, role.name());
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            int id_role = resultSet.getInt("id");
            return id_role;
          } else {
            System.out.println("No such role");
            return -1;
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private int getUserIdByName(String username) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(GET_USERID_BY_NAME)) {
        statement.setString(1, username);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            int id_user = resultSet.getInt("id");
            return id_user;
          } else {
            System.out.println("No such user: " + username);
            return -1;
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private String getUsernameByLoginAndPassword(String login, String password) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(USERNAME_BY_LOGIN_AND_PASSWORD)) {
        statement.setString(1, login);
        statement.setString(2, password);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            String username = resultSet.getString("username");
            System.out.println("Username: " + username);
            return username;
          } else {
            System.out.println("No such user");
            return null;
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isLoginAlreadyExits(String login) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(IS_LOGIN_EXISTS)) {
        statement.setString(1, login);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            String l = resultSet.getString("login");
            System.out.println("Login Already Exits: " + l);
            return true;
          } else {
            System.out.println("No such login");
            return false;
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isUsernameAlreadyExists(String username) {
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(IS_USERNAME_EXISTS)) {
        statement.setString(1, username);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            String u = resultSet.getString("username");
            System.out.println("Username Already Exits: " + u);
            return true;
          } else {
            System.out.println("No such username");
            return false;
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private List<Role> getUserRolesByName(String name) {
    List<Role> roles = new ArrayList<>();
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
      try (PreparedStatement statement = connection.prepareStatement(GET_USER_ROLES_BY_NAME)) {
        statement.setString(1, name);
        try (ResultSet resultSet = statement.executeQuery()) {
          while (resultSet.next()) {
            String role = resultSet.getString("role");
            for (Role r : Role.values()) {
              if (r.name().equalsIgnoreCase(role)) {
                roles.add(r);
                break;
              }
            }
          }
          return roles;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}

