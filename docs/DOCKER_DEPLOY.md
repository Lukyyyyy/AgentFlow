# Docker 部署说明

本项目提供 Docker Compose 部署，用于小规模本地或单机部署。默认只监听本机地址，适合先在服务器本地验证，再接入现有 Nginx/HTTPS 公网入口。

## 国内镜像源

项目内已配置国内源：

- Docker 基础镜像：`docker.1ms.run`，前端 Nginx 使用 `docker.m.daocloud.io`
- Maven 依赖：阿里云 Maven 仓库，见 `backend/settings.xml`
- npm 依赖：`https://registry.npmmirror.com`

当前没有修改全局 Docker daemon 配置，避免重启 Docker 影响服务器上其他运行中的容器。

## 启动

Compose 依赖外部网络 `server_proxy` 和 `shared_services`，启动前请使用 `sudo docker network inspect server_proxy shared_services` 检查。若缺少网络，请确认网络规划后使用 `sudo docker network create server_proxy` 或 `sudo docker network create shared_services` 创建缺少的网络；已有网络无需重建。共享网络仅应接入可信容器。

以下复制操作仅用于首次部署；已有 `.env` 时请保留现有密码和密钥，不要用示例覆盖。

```bash
test -e .env || cp .env.docker.example .env
# 修改 .env 中的密码、JWT 密钥和腾讯云 SES API 密钥
sudo docker compose up -d --build
```

## 邮箱注册

邮箱注册通过腾讯云 SES 发送验证码。示例配置已包含审核通过的发信地址与模板 ID；部署前只需在 `.env` 中填写：

```dotenv
TENCENT_SES_SECRET_ID=your_secret_id
TENCENT_SES_SECRET_KEY=your_secret_key
```

当前配置使用：

- 发信地址：`agentflow-noreply@mail.lukybetter.com`
- 验证码模板 ID：`213823`（模板变量为 `code`、`minutes`）
- 邮件服务测试模板 ID：`213824`（预留，不用于公开注册接口）
- 默认区域：`ap-hongkong`；如果 SES 发信域名所在区域不同，请修改 `TENCENT_SES_REGION`

修改配置后重建后端容器：

```bash
sudo docker compose up -d --build backend
```

`TENCENT_SES_SECRET_ID` 或 `TENCENT_SES_SECRET_KEY` 留空时，获取注册验证码会返回 `503`。

当前 Compose 会启动：

- `mysql`：MySQL 8.4，仅在数据目录为空时执行 `backend/src/main/resources/schema.sql` 初始化
- `redis`：Redis 7.4，限制 `maxmemory 128mb`
- `minio`：对象存储，默认只绑定 `127.0.0.1:9000`
- `agentflow-backend`：Spring Boot，默认只绑定 `127.0.0.1:8084`
- `agentflow-frontend`：Nginx 静态前端，默认只绑定 `127.0.0.1:5173`

容器名称在 Docker 主机上必须唯一；已有同名服务时，请先确认所属部署，不要直接删除或替换。MySQL 和 MinIO 使用命名数据卷，更新时不要执行 `docker compose down -v`，以免删除数据。`backups/` 为本地备份目录，不应提交到 Git。

## 验证

```bash
sudo docker compose ps
curl --noproxy '*' -I http://127.0.0.1:5173
curl --noproxy '*' http://127.0.0.1:5173/api/node-types
```

如果本机设置了 HTTP 代理，访问 `127.0.0.1` 时建议加 `--noproxy '*'`，否则可能出现空响应。

## 资源限制

当前限制适合 2-3 个用户同时在线的轻量使用：

- MySQL：`mem_limit: 768m`，InnoDB buffer pool `256M`
- Redis：`mem_limit: 192m`，`maxmemory 128mb`
- MinIO：`mem_limit: 384m`
- 后端：`mem_limit: 768m`，JVM `-Xms128m -Xmx512m`
- 前端：`mem_limit: 128m`

## 公网开放建议

不要直接公网暴露 Vite 或后端端口。建议保留本 Compose 的本机绑定，由服务器现有 Nginx/HTTPS 入口反代：

```nginx
location / {
    proxy_pass http://127.0.0.1:5173;
}

location /api/ {
    proxy_pass http://127.0.0.1:8084/api/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 300s;
    proxy_buffering off;
}
```

## 音频、图片和视频的外部访问

后端通过 `MINIO_ENDPOINT=http://minio:9000` 上传并签名，使用 `MINIO_PUBLIC_URL` 生成完整的对外链接。前端 Nginx 的 `/media/` 代理会去掉前缀，并将 Host 还原为 `minio:9000`，保留签名校验。代理仅开放 GET/HEAD，支持播放器 Range 请求，无需将桶设为公开。

在根目录 `.env` 中设置：

```dotenv
# 本地 Docker（默认）
MINIO_PUBLIC_URL=http://localhost:5173/media
# 服务器：用实际可从外部访问的网站地址替换上面的值
# MINIO_PUBLIC_URL=https://app.example.com/media
```

公网入口需将 `/media/` 原样转发给前端容器（上面的 `location /` 已覆盖）。如另有登录网关，也需允许持签名链接的外部客户端访问此路径。不要直接把内部签名链接的域名替换成 MinIO 公网地址，绕过还原 Host 的代理会导致签名失效。

```bash
sudo docker compose up -d --build backend frontend
```

音频、图片和视频节点共用此文件上传服务，成功转存到 MinIO 后均返回完整公网链接。重新生成媒体后，将完整链接交给外部服务即可。可在另一台机器用 `curl -f --range 0-1023 '完整媒体链接' -o /tmp/audio-part` 验证，URL 需加引号以保留查询参数。签名默认有效期为 7 天（`MINIO_PRESIGNED_URL_EXPIRY_SECONDS=604800`），过期需重新生成签名；历史运行结果中的旧链接不会自动更新。

本地独立启动前后端时，默认 `MINIO_ENDPOINT` 和 `MINIO_PUBLIC_URL` 均为 `http://localhost:9000`，维持原有直连方式。也可将后端 `MINIO_PUBLIC_URL` 改为 `http://localhost:5173/media`，使用 Vite 媒体代理；`VITE_MINIO_PROXY_TARGET` 必须与后端 `MINIO_ENDPOINT` 一致。`localhost` 链接仅供本机访问，交给外部服务时必须配置可达的公网入口。

## 常用命令

```bash
sudo docker compose logs -f backend
sudo docker compose restart backend
sudo docker compose down
sudo docker compose up -d --build
```
