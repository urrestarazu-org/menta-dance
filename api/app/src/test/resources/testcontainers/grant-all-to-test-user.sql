-- Mounted at /docker-entrypoint-initdb.d/ (see withCopyFileToContainer below), so the
-- official mysql image's own entrypoint runs this as root during first boot — the same
-- phase that creates the app user and grants it access to its one configured database.
-- Each Abstract*MySqlIntegrationTest base class creates one schema per distinct Spring
-- context on its shared domain container, so the app user needs privileges over every
-- schema, not just one.
GRANT ALL PRIVILEGES ON *.* TO 'test'@'%';
FLUSH PRIVILEGES;
