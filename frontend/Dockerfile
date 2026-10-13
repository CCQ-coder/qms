FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# An open tab from the previous release imports this view URL after login.
# Redirect the whole page so old and new application runtimes never mix.
RUN set -eu; \
    assets=/usr/share/nginx/html/assets; \
    view_css="$(find "$assets" -maxdepth 1 -name 'EquipmentRuntimeMonitorView-*.css' -print -quit)"; \
    test -n "$view_css"; \
    for old_hash in BwmNvi04 CQvAnN-I; do \
      printf 'window.location.replace("/equipment/runtime-monitor"); export default {};\n' > "$assets/EquipmentRuntimeMonitorView-$old_hash.js"; \
    done; \
    for old_hash in BtGb76UN BXldtvmw; do \
      cp "$view_css" "$assets/EquipmentRuntimeMonitorView-$old_hash.css"; \
    done
EXPOSE 80
