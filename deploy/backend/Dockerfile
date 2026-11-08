# =============================================================================
# 后端运行时镜像
# -----------------------------------------------------------------------------
# 刻意**不做多阶段构建**：这台服务器只有 2G 内存，在它上面跑 mvn package
# 会因为 Maven + javac 的内存峰值被 OOM killer 干掉（表现是编译到一半进程消失）。
#
# 所以 jar 在本地构建好，跟 Dockerfile 一起上传，镜像只负责"装进去跑起来"。
# 这样 docker build 只拉一个 JRE 基础镜像（~180MB），几分钟完成。
# =============================================================================

FROM eclipse-temurin:21-jre-alpine

# 时区数据：日志时间、@Scheduled 的 cron 都依赖它
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 上传前由 scripts/build-deploy.ps1 把 target 里的 jar 拷到这里
COPY app.jar app.jar

EXPOSE 8080

# JVM 参数由 compose 的 JAVA_TOOL_OPTIONS 注入，不写死在这里——
# 换机器调内存时只改 compose，不用重新构建镜像
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
