\echo
\echo 'Creating database schema for SDA:'
\echo '> 01_main.sql'
\ir 01_main.sql
\echo '> 02_functions.sql'
\ir 02_functions.sql
\echo '> 03.1_legacy_main.sql'
\ir 03.1_legacy_main.sql
\echo '> 03.2_legacy_download.sql'
\ir 03.2_legacy_download.sql
\echo '> 03.3_legacy_ega_ebi.sql'
\ir 03.3_legacy_ega_ebi.sql
\echo '> 04_grants.sql'
\ir 04_grants.sql

CREATE USER sda_user WITH PASSWORD 'sda-user-pass';
GRANT base, ingest, verify, finalize, sync, mapper, download TO sda_user;
