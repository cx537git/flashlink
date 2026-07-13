# ⚡ FlashLink

**极速短链接服务** — 基于 Spring Boot + Redis + MySQL 的高性能短链接生成与跳转平台。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📖 项目简介

FlashLink 是一个高性能短链接服务，将任意长链接转换为短链码，通过短链码实现 302 临时重定向，同时完成点击量统计。项目针对 2c2g 云服务器做了深度优化，支持 Docker Compose 一键部署。

**核心亮点：**

- 🚀 **三级查找策略**：布隆过滤器 → Redis 缓存 → 数据库，逐级兜底，毫秒级响应
- 🛡️ **缓存穿透防护**：布隆过滤器 + NOT_FOUND / EXPIRED 哨兵值双重防护
- 🔒 **细粒度并发控制**：Guava Striped 按短链码分片加锁，高并发下互不阻塞
- 📊 **异步点击统计**：Redis Stream 消费者组批量聚合，解耦主链路
- ⚙️ **双缓冲布隆过滤器**：定时重建 + 无锁切换，解决布隆过滤器不支持删除问题
- 🐳 **Docker 一键部署**：针对 2c2g 服务器内存极致优化（总计 880MB），含健康检查与资源限制

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        用户请求                                   │
│              POST /api/short-link  (生成短链)                     │
│              GET  /s/{shortCode}  (短链跳转)                      │
└──────────────┬──────────────────────────┬────────────────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────────┐  ┌────────────────────────────────────┐
│     RateLimitInterceptor │  │       RateLimitInterceptor          │
│     (生成: 10 QPS)        │  │       (跳转: 100 QPS)               │
└──────────────┬───────────┘  └──────────────┬─────────────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────┐  ┌────────────────────────────────────┐
│  Snowflake + Base62 编码  │  │  三级查找策略                        │
│  写入 DB + 布隆过滤器     │  │  ① 布隆过滤器预判                     │
│  返回短链码              │  │  ② Redis 缓存查询                    │
└──────────────────────────┘  │  ③ 数据库回源 (Guava Striped 锁)     │
                              │  302 重定向 + 发布点击事件到 Stream    │
                              └──────────────┬─────────────────────┘
                                             │
                                             ▼
                              ┌────────────────────────────────────┐
                              │  Redis Stream (ClickEventConsumer)  │
                              │  批量聚合 + PEL 认领 + 原子写入 DB    │
                              └────────────────────────────────────┘
```

---

## 📦 技术栈

| 组件   | 技术选型                                         | 用途                     |
| ------ | ------------------------------------------------ | ------------------------ |
| 框架   | Spring Boot 4.1.0                                | 应用框架                 |
| 语言   | Java 21                                          | 开发语言                 |
| ORM    | MyBatis-Plus 3.5.16                              | 数据库访问               |
| 数据库 | MySQL 8.4                                        | 持久化存储               |
| 缓存   | Redis 7.4 + Lettuce                              | 短链缓存 / Stream 消息队列 |
| 工具库 | Guava 33.6 (BloomFilter / RateLimiter / Striped) | 高并发基础设施            |
| 工具库 | Hutool 5.8.46                                    | Snowflake ID 生成        |
| 容器化 | Docker + Docker Compose                          | 部署运维                 |

---

## 🚀 快速开始

### 环境要求

- **JDK 21**+
- **Maven 3.9**+
- **MySQL 8.4**+
- **Redis 7.4**+
- **Docker & Docker Compose**（可选，用于容器化部署）

### 本地开发

1. **克隆项目**

   ```bash
   git clone <your-repo-url>
   cd flashlink
   ```

2. **创建数据库**

   执行 SQL 初始化脚本：

   ```bash
   mysql -u root -p < src/main/resources/flash_link_v1.sql
   ```

3. **修改配置**

   编辑 `src/main/resources/application-dev.yml`，修改数据库和 Redis 连接信息：

   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/flash_link?...
       username: root
       password: your_password
     data:
       redis:
         host: localhost
         port: 6379
   ```

4. **启动应用**

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   应用启动后，布隆过滤器会自动从数据库加载所有有效短链码。

