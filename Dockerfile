# ============================================================
# FlashLink 短链接服务 - Docker 镜像
# 采用多阶段构建：编译阶段用 JDK 21，运行阶段用 JRE 21
# 构建命令：docker build -t flashlink:latest .
# ============================================================

# -------------------- 阶段一：Maven 编译 --------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 配置阿里云 Maven 镜像加速依赖下载
RUN mkdir -p /root/.m2 && \
    echo '<settings>'                           > /root/.m2/settings.xml && \
    echo '  <mirrors>'                          >> /root/.m2/settings.xml && \
    echo '    <mirror>'                         >> /root/.m2/settings.xml && \
    echo '      <id>aliyunmaven</id>'           >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>*</mirrorOf>'         >> /root/.m2/settings.xml && \
    echo '      <name>阿里云公共仓库</name>'       >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '    </mirror>'                        >> /root/.m2/settings.xml && \
    echo '  </mirrors>'                         >> /root/.m2/settings.xml && \
    echo '</settings>'                          >> /root/.m2/settings.xml

# 先复制 pom.xml 和 wrapper，利用 Docker 层缓存加速依赖下载
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN mvn dependency:go-offline -B -q

# 复制源码并编译（跳过测试，生产环境测试应在 CI 中完成）
COPY src src
RUN mvn package -DskipTests -B -q

# -------------------- 阶段二：运行时镜像 --------------------
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户，遵循最小权限原则
RUN addgroup -S flashlink && adduser -S flashlink -G flashlink

# 安装 curl（健康检查用）并设置时区
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

WORKDIR /app

# ============================================================
# JVM 参数说明（针对 2c2g 服务器优化，容器内存限制 512MB）
# ============================================================
# -Xms256m -Xmx256m      固定堆大小 256MB，避免运行时扩容开销
# -XX:+UseSerialGC       单线程 GC，适合小堆（<1GB）且 CPU 核数少的环境
# -XX:MaxMetaspaceSize=128m  限制元空间，防止无限增长
# -XX:ReservedCodeCacheSize=64m  限制 JIT 编译缓存
# -XX:+UseContainerSupport    启用容器感知，尊重 cgroup 内存限制
# -XX:+ExitOnOutOfMemoryError  OOM 立即退出，让 Docker 重启
# ============================================================
ENV JAVA_OPTS="-Xms256m \
    -Xmx256m \
    -XX:+UseSerialGC \
    -XX:MaxMetaspaceSize=128m \
    -XX:ReservedCodeCacheSize=64m \
    -XX:+UseContainerSupport \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/ \
    -Duser.timezone=Asia/Shanghai"

# 从编译阶段复制 jar 包
COPY --from=builder /build/target/*.jar app.jar

# 创建日志目录并设置权限
RUN mkdir -p /app/logs && chown -R flashlink:flashlink /app

# 切换到非 root 用户
USER flashlink

# 应用端口（短链跳转主端口）
EXPOSE 8080
# 管理端口（Actuator 健康检查、Prometheus 指标）
EXPOSE 8081

# Docker 健康检查：通过 Actuator 就绪探针判断服务是否可用
HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]