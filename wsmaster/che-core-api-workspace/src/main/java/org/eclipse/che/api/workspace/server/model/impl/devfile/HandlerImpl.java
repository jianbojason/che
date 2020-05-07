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

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.eclipse.che.api.core.model.workspace.devfile.Exec;
import org.eclipse.che.api.core.model.workspace.devfile.Handler;

@Entity(name = "DevfileHandler")
@Table(name = "devfile_handler")
public class HandlerImpl implements Handler {

  @Id
  @GeneratedValue
  @Column(name = "id")
  private Long generatedId;

  @Embedded private Exec exec;

  @Override
  public Exec getExec() {
    return exec;
  }

  public void setExec(Exec exec) {
    this.exec = exec;
  }
}
