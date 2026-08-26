FROM node:24-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
# Only the public runtime configuration file is writable by nginx's unprivileged user.
COPY --from=build --chown=101:101 /workspace/dist/runtime-config.js /usr/share/nginx/html/runtime-config.js
COPY --chmod=0555 docker-entrypoint.d/30-sso-lab-runtime-config.sh /docker-entrypoint.d/30-sso-lab-runtime-config.sh
EXPOSE 8080
