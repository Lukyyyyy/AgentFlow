FROM docker.1ms.run/library/node:22-alpine AS build
WORKDIR /workspace

RUN npm config set registry https://registry.npmmirror.com
COPY package*.json ./
RUN npm ci

COPY . .
ENV VITE_API_BASE_URL=/api
RUN npm run build

FROM docker.m.daocloud.io/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
