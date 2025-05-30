/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.cli.commands;

import java.util.List;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.cli.CommandContext;
import org.apache.gravitino.cli.ErrorMessages;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.exceptions.NoSuchMetalakeException;
import org.apache.gravitino.exceptions.NoSuchUserException;

public class RoleDetails extends Command {

  protected String metalake;
  protected String role;

  /**
   * Displays the securable objects in a role.
   *
   * @param context The command context.
   * @param metalake The name of the metalake.
   * @param role The name of the role.
   */
  public RoleDetails(CommandContext context, String metalake, String role) {
    super(context);
    this.metalake = metalake;
    this.role = role;
  }

  /** Displays the securable objects of a specified role. */
  @Override
  public void handle() {
    List<SecurableObject> objects = null;

    try {
      GravitinoClient client = buildClient(metalake);
      objects = client.getRole(role).securableObjects();
    } catch (NoSuchMetalakeException err) {
      exitWithError(ErrorMessages.UNKNOWN_METALAKE);
    } catch (NoSuchUserException err) {
      exitWithError(ErrorMessages.UNKNOWN_GROUP);
    } catch (Exception exp) {
      exitWithError(exp.getMessage());
    }

    if (objects == null || objects.isEmpty()) {
      printInformation("No securable objects found for role: " + role);
    } else {
      printResults(objects);
    }
  }
}
