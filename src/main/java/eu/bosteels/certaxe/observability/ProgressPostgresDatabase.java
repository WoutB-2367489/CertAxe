package eu.bosteels.certaxe.observability;

import eu.bosteels.certaxe.ct.LogList;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.sql.*;

@SuppressWarnings("SqlDialectInspection")
@Service
@Primary // Choose CertificatePostgresDatabase Bean over CertificateDuckDBDatabase Bean
public class ProgressPostgresDatabase implements ProgressDatabase{

  private final Connection connection;
//  private final SmartAppender appender;
  private static final Logger logger = LoggerFactory.getLogger(ProgressPostgresDatabase.class);

  private final static String CREATE_TABLE;

  static {
    CREATE_TABLE = """
              CREATE TABLE IF NOT EXISTS event (
                started  TIMESTAMP,
                finished TIMESTAMP,
                type     VARCHAR,
                list     VARCHAR,
                start_index    int,
                end_index      int
              );
        """;
  }

  @SneakyThrows
  public ProgressPostgresDatabase() {

    String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
    String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres");
    String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/ctlogs");
    this.connection = DriverManager.getConnection(url, user, password);

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(CREATE_TABLE);
    }
    long rows = countRows();
    logger.info("rows = {}", rows);

  }

  @SneakyThrows
  public long countRows() {
    final String query = "select count(1) count from event";
    try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
      if (rs.next()) {
        return rs.getLong("count");
      }
      logger.warn("query {} did not return any rows!?", query);
      return 0;
    }
  }



  @SneakyThrows
  public void append(Event event) {

    String sql = "insert into event (started, finished, type, list, start_index, end_index) values (?, ?, ?, ?, ?, ?)";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setTimestamp(1, new java.sql.Timestamp(event.getStarted().toEpochMilli()));
      ps.setTimestamp(2, new java.sql.Timestamp(event.getFinished().toEpochMilli()));
      ps.setString(3, String.valueOf(event.getType()));
      ps.setString(4, event.getListUrl());
      ps.setInt(5, event.getStart());
      ps.setInt(6, event.getEnd());
      ps.execute();
      logger.debug("row appended");
    } catch (Exception e) {
      logger.warn("failed to insert event: {} -> {}", event.getStart(), event.getEnd());
    }
  }

  public int getNextIndex(LogList list) {
    String query = "select max(end_index) last_index from event where type = 'Appended' and list = ?";
    try (PreparedStatement ps = connection.prepareStatement(query)) {
      ps.setString(1, list.getBaseURL());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          int last_index = rs.getInt("last_index");
          logger.info("{} => last_index = {}", list.getBaseURL(), last_index);
          return last_index + 1;
        }
        logger.info("No progress found for {}", list.getFriendlyName());
        return 0;
      }
    } catch (SQLException e) {
      logger.error("Fetching progress failed: ", e);
      return 0;
    }

  }



}