5. **访问服务**

   - 短链生成页面：[http://localhost:8080](http://localhost:8080)
   - API 接口：`POST /api/short-link`、`GET /s/{shortCode}`

### Docker Compose 部署（生产环境推荐）

1. **配置环境变量**

   复制并编辑 `.env` 文件：

   ```bash
   cp .env.example .env
   # 修改数据库密码等敏感信息
   ```

2. **一键启动**

   ```bash
   docker compose up -d
   ```

3. **查看日志**

   ```bash
   docker compose logs -f flashlink
   ```

4. **停止服务**

   ```bash
   docker compose down
   ```

---

## 📡 API 接口

### 1. 生成短链接

```http
POST /api/short-link
Content-Type: application/json

{
  "originalUrl": "https://www.example.com/very/long/url",
  "expireTime": "2026-12-31T23:59:59"
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": "2TD6pyi6BbE"
}
```

| 参数           | 类型     | 必填 | 说明                        |
| -------------- | -------- | ---- | --------------------------- |
| `originalUrl`  | String   | 是   | 原始长链接，须以 http(s) 开头 |
| `expireTime`   | DateTime | 否   | 过期时间，null 表示永不过期   |

### 2. 短链接跳转

```http
GET /s/{shortCode}
```

浏览器访问 `http://localhost:8080/s/2TD6pyi6BbE` 即可 302 跳转到原始链接。

### 错误码

| 状态码 | 含义                 |
| ------ | -------------------- |
| 200    | 成功                 |
| 302    | 重定向成功           |
| 400    | 参数校验失败         |
| 404    | 短链不存在           |
| 410    | 短链已过期           |
| 429    | 触发限流，请稍后重试 |
| 503    | 系统繁忙，请稍后重试 |

---

## 🔬 核心设计

### 1. 短码生成：Snowflake + Base62

使用 Hutool 的 Snowflake 算法生成分布式唯一 ID，再通过**除基取余法**转换为 Base62 编码（字符集 `[0-9A-Za-z]`），生成约 11 位的 URL 安全短码。

```
Snowflake ID (64-bit) → Base62 编码 → 短链码 (如 "2TD6pyi6BbE")
```

### 2. 三级查找策略

```
请求 → 布隆过滤器(mightContain?) → 否 → 404
                    ↓ 是
           Redis 缓存(get?) → 命中 → 302 重定向
                    ↓ 未命中
         Guava Striped 细粒度锁
                    ↓
           MySQL 数据库回源 → 写入 Redis → 302 重定向
```

### 3. 布隆过滤器双缓冲

布隆过滤器不支持删除操作，过期短码会持续增加误判率。采用**双缓冲机制**：

- 每日凌晨 3:00 定时重建，从数据库全量加载有效短码
- 重建期间新生成的短码**同时写入 current 和 next 两个过滤器**
- 重建完成后**原子切换**，整个过程无锁、无停顿

### 4. 异步点击统计

跳转时通过 **Redis Stream** 异步发布点击事件，消费者批量聚合后原子写入数据库：

```
302 重定向 → publishClickEvent(Stream) → Consumer 批量聚合 → SQL 原子递增
```

- 消费者组保证 At-Least-Once 语义
- PEL 认领机制处理超时未 ACK 的消息
- 批量处理（每批 500 条），减少数据库写入压力

### 5. 缓存穿透防护

- **布隆过滤器**：拦截不存在的短码，直接返回 404
- **NOT_FOUND 哨兵值**：数据库不存在的短码缓存 3-6 分钟
- **EXPIRED 哨兵值**：已过期短码缓存 3-6 分钟
- **随机 TTL**：防止缓存雪崩

### 6. 限流策略

| 接口                  | 限流  | 说明             |
| --------------------- | ----- | ---------------- |
| POST /api/short-link  | 10/s  | 写操作，成本较高 |
| GET /s/{shortCode}    | 100/s | 读操作，核心链路 |

---

## 📁 项目结构

```
flashlink
├── src/main/java/personal/cx537/flashlink
│   ├── FlashlinkApplication.java            # 启动类
│   ├── config/
│   │   ├── WebConfig.java                   # Web MVC 配置（限流拦截器）
│   │   └── SchedulingConfig.java            # 定时任务线程池配置
│   ├── controller/
│   │   └── ShortLinkController.java         # 短链接 REST 控制器
│   ├── service/
│   │   ├── ShortLinkService.java            # 服务接口
│   │   └── impl/
│   │       └── ShortLinkServiceImpl.java    # 核心业务实现
│   ├── generator/
│   │   ├── ShortCodeGenerator.java          # 短码生成器接口
│   │   └── impl/
│   │       └── SnowflakeShortCodeGenerator.java  # Base62 编码实现
│   ├── entity/
│   │   └── ShortLink.java                   # 短链接实体
│   ├── mapper/
│   │   └── ShortLinkMapper.java             # MyBatis-Plus Mapper
│   ├── dto/
│   │   ├── Result.java                      # 统一响应封装
│   │   └── GenerateConditionDTO.java        # 生成请求参数
│   └── common/
│       ├── bloomfilter/
│       │   ├── BloomFilterHolder.java       # 双缓冲持有者
│       │   ├── BloomFilterInitializer.java  # 启动初始化
│       │   └── BloomFilterRebuilder.java    # 定时重建
│       ├── consumer/
│       │   └── ClickEventConsumer.java      # Stream 点击事件消费者
│       ├── interceptor/
│       │   └── RateLimitInterceptor.java    # Guava 限流拦截器
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java
│       │   ├── ShortLinkNotFoundException.java
│       │   ├── ShortLinkExpiredException.java
│       │   └── SystemBusyException.java
│       └── constant/
│           ├── CacheConstants.java          # 缓存键常量
│           └── RedisStreamConstants.java    # Stream 常量
├── src/main/resources/
│   ├── application.yml                      # 主配置
│   ├── application-dev.yml                  # 开发环境配置
│   ├── application-prod.yml                 # 生产环境配置（Docker）
│   ├── flash_link_v1.sql                    # 建表脚本
│   └── static/                              # 前端页面
│       ├── index.html
│       ├── css/style.css
│       └── js/app.js
├── Dockerfile                               # 多阶段构建镜像
├── docker-compose.yml                       # 容器编排（MySQL + Redis + App）
├── .env                                     # 环境变量
└── pom.xml                                  # Maven 依赖
```

---

## 📊 性能优化

针对 2c2g 云服务器的优化措施：

| 优化项         | 具体措施                                       |
| -------------- | ---------------------------------------------- |
| JVM 调优       | SerialGC + 固定 256MB 堆 + 128MB 元空间        |
| MySQL 内存优化 | InnoDB 缓冲池 96MB + 关闭 Performance Schema   |
| Redis 内存限制 | maxmemory 48MB + volatile-lru 逐出策略          |
| 连接池         | HikariCP 10 连接 + MySQL max_connections=80    |
| Tomcat 线程池  | 最大 100 线程 + 5000 最大连接数                 |
| Gzip 压缩      | 减少 JSON 响应体积                              |
| 优雅停机       | 等待当前请求处理完成，最长 30 秒                 |

---

## 👤 作者

**Ethan Wu** (cx537)

---

## 📄 许可证

MIT License