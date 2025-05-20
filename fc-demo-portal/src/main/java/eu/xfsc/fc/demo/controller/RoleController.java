package eu.xfsc.fc.demo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.client.RoleClient;

/**
 * Roles API Controller.
 */
@RestController
@RequestMapping("roles")
public class RoleController {

  @Autowired
  private RoleClient roleClient;

  /**
   * GET /roles : Get all registered roles in the catalogue.
   *
   * @return All roles (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or May contain hints how to solve the error or indicate what went wrong at the server. (status code 500)
   */
  @GetMapping
  public List<String> getAllRoles() {
    return roleClient.getAllRoles(0, 0);
  }
}
