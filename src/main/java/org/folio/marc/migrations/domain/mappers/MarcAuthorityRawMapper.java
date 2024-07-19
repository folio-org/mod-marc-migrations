package org.folio.marc.migrations.domain.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.folio.marc.migrations.domain.entities.MarcRecord;
import org.folio.marc.migrations.domain.entities.types.RecordState;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.stereotype.Component;

@Component("marcAuthorityRawMapper")
public class MarcAuthorityRawMapper extends DataClassRowMapper<MarcRecord> {
  @Override
  public MarcRecord mapRow(ResultSet rs, int rowNumber) throws SQLException {
    return new MarcRecord(rs.getObject("marc_id", UUID.class),
      rs.getObject("authority_id", UUID.class),
      rs.getObject("marc"),
      RecordState.valueOf(rs.getString("state")),
      rs.getObject("version", Integer.class)
    );
  }
}
