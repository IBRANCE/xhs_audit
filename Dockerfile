# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制pom.xml并下载依赖（利用Docker层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装Playwright依赖
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    freetype-dev \
    harfbuzz \
    ca-certificates \
    ttf-freefont \
    && rm -rf /var/cache/apk/*

# 设置Playwright使用系统Chrome
ENV PLAYWRIGHT_BROWSERS_PATH=/usr/bin/chromium
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 创建非root用户
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown appuser:appuser app.jar

USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/audit/health || exit 1

# 启动应用
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
