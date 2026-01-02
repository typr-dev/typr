#!/bin/bash
# DB2 healthcheck script - verifies database is accessible

# Run as db2inst1 user
su - db2inst1 <<'EOF'
db2 connect to typr >/dev/null 2>&1
RESULT=$?
db2 connect reset >/dev/null 2>&1
exit $RESULT
EOF
