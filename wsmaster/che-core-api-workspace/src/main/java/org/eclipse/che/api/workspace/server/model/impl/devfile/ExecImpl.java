/*
 * Copyright (c) 2012-2020 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.workspace.server.model.impl.devfile;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import org.eclipse.che.api.core.model.workspace.devfile.Exec;

@Entity(name = "DevfileExec")
@Table(name = "devfile_exec")
public class ExecImpl implements Exec {

  @Id
  @GeneratedValue
  @Column(name = "id")
  private Long generatedId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "devfile_exec_command",
      joinColumns = @JoinColumn(name = "devfile_exec_id"))
  @Column(name = "command")
  private List<String> command;


  @Override
  public List<String> getCommand() {
    if (command == null) {
      command = new ArrayList<>();
    }
    return command;
  }

  public void setCommand(List<String> command) {
    this.command = command;
  }
}
