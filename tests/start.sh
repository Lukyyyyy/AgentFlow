#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "${test_dir}"' EXIT
mkdir -p "${test_dir}/backend" "${test_dir}/frontend/node_modules" "${test_dir}/bin" "${test_dir}/state"
cp "${repo_dir}/start.sh" "${test_dir}/start.sh"
cat > "${test_dir}/backend/.env" <<'EOF'
MYSQL_HOST=localhost
MYSQL_PORT=3306
MINIO_ENDPOINT=http://localhost:9000
EOF
touch "${test_dir}/frontend/.env.local"
printf '#!/usr/bin/env bash\nexit 0\n' > "${test_dir}/backend/mvnw"
chmod +x "${test_dir}/backend/mvnw"

cat > "${test_dir}/bin/mysqladmin" <<'EOF'
#!/usr/bin/env bash
[[ -f "${MOCK_STATE}/mysql" ]]
EOF
cat > "${test_dir}/bin/redis-cli" <<'EOF'
#!/usr/bin/env bash
echo PONG
EOF
cat > "${test_dir}/bin/curl" <<'EOF'
#!/usr/bin/env bash
[[ -f "${MOCK_STATE}/minio" ]]
EOF
cat > "${test_dir}/bin/brew" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == list ]]; then
  exit 0
fi
if [[ "$1" == services && "$2" == start ]]; then
  touch "${MOCK_STATE}/$3"
  echo "$3" >> "${MOCK_STATE}/started"
fi
EOF
cat > "${test_dir}/bin/npm" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${test_dir}/bin/"*

export MOCK_STATE="${test_dir}/state"
export PATH="${test_dir}/bin:${PATH}"

touch "${MOCK_STATE}/mysql" "${MOCK_STATE}/minio"
bash "${test_dir}/start.sh" > "${test_dir}/ready.log" 2>&1
[[ ! -e "${MOCK_STATE}/started" ]]

rm "${MOCK_STATE}/mysql" "${MOCK_STATE}/minio"
bash "${test_dir}/start.sh" > "${test_dir}/stopped.log" 2>&1
[[ "$(sort "${MOCK_STATE}/started" | tr '\n' ' ')" == 'minio mysql ' ]]
grep -q 'MySQL 已就绪' "${test_dir}/stopped.log"
grep -q 'MinIO 已就绪' "${test_dir}/stopped.log"

sed -i.bak 's/MYSQL_HOST=localhost/MYSQL_HOST=db.example.test/' "${test_dir}/backend/.env"
rm "${MOCK_STATE}/mysql" "${MOCK_STATE}/started"
bash "${test_dir}/start.sh" > "${test_dir}/external.log" 2>&1
[[ ! -e "${MOCK_STATE}/started" ]]
grep -q '使用外部 MySQL' "${test_dir}/external.log"

echo 'start.sh middleware checks passed'
