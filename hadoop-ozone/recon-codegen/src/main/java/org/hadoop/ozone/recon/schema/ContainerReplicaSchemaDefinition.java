package org.hadoop.ozone.recon.schema;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.hadoop.ozone.recon.codegen.SqlDbUtils.TABLE_EXISTS_CHECK;

@Singleton
public class ContainerReplicaSchemaDefinition implements ReconSchemaDefinition {

  public static final String UNHEALTHY_REPLICAS_TABLE_NAME
      = "UNHEALTHY_REPLICAS";

  @Inject
  public ContainerReplicaSchemaDefinition(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  private static final String CONTAINER_ID = "container_id";
  private static final String DATANODE_UUID = "datanode_uuid";
  private final DataSource dataSource;
  private DSLContext dslContext;

  @Override
  public void initializeSchema() throws SQLException {/**/
    Connection conn = dataSource.getConnection();
    dslContext = DSL.using(conn);
    if (!TABLE_EXISTS_CHECK.test(conn,
        UNHEALTHY_REPLICAS_TABLE_NAME)) {
      createUnhealthyReplicasTable();
    }
  }

  private void createUnhealthyReplicasTable() {
    dslContext.createTableIfNotExists(UNHEALTHY_REPLICAS_TABLE_NAME)
        .column(CONTAINER_ID, SQLDataType.BIGINT.nullable(false))
        .column(DATANODE_UUID, SQLDataType.BIGINT.nullable(false))
        .column("in_state_since", SQLDataType.BIGINT.nullable(false))
        .constraint(DSL.constraint("pk_replica_id")
            .primaryKey(CONTAINER_ID, DATANODE_UUID))
        .execute();
  }

  public DSLContext getDSLContext() {
    return dslContext;
  }

}
