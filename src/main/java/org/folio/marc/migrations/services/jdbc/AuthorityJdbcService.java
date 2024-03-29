package org.folio.marc.migrations.services.jdbc;

import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class AuthorityJdbcService extends JdbcService {

  private static final String CREATE_RECORDS_LB_VIEW_SQL = """
    CREATE OR REPLACE VIEW %s.records_lb_view
          AS
        SELECT *
          FROM %s_mod_source_record_storage.records_lb;
    """;
  private static final String CREATE_MARC_RECORDS_LB_VIEW_SQL = """
    CREATE OR REPLACE VIEW %s.marc_records_lb_view
          AS
        SELECT *
          FROM %s_mod_source_record_storage.marc_records_lb;
    """;
  private static final String CREATE_AUTHORITY_VIEW_SQL = """
    CREATE OR REPLACE VIEW %s.authority_view
          AS
        SELECT *
          FROM %s_mod_entities_links.authority;
    """;
  private static final String CREATE_MARC_AUTHORITY_VIEW_SQL = """
    CREATE OR REPLACE VIEW %1$s.marc_authority_view
           AS
         SELECT r.id as marc_id, r.external_id as authority_id, mr.content as marc, r.state, a._version as version
         FROM %1$s.records_lb_view r
         LEFT JOIN %1$s.marc_records_lb_view mr ON mr.id = r.id
         LEFT JOIN %1$s.authority_view a ON a.id = r.external_id
         WHERE r.state = 'ACTUAL' AND r.record_type = 'MARC_AUTHORITY'
         ORDER BY r.id;
     """;

  private static final String COUNT_AUTHORITY_RECORDS = """
    SELECT COUNT(*)
    FROM %s.marc_authority_view;
    """;

  private static final String GET_AUTHORITY_IDS_CHUNK = """
    SELECT marc_id
    FROM %s.marc_authority_view
    %s
    LIMIT %s;
    """;

  private static final String GET_AUTHORITIES_CHUNK = """
    SELECT *
    FROM %s.marc_authority_view
    WHERE marc_id >= '%s' and marc_id <= '%s';
    """;

  private final JdbcTemplate jdbcTemplate;
  private final BeanPropertyRowMapper<MarcRecord> recordsMapper;

  public AuthorityJdbcService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                              BeanPropertyRowMapper<MarcRecord> recordsMapper) {
    super(context);
    this.jdbcTemplate = jdbcTemplate;
    this.recordsMapper = recordsMapper;
  }

  public void initViews(String tenantId) {
    var schemaName = getSchemaName();
    createView(tenantId, CREATE_RECORDS_LB_VIEW_SQL.formatted(schemaName, tenantId));
    createView(tenantId, CREATE_MARC_RECORDS_LB_VIEW_SQL.formatted(schemaName, tenantId));
    createView(tenantId, CREATE_AUTHORITY_VIEW_SQL.formatted(schemaName, tenantId));
    createView(tenantId, CREATE_MARC_AUTHORITY_VIEW_SQL.formatted(schemaName));
  }

  public Integer countNumOfRecords() {
    log.info("countNumOfRecords::Counting number of records in 'marc_authority_view'");
    return jdbcTemplate.queryForObject(COUNT_AUTHORITY_RECORDS.formatted(getSchemaName()), Integer.class);
  }

  public List<UUID> getAuthorityIdsChunk(UUID idFrom, Integer limit) {
    var whereClause = idFrom == null ? "" : "WHERE marc_id > '%s'".formatted(idFrom);
    var sql = GET_AUTHORITY_IDS_CHUNK.formatted(getSchemaName(), whereClause, limit);
    return jdbcTemplate.queryForList(sql, UUID.class);
  }

  public List<UUID> getAuthorityIdsChunk(Integer limit) {
    return getAuthorityIdsChunk(null, limit);
  }

  public List<MarcRecord> getAuthoritiesChunk(UUID from, UUID to) {
    log.debug("getAuthoritiesChunk:: from id {}, to id {}", from, to);
    var sql = GET_AUTHORITIES_CHUNK.formatted(getSchemaName(), from, to);
    return jdbcTemplate.query(sql, recordsMapper);
  }

  private void createView(String tenantId, String query) {
    log.info("createView:: Attempting to create view [tenant: {}, query: {}]", tenantId, query);
    jdbcTemplate.execute(query);
    log.info("createView:: Successfully created view [tenant: {}, query: {}]", tenantId, query);
  }
}
