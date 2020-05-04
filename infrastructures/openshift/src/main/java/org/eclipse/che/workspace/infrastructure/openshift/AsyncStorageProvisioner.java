/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.openshift;

import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.google.common.base.Predicate;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.ssh.server.SshManager;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.shared.model.SshPair;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncStorageProvisioner {

  private static final int SERVICE_PORT = 2222;
  private static final String SSH_KEY_NAME = "rsync-via-ssh";
  private static final String CONFIG_MAP_VOLUME_NAME = "async-storage-configvolume";
  private static final String ASYNC_STORAGE_CONFIG = "async-storage-config";
  private static final String AUTHORIZED_KEYS = "authorized_keys";
  private static final String SSH_KEY_PATH = "/.ssh/" + AUTHORIZED_KEYS;
  private static final String STORAGE = "storage";
  private static final String STORAGE_DATA_PATH = "/var/lib/storage/data/";
  private static final String STORAGE_DATA = "storage-data";

  private static final Logger LOG = LoggerFactory.getLogger(AsyncStorageProvisioner.class);

  private final String     pvcName;
  private final String     storageImage;
  private final SshManager sshManager;
  private final OpenShiftClientFactory clientFactory;

  @Inject
  public AsyncStorageProvisioner(
      @Named("che.infra.kubernetes.pvc.name") String pvcName,
      @Named("che.infra.kubernetes.async.storage.image") String image,
      SshManager sshManager,
      OpenShiftClientFactory openShiftClientFactory) {
    this.pvcName = pvcName;
    this.storageImage = image;
    this.sshManager = sshManager;
    this.clientFactory = openShiftClientFactory;
  }

  public void provision(RuntimeIdentity identity) throws InfrastructureException {
    List<SshPairImpl> sshPairs = getOrCreateSshPairs(identity);
    if (sshPairs == null) {
      return;
    }
    SshPair sshPair = sshPairs.get(0);

    String namespace = identity.getInfrastructureNamespace();
    KubernetesClient oc = clientFactory.create(identity.getWorkspaceId());

    String configMapName = namespace + ASYNC_STORAGE_CONFIG;

    boolean isMapExist =
        oc.configMaps()
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .anyMatch(
                (Predicate<ConfigMap>)
                    configMap -> configMap.getMetadata().getName().equals(configMapName));

    if (!isMapExist) {
      ConfigMap configMap = createConfigMap(namespace, configMapName, sshPair);
      oc.configMaps().inNamespace(namespace).create(configMap);
    }

    boolean isPodExist =
        oc.pods()
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .anyMatch((Predicate<Pod>) pod -> pod.getMetadata().getName().equals(STORAGE));

    if (!isPodExist) {
      Pod pod = createStoragePod(namespace, configMapName);
      oc.pods().inNamespace(namespace).create(pod);
    }

    boolean isServiceExist =
        oc.services()
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .anyMatch(
                (Predicate<Service>) service -> service.getMetadata().getName().equals(STORAGE));

    if (!isServiceExist) {
      Service service = createStorageService(namespace);
      oc.services().inNamespace(namespace).create(service);
    }
  }

  private List<SshPairImpl> getOrCreateSshPairs(RuntimeIdentity identity) {
    List<SshPairImpl> sshPairs;
    try {
      sshPairs = sshManager.getPairs(identity.getOwnerId(), "internal");
    } catch (ServerException e) {
      LOG.warn("Unable to get SSH Keys. Cause: {}", e.getMessage());
      return null;
    }
    if (sshPairs.isEmpty()) {
      try {
        sshPairs =
            singletonList(sshManager.generatePair(identity.getOwnerId(), "internal", SSH_KEY_NAME));
      } catch (ServerException | ConflictException e) {
        LOG.warn("Unable to generate the SSH key for async storage service. Cause: {}", e.getMessage());
        return null;
      }
    }
    return sshPairs;
  }

  private ConfigMap createConfigMap(String namespace, String configMapName, SshPair sshPair) {
    Map<String, String> sshConfigData = new HashMap<>();
    sshConfigData.put(AUTHORIZED_KEYS, sshPair.getPublicKey());
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(configMapName)
            .withNamespace(namespace)
            .endMetadata()
            .withData(sshConfigData)
            .build();
    return configMap;
  }

  private Pod createStoragePod(String namespace, String configMap) {
    String containerName = Names.generateName(STORAGE);

    VolumeMount storageVolumeMount =
        new VolumeMountBuilder()
            .withMountPath(STORAGE_DATA_PATH)
            .withName(STORAGE_DATA)
            .withReadOnly(false)
            .build();

    VolumeMount sshVolumeMount =
        new VolumeMountBuilder()
            .withMountPath(SSH_KEY_PATH)
            .withSubPath(AUTHORIZED_KEYS)
            .withName(CONFIG_MAP_VOLUME_NAME)
            .withReadOnly(true)
            .build();

    Container container =
        new ContainerBuilder()
            .withName(containerName)
            .withImage(storageImage)
            .withNewResources()
            .addToLimits("memory", new Quantity("512Mi"))
            .addToRequests("memory", new Quantity("256Mi"))
            .endResources()
            .withPorts(
                new ContainerPortBuilder().withContainerPort(SERVICE_PORT).withProtocol("TCP").build())
            .withVolumeMounts(storageVolumeMount, sshVolumeMount)
            .build();

    Volume storageVolume =
        new VolumeBuilder()
            .withName(STORAGE_DATA)
            .withPersistentVolumeClaim(
                new PersistentVolumeClaimVolumeSourceBuilder()
                    .withClaimName(pvcName)
                    .withReadOnly(false)
                    .build())
            .build();

    Volume sshKeyVolume =
        new VolumeBuilder()
            .withName(CONFIG_MAP_VOLUME_NAME)
            .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMap).build())
            .build();

    PodSpecBuilder podSpecBuilder = new PodSpecBuilder();
    PodSpec podSpec =
        podSpecBuilder.withContainers(container).withVolumes(storageVolume, sshKeyVolume).build();

    Pod pod =
        new PodBuilder()
            .withApiVersion("v1")
            .withKind("Pod")
            .withNewMetadata()
            .withName(STORAGE)
            .withNamespace(namespace)
            .withLabels(singletonMap("app", STORAGE))
            .endMetadata()
            .withSpec(podSpec)
            .build();
    return pod;
  }

  private Service createStorageService(String namespace) {
    ObjectMeta meta = new ObjectMeta();
    meta.setName(STORAGE);
    meta.setNamespace(namespace);

    IntOrString targetPort =
        new IntOrStringBuilder().withIntVal(SERVICE_PORT).withStrVal(valueOf(SERVICE_PORT)).build();

    ServicePort port =
        new ServicePortBuilder()
            .withName("2222")
            .withProtocol("TCP")
            .withPort(SERVICE_PORT)
            .withTargetPort(targetPort)
            .build();
    ServiceSpec spec = new ServiceSpec();
    spec.setPorts(asList(port));
    spec.setSelector(singletonMap("app", STORAGE));

    Service service = new Service();
    service.setApiVersion("v1");
    service.setKind("Service");
    service.setMetadata(meta);
    service.setSpec(spec);

    return service;
  }
}
