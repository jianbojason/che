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
import org.eclipse.che.api.core.model.workspace.devfile.Handler;
import org.eclipse.che.api.core.model.workspace.devfile.Lifecycle;

@Entity(name = "DevfileLifecycle")
@Table(name = "devfile_lifecycle")
public class LifecycleImpl implements Lifecycle {

  @Id
  @GeneratedValue
  @Column(name = "id")
  private Long generatedId;

  @Embedded private Handler preStop;
  @Embedded private Handler postStart;

  @Override
  public Handler getPreStop() {
    return preStop;
  }

  public void setPreStop(Handler preStop) {
    this.preStop = preStop;
  }

  @Override
  public Handler getPostStart() {
    return postStart;
  }

  public void setPostStart(Handler postStart) {
    this.postStart = postStart;
  }
}
