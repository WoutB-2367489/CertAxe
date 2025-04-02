package eu.bosteels.certaxe.db;

import eu.bosteels.certaxe.certificates.Certificate;
import eu.bosteels.certaxe.certificates.CertificateAppender;
import eu.bosteels.certaxe.ct.LogList;
import eu.bosteels.certaxe.observability.ProgressPostgresDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;

@SuppressWarnings("SqlDialectInspection")
@Primary // Choose CertificatePostgresDatabase Bean over CertificateDuckDBDatabase Bean
@Service
public class CertificatePostgresDatabase implements CertificateAppender {

  @Value("${default-data-source-url}")
  private String DEFAULT_DATA_SOURCE_URL = "jdbc:postgresql://localhost:5432/ctlogs";

  private final Connection connection;
  private static final Logger logger = LoggerFactory.getLogger(CertificatePostgresDatabase.class);

  private final ProgressPostgresDatabase progressDatabase;

  // TODO: create enum for signatureHashAlgorithm
  // CREATE TYPE signatureHashAlgorithm AS ENUM ('sha256, 'sha384', 'sha1', 'md5', sha512');
  private PreparedStatement preparedStatement;

  private final static String CREATE_TABLE;
  // or REPLACE

  static {
    CREATE_TABLE = """
              CREATE TABLE if not exists certificate (
                ct_list                   VARCHAR,
                ct_index                  INTEGER, --// or LONG ?
                version                   INTEGER,
                serialNumberHex           VARCHAR,
                publicKeySchema           VARCHAR,
                publicKeyLength           INTEGER,
                notBefore                 TIMESTAMP   NOT NULL, -- TIMESTAMP_MS  not null,
                notAfter                  TIMESTAMP   NOT NULL, -- TIMESTAMP_MS  not null,
                issuer                    VARCHAR,
                issuer_cn                 VARCHAR,
                issuer_c                  VARCHAR,
                issuer_o                  VARCHAR,
                subject                   VARCHAR,
                subject_cn                VARCHAR,
                subject_c                 VARCHAR,
                subject_o                 VARCHAR,
                signatureHashAlgorithm    VARCHAR,
                sha256Fingerprint         VARCHAR,
                subjectAlternativeNames   VARCHAR[],
                domainNames               VARCHAR[],
                publicSuffixes            VARCHAR[],
                registrableNames          VARCHAR[],
                topPrivateDomains         VARCHAR[],
                tlds                      VARCHAR[],
                authorityKeyIdentifier    VARCHAR,
                subjectKeyIdentifier      VARCHAR,
                keyUsage                  VARCHAR[],
                extendedKeyUsage          VARCHAR[],
                authorityInfoAccess       VARCHAR[],
                isCa                      BOOLEAN     not null
                );
        """;
  }

//          extensionCount            INTEGER,

  public CertificatePostgresDatabase(ProgressPostgresDatabase progressDatabase) throws SQLException {

    this.progressDatabase = progressDatabase;
    // TODO: decide if we want to use a persistent database instead
    // for example to keep track of the progress


    String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
    String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres");
    String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", DEFAULT_DATA_SOURCE_URL);
    this.connection = DriverManager.getConnection(url, user, password);

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(CREATE_TABLE);
    }
    countRows();

  }

  @SuppressWarnings("SqlDialectInspection")
  public long countRows() throws SQLException {
    final String query = "select count(1) count from certificate";
    try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
      if (rs.next()) {
        var count = rs.getLong("count");
        logger.info("We found {} certificates in the database", count);
        return count;
      }
      logger.warn("query {} did not return any rows!?", query);
      return 0;
    }
  }


  public void append(Certificate certificate, LogList list, long index) throws SQLException {
    String sql = "INSERT INTO certificate (ct_list, ct_index, version, serialNumberHex, publicKeySchema, publicKeyLength, notBefore, notAfter, issuer, issuer_cn, issuer_c, issuer_o, subject, subject_cn, subject_c, subject_o, signatureHashAlgorithm, sha256Fingerprint, subjectAlternativeNames, domainNames, publicSuffixes, registrableNames, topPrivateDomains, tlds, authorityKeyIdentifier, subjectKeyIdentifier, keyUsage, extendedKeyUsage, authorityInfoAccess, isCa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, list.getFriendlyName());
      ps.setLong(2, index);
      ps.setInt(3, certificate.getVersion());
      ps.setString(4, certificate.getSerialNumberHex());
      ps.setString(5, certificate.getPublicKeySchema());
      ps.setInt(6, certificate.getPublicKeyLength());
      ps.setTimestamp(7, new java.sql.Timestamp(certificate.getNotBefore().toEpochMilli()));
      ps.setTimestamp(8, new java.sql.Timestamp(certificate.getNotAfter().toEpochMilli()));
      ps.setString(9, certificate.getIssuer());
      ps.setString(10, certificate.getIssuer_CN());
      ps.setString(11, certificate.getIssuer_C());
      ps.setString(12, certificate.getIssuer_O());
      ps.setString(13, certificate.getSubject());
      ps.setString(14, certificate.getSubject_CN());
      ps.setString(15, certificate.getSubject_C());
      ps.setString(16, certificate.getSubject_O());
      ps.setString(17, certificate.getSignatureHashAlgorithm());
      ps.setArray(19, toSQLArray(certificate.getSubjectAlternativeNames()));
      ps.setArray(20, toSQLArray(certificate.getDomainNames()));
      ps.setArray(21, toSQLArray(certificate.getPublicSuffixes()));
      ps.setArray(22, toSQLArray(certificate.getRegistrableNames()));
      ps.setArray(23, toSQLArray(certificate.getTopPrivateDomains()));
      ps.setString(18, certificate.getSha256Fingerprint());
      ps.setArray(24, toSQLArray(certificate.getTlds()));
      ps.setString(25, certificate.getAuthorityKeyIdentifier());
      ps.setString(26, certificate.getSubjectKeyIdentifier());
      ps.setArray(27, toSQLArray(certificate.getKeyUsage()));
      ps.setArray(28, toSQLArray(certificate.getExtendedKeyUsage()));
      ps.setArray(29, toSQLArray(certificate.getAuthorityInfoAccess()));
      ps.setBoolean(30, certificate.getIsCa());
      this.preparedStatement = ps;
      flush();
    } catch (Exception e) {
      logger.debug("Failed to save cert:", e);
    }

    logger.info("Appended cert {}", index);

  }

  private java.sql.Array toSQLArray(List<String> list) throws SQLException {
    if (list != null) {
      return connection.createArrayOf("VARCHAR", list.toArray());
    }
    return null;
  }

  @Override
  public void flush() throws SQLException {
    preparedStatement.execute();
  }


}
