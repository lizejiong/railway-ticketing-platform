set -eo pipefail

import_sql() {
  local source_file="$1"

  sed -E '/^[[:space:]]*USE[[:space:]]*$/ {
    N
    s/^[[:space:]]*USE[[:space:]]*\n[[:space:]]*([[:alnum:]_]+);/USE `\1`;/
  }' "$source_file" | mysql --protocol=socket --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}"
}

import_sql /docker-entrypoint-initdb.d/10-user-schema.source
import_sql /docker-entrypoint-initdb.d/20-order-schema.source
import_sql /docker-entrypoint-initdb.d/30-pay-schema.source
import_sql /docker-entrypoint-initdb.d/40-ticket-schema.source
import_sql /docker-entrypoint-initdb.d/50-ticket-data.source
import_sql /docker-entrypoint-initdb.d/60-user-data.source
