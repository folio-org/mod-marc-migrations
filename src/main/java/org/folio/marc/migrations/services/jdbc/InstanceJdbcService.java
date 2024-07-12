package org.folio.marc.migrations.services.jdbc;

import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Log4j2
@Service
public class InstanceJdbcService extends JdbcService {

  private static final String CREATE_INSTANCE_VIEW_SQL = """
    CREATE OR REPLACE VIEW %s.instance_view
          AS
        SELECT *
          FROM %s_mod_inventory_storage.instance;
    """;

  private static final String CREATE_MARC_BIB_VIEW_SQL = """
    CREATE OR REPLACE VIEW %1$s.marc_bib_view
           AS
         SELECT r.id as marc_id, r.external_id as instance_id, mr.content as marc, r.state, (i.jsonb ->> '_version')::int as version
         FROM %1$s.records_lb_view r
         LEFT JOIN %1$s.marc_records_lb_view mr ON mr.id = r.id
         LEFT JOIN %1$s.instance_view i ON i.id = r.external_id
         WHERE r.state = 'ACTUAL' AND r.record_type = 'MARC_BIB'
         ORDER BY r.id;
    """;

  private static final String COUNT_INSTANCE_RECORDS = """
    SELECT COUNT(*)
    FROM %s.marc_bib_view;
    """;

  private static final String GET_INSTANCE_IDS_CHUNK = """
    SELECT marc_id
    FROM %s.marc_bib_view
    %s
    LIMIT %s;
    """;

  private static final String GET_INSTANCES_CHUNK = """
    SELECT *
    FROM %s.marc_bib_view
    WHERE marc_id >= '%s' and marc_id <= '%s';
    """;

  private final BeanPropertyRowMapper<MarcRecord> recordsMapper;

  public InstanceJdbcService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                             @Qualifier("marcBibRawMapper") BeanPropertyRowMapper<MarcRecord> recordsMapper) {
    super(jdbcTemplate, context);
    this.recordsMapper = recordsMapper;
  }

  public Integer countNumOfRecords() {
    log.info("countNumOfRecords::Counting number of records in 'marc_bib_view'");
    return jdbcTemplate.queryForObject(COUNT_INSTANCE_RECORDS.formatted(getSchemaName()), Integer.class);
  }

  public void initViews(String tenantId) {
    var schemaName = getSchemaName();
    createView(tenantId, CREATE_INSTANCE_VIEW_SQL.formatted(schemaName, tenantId));
    createView(tenantId, CREATE_MARC_BIB_VIEW_SQL.formatted(schemaName));
  }

  public List<UUID> getInstanceIdsChunk(Integer limit) {
    return getInstanceIdsChunk(null, limit);
  }

  public List<UUID> getInstanceIdsChunk(UUID idFrom, Integer limit) {
    var whereClause = idFrom == null ? "" : "WHERE marc_id > '%s'".formatted(idFrom);
    var sql = GET_INSTANCE_IDS_CHUNK.formatted(getSchemaName(), whereClause, limit);
    return jdbcTemplate.queryForList(sql, UUID.class);
  }

  public List<MarcRecord> getInstancesChunk(UUID from, UUID to) {
    log.debug("getInstancesChunk:: from id {}, to id {}", from, to);
    var sql = GET_INSTANCES_CHUNK.formatted(getSchemaName(), from, to);
    return jdbcTemplate.query(sql, recordsMapper);
  }
}
