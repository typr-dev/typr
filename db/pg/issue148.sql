-- Issue #148: Fix FK column mapping
-- https://github.com/oyvindberg/typo/issues/148

CREATE TABLE test_organisasjon (
  organisasjonskode text NOT NULL,
  PRIMARY KEY (organisasjonskode)
);

CREATE TABLE test_utdanningstilbud (
  organisasjonskode text NOT NULL,
  utdanningsmulighet_kode text NOT NULL,
  PRIMARY KEY (organisasjonskode, utdanningsmulighet_kode),
  FOREIGN KEY (organisasjonskode) REFERENCES test_organisasjon (organisasjonskode)
);

CREATE TABLE test_sak_soknadsalternativ (
  organisasjonskode_saksbehandler text NOT NULL,
  utdanningsmulighet_kode text NOT NULL,
  organisasjonskode_tilbyder text NOT NULL,
  PRIMARY KEY (organisasjonskode_saksbehandler, utdanningsmulighet_kode),
  FOREIGN KEY (organisasjonskode_tilbyder, utdanningsmulighet_kode) 
    REFERENCES test_utdanningstilbud (organisasjonskode, utdanningsmulighet_kode)
);