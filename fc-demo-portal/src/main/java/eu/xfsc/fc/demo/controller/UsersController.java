package eu.xfsc.fc.demo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.api.generated.model.User;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.api.generated.model.UserProfiles;
import eu.xfsc.fc.client.UserClient;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Users API Controller.
 */
@RestController
@RequestMapping("users")
public class UsersController {

  @Autowired
  private UserClient userClient;

  /**
   * POST /users : Register a new user to the associated participant in the catalogue.
   *
   * @param user User profile (optional)
   * @return Created User profile (status code 201)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or HTTP Conflict 409 (status code 409)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @PostMapping
  public UserProfile addUser(@RequestBody(required = false) User user) {
    return userClient.addUser(user);
  }

  /**
   * PUT /users/{userId} : Update the user profile.
   *
   * @param id (required)
   * @param user User profile (optional)
   * @return Updated user profile (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @PutMapping("/{userId}")
  public UserProfile updateUser(@PathVariable("userId") String id,
                                                @RequestBody(required = false) User user) {
    return userClient.updateUser(id, user);
  }

  /**
   * DELETE /users/{userId} : Delete a user.
   *
   * @param id (required)
   * @return Deleted user profile (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or HTTP Conflict 409 (status code 409)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @DeleteMapping("/{userId}")
  public UserProfile deleteUser(@PathVariable("userId") String id) {
    return userClient.deleteUser(id);
  }

  /**
   * GET /users/{userId} : Get the user profile.
   *
   * @param userId (required)
   * @return User profile (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @GetMapping("/{userId}")
  public UserProfile getUser(HttpServletRequest request, @PathVariable("userId") String userId) {
    return userClient.getUser(userId);
  }

  /**
   * GET /users : List the registered users.
   *
   * @param offset The number of items to skip before starting to collect the result set. (optional, default to 0)
   * @param limit The number of items to return. (optional, default to 100)
   * @return List of usernames (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @GetMapping
  public UserProfiles getUsers(
      @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
    return userClient.getUsers(offset, limit);
  }

  /**
   * GET /users/{userId}/roles : Get roles of the user.
   *
   * @param id (required)
   * @return User roles (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @GetMapping("/{userId}/roles")
  public List<String> getUserRoles(HttpServletRequest request, @PathVariable("userId") String id) {
    return userClient.getUserRoles(id);
  }

  /**
   * PUT /users/{userId}/roles : Update roles of the user.
   *
   * @param id (required)
   * @param roles List of roles which should be assigned to the user (optional)
   * @return All assigned roles of the user (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @PutMapping("/{userId}/roles")
  public List<String> updateUserRoles(HttpServletRequest request, @PathVariable("userId") String id,
                                                      @RequestBody(required = false) List<String> roles) {
    return userClient.updateUserRoles(id, roles);
  }
}
