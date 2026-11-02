#!/bin/sh
set -eu

cert=/root/qms-public-tls/state/certificates/gongying.duckdns.org
# shortcut: 1Panel 重建容器可能改名；重建后按实际名称更新。
container=1Panel-openresty-dAWa

openssl x509 -in "$cert.crt" -noout >/dev/null
openssl pkey -in "$cert.key" -noout >/dev/null
cert_pub=$(openssl x509 -in "$cert.crt" -pubkey -noout | openssl pkey -pubin -outform DER | openssl sha256)
key_pub=$(openssl pkey -in "$cert.key" -pubout -outform DER | openssl sha256)
test "$cert_pub" = "$key_pub"
docker exec -u root "$container" install -d -m 700 /etc/qms-public
docker cp "$cert.crt" "$container:/etc/qms-public/server.crt"
docker cp "$cert.key" "$container:/etc/qms-public/server.key"
docker exec -u root "$container" chmod 600 /etc/qms-public/server.crt /etc/qms-public/server.key
docker exec "$container" nginx -t
docker exec "$container" nginx -s reload
