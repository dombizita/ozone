/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.conf;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdds.DFSConfigKeysLegacy;
import org.apache.hadoop.hdds.annotation.InterfaceAudience;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.hdds.utils.LegacyHadoopConfigurationSource;

import com.google.common.base.Preconditions;
import org.apache.hadoop.ozone.OzoneConfigKeys;
import org.apache.ratis.server.RaftServerConfigKeys;

import static java.util.Collections.unmodifiableSortedSet;
import static java.util.stream.Collectors.toCollection;
import static org.apache.hadoop.hdds.ratis.RatisHelper.HDDS_DATANODE_RATIS_PREFIX_KEY;
import static org.apache.hadoop.ozone.OzoneConfigKeys.OZONE_CONTAINER_COPY_WORKDIR;
import static org.apache.hadoop.ozone.OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE_UNRESTRICTED;

/**
 * Configuration for ozone.
 */
@InterfaceAudience.Private
public class OzoneConfiguration extends Configuration
    implements MutableConfigurationSource {

  private final String complianceMode;
  private final boolean checkCompliance;
  private final Properties cryptoProperties;
  public static final SortedSet<String> TAGS = unmodifiableSortedSet(
      Arrays.stream(ConfigTag.values())
          .map(Enum::name)
          .collect(toCollection(TreeSet::new)));

  static {
    addDeprecatedKeys();

    activate();
  }

  public static OzoneConfiguration of(ConfigurationSource source) {
    if (source instanceof LegacyHadoopConfigurationSource) {
      return new OzoneConfiguration(((LegacyHadoopConfigurationSource) source)
          .getOriginalHadoopConfiguration());
    }
    return (OzoneConfiguration) source;
  }

  public static OzoneConfiguration of(OzoneConfiguration source) {
    return source;
  }

  public static OzoneConfiguration of(Configuration conf) {
    Preconditions.checkNotNull(conf);

    return conf instanceof OzoneConfiguration
        ? (OzoneConfiguration) conf
        : new OzoneConfiguration(conf);
  }

  /**
   * @return a new config object of type {@code T} configured with defaults
   * and any overrides from XML
   */
  public static <T> T newInstanceOf(Class<T> configurationClass) {
    OzoneConfiguration conf = new OzoneConfiguration();
    return conf.getObject(configurationClass);
  }

  public OzoneConfiguration() {
    OzoneConfiguration.activate();
    loadDefaults();
    complianceMode = getPropertyUnsafe(OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE,
          OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE_UNRESTRICTED);
    checkCompliance = !complianceMode.equals(OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE_UNRESTRICTED);
    cryptoProperties = getCryptoProperties();
  }

  public OzoneConfiguration(Configuration conf) {
    super(conf);
    //load the configuration from the classloader of the original conf.
    setClassLoader(conf.getClassLoader());
    if (!(conf instanceof OzoneConfiguration)) {
      loadDefaults();
      addResource(conf);
    }
    complianceMode = getPropertyUnsafe(OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE,
        OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE_UNRESTRICTED);
    checkCompliance = !complianceMode.equals(OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE_UNRESTRICTED);
    cryptoProperties = getCryptoProperties();
  }

  private void loadDefaults() {
    try {
      //there could be multiple ozone-default-generated.xml files on the
      // classpath, which are generated by the annotation processor.
      // Here we add all of them to the list of the available configuration.
      Enumeration<URL> generatedDefaults =
          OzoneConfiguration.class.getClassLoader().getResources(
              "ozone-default-generated.xml");
      while (generatedDefaults.hasMoreElements()) {
        addResource(generatedDefaults.nextElement());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    addResource("ozone-default.xml");
    // Adding core-site here because properties from core-site are
    // distributed to executors by spark driver. Ozone properties which are
    // added to core-site, will be overridden by properties from adding Resource
    // ozone-default.xml. So, adding core-site again will help to resolve
    // this override issue.
    addResource("core-site.xml");
    addResource("ozone-site.xml");
  }

  public List<Property> readPropertyFromXml(URL url) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(XMLConfiguration.class);
    Unmarshaller um = context.createUnmarshaller();

    XMLConfiguration config = (XMLConfiguration) um.unmarshal(url);
    return config.getProperties();
  }

  /**
   * Class to marshall/un-marshall configuration from xml files.
   */
  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlRootElement(name = "configuration")
  public static class XMLConfiguration {

    @XmlElement(name = "property", type = Property.class)
    private List<Property> properties = new ArrayList<>();

    public XMLConfiguration() {
    }

    public XMLConfiguration(List<Property> properties) {
      this.properties = new ArrayList<>(properties);
    }

    public List<Property> getProperties() {
      return Collections.unmodifiableList(properties);
    }
  }

  /**
   * Class to marshall/un-marshall configuration properties from xml files.
   */
  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlRootElement(name = "property")
  public static class Property implements Comparable<Property> {

    private String name;
    private String value;
    private String tag;
    private String description;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getTag() {
      return tag;
    }

    public void setTag(String tag) {
      this.tag = tag;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    @Override
    public int compareTo(Property o) {
      if (this == o) {
        return 0;
      }
      return this.getName().compareTo(o.getName());
    }

    @Override
    public String toString() {
      return this.getName() + " " + this.getValue() + " " + this.getTag();
    }

    @Override
    public int hashCode() {
      return this.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof Property) && (((Property) obj).getName())
          .equals(this.getName());
    }
  }

  public static void activate() {
    // adds the default resources
    Configuration.addDefaultResource("hdfs-default.xml");
    Configuration.addDefaultResource("hdfs-site.xml");
  }

  /**
   * The super class method getAllPropertiesByTag
   * does not override values of properties
   * if there is no tag present in the configs of
   * newly added resources.
   *
   * @param tag
   * @return Properties that belong to the tag
   */
  @Override
  public Properties getAllPropertiesByTag(String tag) {
    // Call getProps first to load the newly added resources
    // before calling super.getAllPropertiesByTag
    Properties updatedProps = getProps();
    Properties propertiesByTag = super.getAllPropertiesByTag(tag);
    Properties properties = new Properties();
    Enumeration propertyNames = propertiesByTag.propertyNames();
    while (propertyNames.hasMoreElements()) {
      Object propertyName = propertyNames.nextElement();
      // get the current value of the property
      Object value = updatedProps.getProperty(propertyName.toString());
      if (value != null) {
        properties.put(propertyName, value);
      }
    }
    return properties;
  }

  public Map<String, String> getOzoneProperties() {
    String ozoneRegex = ".*(ozone|hdds|ratis|container|scm|recon)\\..*";
    return getValByRegex(ozoneRegex);
  }

  @Override
  public Collection<String> getConfigKeys() {
    return getProps().keySet()
        .stream()
        .map(Object::toString)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, String> getPropsMatchPrefixAndTrimPrefix(
      String keyPrefix) {
    Properties props = getProps();
    Map<String, String> configMap = new HashMap<>();
    for (String name : props.stringPropertyNames()) {
      if (name.startsWith(keyPrefix)) {
        String value = get(name);
        String keyName = name.substring(keyPrefix.length());
        configMap.put(keyName, value);
      }
    }
    return configMap;
  }

  @Override
  public boolean isPropertyTag(String tagStr) {
    return TAGS.contains(tagStr) || super.isPropertyTag(tagStr);
  }

  private static void addDeprecatedKeys() {
    Configuration.addDeprecations(new DeprecationDelta[]{
        new DeprecationDelta("ozone.datanode.pipeline.limit",
            ScmConfigKeys.OZONE_DATANODE_PIPELINE_LIMIT),
        new DeprecationDelta(HDDS_DATANODE_RATIS_PREFIX_KEY + "."
           + RaftServerConfigKeys.PREFIX + "." + "rpcslowness.timeout",
           HDDS_DATANODE_RATIS_PREFIX_KEY + "."
           + RaftServerConfigKeys.PREFIX + "." + "rpc.slowness.timeout"),
        new DeprecationDelta("dfs.datanode.keytab.file",
            DFSConfigKeysLegacy.DFS_DATANODE_KERBEROS_KEYTAB_FILE_KEY),
        new DeprecationDelta("ozone.scm.chunk.layout",
            ScmConfigKeys.OZONE_SCM_CONTAINER_LAYOUT_KEY),
        new DeprecationDelta("hdds.datanode.replication.work.dir",
            OZONE_CONTAINER_COPY_WORKDIR),
        new DeprecationDelta("dfs.container.chunk.write.sync",
            OzoneConfigKeys.HDDS_CONTAINER_CHUNK_WRITE_SYNC_KEY),
        new DeprecationDelta("dfs.container.ipc",
            OzoneConfigKeys.HDDS_CONTAINER_IPC_PORT),
        new DeprecationDelta("dfs.container.ipc.random.port",
            OzoneConfigKeys.HDDS_CONTAINER_IPC_RANDOM_PORT),
        new DeprecationDelta("dfs.container.ratis.admin.port",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_ADMIN_PORT),
        new DeprecationDelta("dfs.container.ratis.datanode.storage.dir",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_DATANODE_STORAGE_DIR),
        new DeprecationDelta("dfs.container.ratis.datastream.enabled",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_DATASTREAM_ENABLED),
        new DeprecationDelta("dfs.container.ratis.datastream.port",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_DATASTREAM_PORT),
        new DeprecationDelta("dfs.container.ratis.datastream.random.port",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_DATASTREAM_RANDOM_PORT),
        new DeprecationDelta("dfs.container.ratis.enabled",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_ENABLED_KEY),
        new DeprecationDelta("dfs.container.ratis.ipc",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_IPC_PORT),
        new DeprecationDelta("dfs.container.ratis.ipc.random.port",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_IPC_RANDOM_PORT),
        new DeprecationDelta("dfs.container.ratis.leader.pending.bytes.limit",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LEADER_PENDING_BYTES_LIMIT),
        new DeprecationDelta("dfs.container.ratis.log.appender.queue.byte-limit",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LOG_APPENDER_QUEUE_BYTE_LIMIT),
        new DeprecationDelta("dfs.container.ratis.log.appender.queue.num-elements",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LOG_APPENDER_QUEUE_NUM_ELEMENTS),
        new DeprecationDelta("dfs.container.ratis.log.purge.gap",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LOG_PURGE_GAP),
        new DeprecationDelta("dfs.container.ratis.log.queue.byte-limit",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LOG_QUEUE_BYTE_LIMIT),
        new DeprecationDelta("dfs.container.ratis.log.queue.num-elements",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_LOG_QUEUE_NUM_ELEMENTS),
        new DeprecationDelta("dfs.container.ratis.num.container.op.executors",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_NUM_CONTAINER_OP_EXECUTORS_KEY),
        new DeprecationDelta("dfs.container.ratis.num.write.chunk.threads.per.volume",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_NUM_WRITE_CHUNK_THREADS_PER_VOLUME),
        new DeprecationDelta("dfs.container.ratis.replication.level",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_REPLICATION_LEVEL_KEY),
        new DeprecationDelta("dfs.container.ratis.rpc.type",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_RPC_TYPE_KEY),
        new DeprecationDelta("dfs.container.ratis.segment.preallocated.size",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_SEGMENT_PREALLOCATED_SIZE_KEY),
        new DeprecationDelta("dfs.container.ratis.segment.size",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_SEGMENT_SIZE_KEY),
        new DeprecationDelta("dfs.container.ratis.server.port",
            OzoneConfigKeys.HDDS_CONTAINER_RATIS_SERVER_PORT),
        new DeprecationDelta("dfs.container.ratis.statemachinedata.sync.retries",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_STATEMACHINEDATA_SYNC_RETRIES),
        new DeprecationDelta("dfs.container.ratis.statemachinedata.sync.timeout",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_STATEMACHINEDATA_SYNC_TIMEOUT),
        new DeprecationDelta("dfs.container.ratis.statemachine.max.pending.apply-transactions",
            ScmConfigKeys.HDDS_CONTAINER_RATIS_STATEMACHINE_MAX_PENDING_APPLY_TXNS),
        new DeprecationDelta("dfs.ratis.leader.election.minimum.timeout.duration",
            ScmConfigKeys.HDDS_RATIS_LEADER_ELECTION_MINIMUM_TIMEOUT_DURATION_KEY),
        new DeprecationDelta("dfs.ratis.server.retry-cache.timeout.duration",
            ScmConfigKeys.HDDS_RATIS_SERVER_RETRY_CACHE_TIMEOUT_DURATION_KEY),
        new DeprecationDelta("dfs.ratis.snapshot.threshold",
            ScmConfigKeys.HDDS_RATIS_SNAPSHOT_THRESHOLD_KEY)
    });
  }

  /**
   * Gets backwards-compatible configuration property values.
   * @param name Primary configuration attribute key name.
   * @param fallbackName The key name of the configuration property that needs
   *                     to be backward compatible.
   * @param defaultValue The default value to be returned.
   */
  public int getInt(String name, String fallbackName, int defaultValue,
      Consumer<String> log) {
    String value = this.getTrimmed(name);
    if (value == null) {
      value = this.getTrimmed(fallbackName);
      if (log != null) {
        log.accept(name + " is not set.  Fallback to " + fallbackName +
            ", which is set to " + value);
      }
    }
    if (value == null) {
      return defaultValue;
    }
    return Integer.parseInt(value);
  }

  private Properties props;
  private Properties delegatingProps;

  @Override
  public synchronized void reloadConfiguration() {
    super.reloadConfiguration();
    delegatingProps = null;
    props = null;
  }

  @Override
  protected final synchronized Properties getProps() {
    if (delegatingProps == null) {
      props = super.getProps();
      delegatingProps = new DelegatingProperties(this, props);
    }
    return delegatingProps;
  }

  /**
   * Get a property value without the compliance check. It's needed to get the compliance
   * mode and the whitelist parameter values in the checkCompliance method.
   *
   * @param key property name
   * @param defaultValue default value
   * @return property value, without compliance check
   */
  private String getPropertyUnsafe(String key, String defaultValue) {
    return super.getProps().getProperty(key, defaultValue);
  }

  private Properties getCryptoProperties() {
    return super.getAllPropertiesByTag(ConfigTag.CRYPTO_COMPLIANCE.toString());
  }

  public String checkCompliance(String config, String value) {
    // Don't check the ozone.security.crypto.compliance.mode config, even though it's tagged as a crypto config
    if (checkCompliance && cryptoProperties.containsKey(config) &&
        !config.equals(OzoneConfigKeys.OZONE_SECURITY_CRYPTO_COMPLIANCE_MODE)) {

      String whitelistConfig = config + "." + complianceMode + ".whitelist";
      String whitelistValue = getPropertyUnsafe(whitelistConfig, "");

      if (whitelistValue != null) {
        String[] whitelistOptions = whitelistValue.split(",");

        if (!Arrays.asList(whitelistOptions).contains(value)) {
          throw new ConfigurationException("Not allowed configuration value! Compliance mode is set to " +
              complianceMode + " and " + config + " configuration's value is not allowed. Please check the " +
              whitelistConfig + " configuration.");
        }
      }
    }
    return value;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    Properties props = getProps();
    Map<String, String> result = new HashMap<>();
    synchronized (props) {
      for (Map.Entry<Object, Object> item : props.entrySet()) {
        if (item.getKey() instanceof String && item.getValue() instanceof String) {
          checkCompliance((String) item.getKey(), (String) item.getValue());
          result.put((String) item.getKey(), (String) item.getValue());
        }
      }
    }
    return result.entrySet().iterator();
  }
}


