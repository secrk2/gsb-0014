# 矫务通后端：多阶段构建，宿主机无需 JDK/Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 预热依赖层
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/jiaowutong.jar /app/jiaowutong.jar
EXPOSE 7104
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/jiaowutong.jar"]
